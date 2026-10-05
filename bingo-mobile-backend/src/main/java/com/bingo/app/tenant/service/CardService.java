package com.bingo.app.tenant.service;

import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.CardResponse;
import com.bingo.app.tenant.dto.response.GameCardResponse;
import com.bingo.app.tenant.dto.response.PreviewCardResponse;
import com.bingo.app.tenant.entity.*;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.exception.PlayerActionException;
import com.bingo.app.tenant.exception.WalletException;
import com.bingo.app.tenant.repository.CardRepository;
import com.bingo.app.tenant.repository.GameCardRepository;
import com.bingo.app.tenant.repository.GameRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardService {

    private final CardRepository cardRepository;
    private final GameCardRepository gameCardRepository;
    private final GameRepository gameRepository;
    private final com.bingo.app.tenant.repository.CardPreviewRepository cardPreviewRepository;
    private final PlayerService playerService;
    private final WalletService walletService;
    private final ObjectMapper objectMapper;
    private final TenantMapper tenantMapper;

    private static final int[][] COLUMN_RANGES = {
            {1, 15},   // B
            {16, 30},  // I
            {31, 45},  // N
            {46, 60},  // G
            {61, 75}   // O
    };
    private static final int CARD_SIZE = 5;
    private static final int FREE_SPACE_ROW = 2;
    private static final int FREE_SPACE_COL = 2;

    /**
     * Names of the game statuses that count as "live" for card availability.
     * Native queries need the raw varchar values, not the enum.
     */
    private static final List<String> LIVE_STATUS_NAMES =
            GameStatus.ACTIVE.stream().map(Enum::name).toList();

    /** Every admin tenant is guaranteed at least this many cards in its pool. */
    @Value("${bingo.initial-card-pool:100}")
    private int initialCardPoolSize;

    /**
     * Pages of free cards (by card number) within this tenant, e.g. page 0 = #1..N,
     * page 1 = next slice. Available = not dealt to any live game.
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<CardResponse> getAvailableCards(int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(0, page);
        return cardRepository.findAvailable(GameStatus.ACTIVE, PageRequest.of(safePage, safeSize)).stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public long countAvailableCards() {
        return cardRepository.countAvailable(GameStatus.ACTIVE);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public CardResponse getCard(Long cardId) {
        return tenantMapper.toDto(cardRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Card not found")));
    }

    /**
     * Guarantee the tenant holds at least {@code initialCardPoolSize} cards.
     * Fires for brand-new tenants (empty pool) and tops up pre-existing tenants
     * that have fewer cards than the default pool size.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void ensureInitialCardPool() {
        long existing = cardRepository.count();
        if (existing < initialCardPoolSize) {
            int missing = initialCardPoolSize - (int) existing;
            log.info("Tenant has {} cards — generating {} to reach the initial pool of {}",
                    existing, missing, initialCardPoolSize);
            generateCardPool(missing);
        }
    }

    /**
     * Generate a unique Bingo card
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public CardResponse generateUniqueCard() {
        int maxAttempts = 100;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int[][] numbers = generateCardNumbers();
            String numbersJson = toJson(numbers);
            String numbersHash = hashNumbers(numbersJson);

            // Check if card already exists
            if (cardRepository.findByNumbersHash(numbersHash).isEmpty()) {
                Card card = Card.builder()
                        .numbers(numbersJson)
                        .numbersHash(numbersHash)
                        .used(false)
                        .usageCount(0)
                        .gamesWon(0)
                        .winRate(0.0)
                        .build();
                return tenantMapper.toDto(cardRepository.save(card));
            }
        }

        throw new RuntimeException("Failed to generate unique card after " + maxAttempts + " attempts");
    }

    /**
     * Generate a pool of unique cards
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void generateCardPool(int count) {
        log.info("Generating {} unique cards", count);
        int generated = 0;

        for (int i = 0; i < count && generated < count; i++) {
            try {
                generateUniqueCard();
                generated++;
                if (generated % 100 == 0) {
                    log.info("Generated {} unique cards so far", generated);
                }
            } catch (Exception e) {
                log.warn("Failed to generate card on attempt {}: {}", i, e.getMessage());
            }
        }

        log.info("Successfully generated {} unique cards", generated);
    }

    /**
     * Generate random Bingo card numbers
     */
    private int[][] generateCardNumbers() {
        int[][] card = new int[CARD_SIZE][CARD_SIZE];
        SecureRandom random = new SecureRandom();

        for (int col = 0; col < CARD_SIZE; col++) {
            List<Integer> columnNumbers = new ArrayList<>();
            int min = COLUMN_RANGES[col][0];
            int max = COLUMN_RANGES[col][1];

            while (columnNumbers.size() < CARD_SIZE) {
                int number = min + random.nextInt(max - min + 1);
                if (!columnNumbers.contains(number)) {
                    columnNumbers.add(number);
                }
            }

            Collections.sort(columnNumbers);

            for (int row = 0; row < CARD_SIZE; row++) {
                card[row][col] = columnNumbers.get(row);
            }
        }

        // Set free space
        card[FREE_SPACE_ROW][FREE_SPACE_COL] = 0;

        return card;
    }

    /**
     * Register a player for a game using a player-chosen (or auto-picked) card.
     *
     * The chosen card is locked for the duration of the transaction (PESSIMISTIC_WRITE),
     * then re-verified as free so two players can never grab the same card concurrently.
     * The (game_id, card_id) unique index is the DB-hard backstop.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public GameCardResponse assignCard(Long gameId, Long playerId, Long cardId) {
        // Validate game exists and is in registration phase
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        if (game.getStatus() != GameStatus.REGISTRATION_OPEN) {
            throw new PlayerActionException("Game is not accepting registrations",
                    "This game is no longer accepting registrations.");
        }

        // Players may hold multiple cards in the same game (each an entry).

        // Enforce: player can only be in ONE active game at a time (holding multiple
        // cards in the same game is allowed — the current game is excluded here).
        var activeGameCards = gameCardRepository.findByPlayerIdAndActiveGamesExcluding(playerId, gameId);
        if (!activeGameCards.isEmpty()) {
            throw new PlayerActionException(
                    "Player already has a card for an active game",
                    "You are already registered for an active game. Finish it before joining another.");
        }

        Card card = resolveCard(cardId);

        // Create game card entry for the chosen card
        GameCard gameCard = GameCard.builder()
                .gameId(gameId)
                .playerId(playerId)
                .card(card)
                .winner(false)
                .build();

        // Deduct entry fee from player balance (records BET transaction)
        if (playerService.getBalance(playerId).compareTo(game.getEntryFee()) < 0) {
            throw new WalletException("Insufficient balance",
                    "Your balance is " + playerService.getBalance(playerId).stripTrailingZeros().toPlainString()
                            + " coins, but this card costs " + game.getEntryFee().stripTrailingZeros().toPlainString()
                            + " coins. Please request more coins from your agent.");
        }

        walletService.deductBet(playerId, game.getEntryFee(), gameId);

        // Update prize pool
        game.setPrizePool(game.getPrizePool().add(game.getEntryFee()));
        gameRepository.save(game);

        // Track card usage
        card.setUsageCount(card.getUsageCount() + 1);
        cardRepository.save(card);

        return tenantMapper.toDto(gameCardRepository.save(gameCard));
    }

    /**
     * Register a player for a game with {@code count} auto-picked free cards in
     * a single atomic transaction. Every card is an entry: it is locked,
     * verified free, and charged the entry fee (one BET per card). If the pool
     * has fewer free cards than {@code count}, the available ones are assigned.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public List<GameCardResponse> assignCardsAuto(Long gameId, Long playerId, int count) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        List<Card> free = cardRepository.findRandomAvailable(LIVE_STATUS_NAMES, Math.max(1, count));
        if (free.isEmpty()) {
            throw new PlayerActionException("No free cards",
                    "No free cards available. Please tell your agent to request more cards from the platform.");
        }
        BigDecimal needed = game.getEntryFee().multiply(BigDecimal.valueOf(free.size()));
        if (playerService.getBalance(playerId).compareTo(needed) < 0) {
            throw new WalletException("Insufficient balance",
                    "Your balance is " + playerService.getBalance(playerId).stripTrailingZeros().toPlainString()
                            + " coins, but " + free.size() + (free.size() == 1 ? " card costs " : " cards cost ")
                            + needed.stripTrailingZeros().toPlainString() + " coins. Please request more coins from your agent.");
        }
        List<GameCardResponse> result = new ArrayList<>(free.size());
        for (Card card : free) {
            result.add(assignCard(gameId, playerId, card.getId()));
        }
        return result;
    }

    /**
     * Hold {@code count} free cards for the player to look at. Nothing is charged
     * and the cards are not registered -- the player registers the ones they
     * actually want with {@link #registerPreviewedCard}.
     *
     * <p>Previewed cards are held rather than merely displayed, so a card the
     * player is looking at cannot be dealt to somebody else in the meantime.
     * Requesting a preview again tops the holding up to {@code count} rather
     * than dealing a second batch, which keeps a player from filling the pool
     * with previews they never intend to buy.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public List<PreviewCardResponse> previewCards(Long gameId, Long playerId, int count) {
        Game game = requireRegistrationOpen(gameId);
        requireNoOtherActiveGame(gameId, playerId);

        int wanted = Math.max(1, Math.min(count, 50));
        long held = cardPreviewRepository.countByGameIdAndPlayerId(gameId, playerId);
        long registered = gameCardRepository.countByGameIdAndPlayerId(gameId, playerId);
        long totalOwnedInGame = held + registered;
        if (totalOwnedInGame >= wanted) {
            // Already holding/registered at least this many: just show what is on hold.
            return heldPreviews(gameId, playerId);
        }
        int missing = (int) (wanted - totalOwnedInGame);
        if (missing > 50) missing = 50;
        if (missing <= 0) {
            return heldPreviews(gameId, playerId);
        }

        List<Card> free = cardRepository.findRandomAvailable(LIVE_STATUS_NAMES, missing);
        if (free.size() < missing) {
            // The player asked to see this many cards, so they are told how many
            // there really are instead of being shown a smaller set.
            throw new PlayerActionException("Not enough free cards",
                    "Only " + free.size() + (free.size() == 1 ? " card is" : " cards are")
                            + " still available. Please ask for a smaller number of cards,"
                            + " or tell your agent to request more cards from the platform.");
        }
        for (Card card : free) {
            cardPreviewRepository.save(CardPreview.builder()
                    .gameId(gameId)
                    .playerId(playerId)
                    .card(card)
                    .build());
        }
        log.info("Game {}: player {} previewing {} card(s)", gameId, playerId, free.size());
        return heldPreviews(gameId, playerId);
    }

    private List<PreviewCardResponse> heldPreviews(Long gameId, Long playerId) {
        return cardPreviewRepository.findByGameIdAndPlayerIdOrderByCreatedAtAsc(gameId, playerId).stream()
                .map(cp -> PreviewCardResponse.builder()
                        .cardId(cp.getCard().getId())
                        .numbers(parseNumbers(cp.getCard().getNumbers()))
                        .build())
                .toList();
    }

    /**
     * Turn one previewed card into a real registration: take the entry fee, add
     * it to the pot and deal the card to the player.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public GameCardResponse registerPreviewedCard(Long gameId, Long playerId, Long cardId) {
        Game game = requireRegistrationOpen(gameId);
        // Holding a preview is not a registration, so the one-active-game guard is
        // not applied when the card is previewed. It has to be applied here
        // instead, or a player could preview their way past it.
        requireNoOtherActiveGame(gameId, playerId);

        // Enforce max cards per player in game (50)
        long held = cardPreviewRepository.countByGameIdAndPlayerId(gameId, playerId);
        long registered = gameCardRepository.countByGameIdAndPlayerId(gameId, playerId);
        if (registered >= 50) {
            throw new PlayerActionException("Too many cards",
                    "You can hold at most 50 cards in a game.");
        }

        CardPreview preview = cardPreviewRepository.findByGameIdAndPlayerIdAndCardId(gameId, playerId, cardId)
                .orElseThrow(() -> new PlayerActionException("Card not held",
                        "This card is not one of the cards you are holding. Request a new set of cards."));
        Card card = preview.getCard();

        // The hold keeps other players out, but the row is re-checked so a card
        // that was registered by somebody else meanwhile is not dealt twice. This
        // player's own preview is excluded: holding it is the whole point here.
        if (cardRepository.isCardOccupiedByOthers(cardId, gameId, playerId, GameStatus.ACTIVE)) {
            throw new PlayerActionException("Card already taken",
                    "This card was just taken by another player. Please pick another.");
        }

        BigDecimal fee = game.getEntryFee();
        BigDecimal balance = playerService.getBalance(playerId);
        if (balance.compareTo(fee) < 0) {
            throw new WalletException("Insufficient balance",
                    "Your balance is " + balance.stripTrailingZeros().toPlainString()
                            + " coins, but this card costs " + fee.stripTrailingZeros().toPlainString()
                            + " coins. You need " + fee.subtract(balance).stripTrailingZeros().toPlainString()
                            + " more coins. Please request more coins from your agent.");
        }

        cardPreviewRepository.delete(preview);
        walletService.deductBet(playerId, fee, gameId);

        game.setPrizePool(game.getPrizePool().add(fee));
        gameRepository.save(game);

        card.setUsageCount(card.getUsageCount() + 1);
        cardRepository.save(card);

        GameCard gameCard = gameCardRepository.save(GameCard.builder()
                .gameId(gameId)
                .playerId(playerId)
                .card(card)
                .winner(false)
                .build());
        log.info("Game {}: player {} registered previewed card {}", gameId, playerId, cardId);
        return tenantMapper.toDto(gameCard);
    }

    /**
     * Drop a card from the player's view.
     *
     * <p>A preview that was never registered is simply released back to the
     * pool. A registered card is unregistered: the entry fee goes back to the
     * player's balance and comes back out of the pot, because the pot may only
     * hold money from cards that are actually in play.
     *
     * @return what happened, so the client can word it correctly
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public UnregisterResult unregisterCard(Long gameId, Long playerId, Long cardId) {
        requireRegistrationOpen(gameId);

        var preview = cardPreviewRepository.findByGameIdAndPlayerIdAndCardId(gameId, playerId, cardId);
        if (preview.isPresent()) {
            cardPreviewRepository.delete(preview.get());
            log.info("Game {}: player {} released previewed card {}", gameId, playerId, cardId);
            return new UnregisterResult(false, BigDecimal.ZERO);
        }

        GameCard gameCard = gameCardRepository.findAllByGameIdAndPlayerId(gameId, playerId).stream()
                .filter(gc -> gc.getCard().getId().equals(cardId))
                .findFirst()
                .orElseThrow(() -> new PlayerActionException("Card not held",
                        "You do not hold this card in this game."));

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        BigDecimal refund = game.getEntryFee();

        walletService.refundPlayer(playerId, refund, gameId);

        // The pot must never go negative: a game whose pot was already paid out
        // (or a zero-fee game) is floored at zero rather than credited a negative.
        BigDecimal remaining = game.getPrizePool().subtract(refund);
        game.setPrizePool(remaining.signum() < 0 ? BigDecimal.ZERO : remaining);
        gameRepository.save(game);

        gameCardRepository.delete(gameCard);
        log.info("Game {}: player {} unregistered card {} and was refunded {}",
                gameId, playerId, cardId, refund);
        return new UnregisterResult(true, refund);
    }

    /** True when the card went back to the pool as a preview; false when it was a paid registration. */
    public record UnregisterResult(boolean wasRegistered, BigDecimal refund) {}

    private Game requireRegistrationOpen(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        if (game.getStatus() != GameStatus.REGISTRATION_OPEN) {
            throw new PlayerActionException("Game is not accepting registrations",
                    "This game is no longer accepting registrations.");
        }
        return game;
    }

    /** One active game at a time. Previews are not registrations, so they do not count. */
    private void requireNoOtherActiveGame(Long gameId, Long playerId) {
        var activeGameCards = gameCardRepository.findByPlayerIdAndActiveGamesExcluding(playerId, gameId);
        if (!activeGameCards.isEmpty()) {
            throw new PlayerActionException(
                    "Player already has a card for an active game",
                    "You are already registered for an active game. Finish it before joining another.");
        }
    }

    private Card resolveCard(Long cardId) {
        if (cardId == null) {
            // Auto-pick a random free card
            List<Card> free = cardRepository.findRandomAvailable(LIVE_STATUS_NAMES, 1);
            if (free.isEmpty()) {
                throw new PlayerActionException("No free cards",
                        "No free cards available. Please tell your agent to request more cards from the platform.");
            }
            return free.get(0);
        }

        // Lock the row so concurrent selections of the same card serialize, then re-check.
        Card card = cardRepository.findByIdForUpdate(cardId)
                .orElseThrow(() -> new PlayerActionException("Card not found",
                        "This card no longer exists."));

        if (cardRepository.isCardOccupied(cardId, GameStatus.ACTIVE)) {
            throw new PlayerActionException("Card already taken",
                    "This card was just taken by another player. Please pick another.");
        }

        return card;
    }

    /**
     * Get cards for a player across their games
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<GameCardResponse> findCardsForPlayer(Long playerId) {
        return gameCardRepository.findByPlayerIdOrderByCreatedAtDesc(playerId).stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    /**
     * Check if player has a card for a game
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean hasCardForGame(Long gameId, Long playerId) {
        return gameCardRepository.existsByGameIdAndPlayerId(gameId, playerId);
    }

    /**
     * Count cards for a game
     */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public int countCardsForGame(Long gameId) {
        return gameCardRepository.countByGameId(gameId);
    }

    /**
     * Mark a specific dealt card as winner and update its pool stats.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void markCardAsWinner(Long gameId, Long cardId) {
        GameCard gameCard = gameCardRepository.findByGameIdAndCardId(gameId, cardId)
                .orElseThrow(() -> new RuntimeException("Game card not found"));

        gameCard.setWinner(true);
        gameCardRepository.save(gameCard);

        Card card = gameCard.getCard();
        card.setGamesWon(card.getGamesWon() + 1);
        card.setWinRate(card.getUsageCount() > 0
                ? (card.getGamesWon().doubleValue() / card.getUsageCount()) * 100
                : 0.0);
        cardRepository.save(card);
    }

    /** Card numbers for the client. A card the server cannot read is shown as an empty grid. */
    private int[][] parseNumbers(String numbersJson) {
        if (numbersJson == null || numbersJson.isBlank()) {
            return new int[CARD_SIZE][CARD_SIZE];
        }
        try {
            int[][] parsed = objectMapper.readValue(numbersJson, int[][].class);
            return parsed != null && parsed.length > 0 ? parsed : new int[CARD_SIZE][CARD_SIZE];
        } catch (Exception e) {
            log.error("Failed to parse card numbers: {}", e.getMessage());
            return new int[CARD_SIZE][CARD_SIZE];
        }
    }

    private String toJson(int[][] numbers) {
        try {
            return objectMapper.writeValueAsString(numbers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize card", e);
        }
    }

    private String hashNumbers(String numbers) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(numbers.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash card", e);
        }
    }

    /**
     * Take the most recent cards the player used in any previous game (ended,
     * completed, or even the last one they touched) and create fresh previews
     * for the target game in REGISTRATION_OPEN. The original card numbers are
     * reused, but they are treated as new previews in the new game (no marks,
     * not registered). If any of those cards are unavailable (held elsewhere),
     * we just skip them rather than failing the whole operation.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public List<PreviewCardResponse> previewPreviousCards(Long targetGameId, Long playerId) {
        Game target = requireRegistrationOpen(targetGameId);
        requireNoOtherActiveGame(targetGameId, playerId);

        List<GameCard> lastCards = gameCardRepository.findLastCardsByPlayerId(playerId);
        if (lastCards.isEmpty()) {
            throw new PlayerActionException("No previous cards",
                    "You haven't played any cards yet to reuse.");
        }

        List<PreviewCardResponse> created = new ArrayList<>();
        for (GameCard gc : lastCards) {
            Long cardId = gc.getCard().getId();
            // Already previewed for this game? skip
            if (cardPreviewRepository.findByGameIdAndPlayerIdAndCardId(targetGameId, playerId, cardId).isPresent()) {
                // already held as preview
                continue;
            }
            // Check if this card is occupied by others in active games (excluding this target)
            if (cardRepository.isCardOccupiedByOthers(cardId, targetGameId, playerId, GameStatus.ACTIVE)) {
                continue; // skip unavailable
            }
            cardPreviewRepository.save(CardPreview.builder()
                    .gameId(targetGameId)
                    .playerId(playerId)
                    .card(gc.getCard())
                    .build());
            created.add(PreviewCardResponse.builder()
                    .cardId(cardId)
                    .numbers(parseNumbers(gc.getCard().getNumbers()))
                    .build());
        }

        if (created.isEmpty()) {
            return heldPreviews(targetGameId, playerId);
        }
        log.info("Game {}: player {} previewing {} previous card(s)", targetGameId, playerId, created.size());
        return created;
    }
}