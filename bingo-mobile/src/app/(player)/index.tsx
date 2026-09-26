import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { FlatList, Pressable, RefreshControl, Text, View } from 'react-native';
import { gamesApi, walletApi } from '@/api';
import { CardPickerModal } from '@/components/games/CardPickerModal';
import { Button, Card, Screen, ScreenHeader, Subtitle, Title } from '@/components/ui';
import { ThemeToggleButton } from '@/components/ui/ThemeToggleButton';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale } from '@/lib/clientTranslations';
import { GameResponse, GameStatus } from '@/types';

export default function PlayerLobbyScreen() {
  const t = useTranslate();
  const router = useRouter();
  const qc = useQueryClient();
  const [filter, setFilter] = useState<'all' | 'mine'>('all');
  const [pickerGame, setPickerGame] = useState<GameResponse | null>(null);

  // Games open/close without the player doing anything, and this tab stays
  // mounted, so poll and re-check on focus. Without this the lobby keeps
  // offering a stale game and registering fails with "no longer accepting
  // registrations" even though the admin has a new game open.
  const gamesQuery = useQuery({
    queryKey: ['player/games'],
    queryFn: () => gamesApi.getActive(),
    refetchInterval: 10_000,
  });
  const walletQuery = useQuery({ queryKey: ['wallet'], queryFn: () => walletApi.get() });

  const refreshGames = useCallback(() => {
    void qc.invalidateQueries({ queryKey: ['player/games'] });
  }, [qc]);

  useFocusEffect(
    useCallback(() => {
      refreshGames();
    }, [refreshGames])
  );

  const games: GameResponse[] = gamesQuery.data?.data ?? [];

  const visible = filter === 'mine'
    ? games.filter((g) => g.registered || g.activeGameId === g.id)
    : games;

  const goGame = (g: GameResponse) => {
    router.push({ pathname: '/(player)/game/[id]', params: { id: String(g.id) } });
  };

  const register = async (g: GameResponse) => {
    setPickerGame(g);
  };

  const handleRegistered = async () => {
    await gamesQuery.refetch();
  };

  // A rejected registration usually means our copy of the lobby is out of date
  // (game ended / filled), so drop it and show the real list right away.
  const handleRegisterFailed = () => {
    refreshGames();
  };

  return (
    <Screen>
      <ScreenHeader title={t('common.appName') ?? 'BingoPlus'} right={<ThemeToggleButton />} />

      <View className="flex-row items-center justify-between mb-4 bg-bp-surface rounded-2xl border border-bp-borderInactive p-4">
        <View>
          <Subtitle>{t('player.yourBalance') ?? 'Your Balance'}</Subtitle>
          <Title className="text-2xl">
            {walletQuery.data?.data.balance?.toLocaleString() ?? '—'}
          </Title>
          <Subtitle className="text-xs">{t('player.coinsAvailable') ?? 'birr available'}</Subtitle>
        </View>
        <Button variant="outline" onPress={() => router.push('/(player)/wallet')}>
          {t('player.buyCoins') ?? 'Buy Birr'}
        </Button>
      </View>

      <View className="flex-row gap-2 mb-3">
        <FilterChip
          active={filter === 'all'}
          label={t('player.live', { count: games.length }) ?? `Live (${games.length})`}
          onPress={() => setFilter('all')}
        />
        <FilterChip
          active={filter === 'mine'}
          label={t('player.myGames') ?? 'My Games'}
          onPress={() => setFilter('mine')}
        />
        <Button variant="outline" onPress={() => router.push('/(player)/my-games')} style={{ paddingVertical: 8, paddingHorizontal: 12 }}>
          <Text className="text-bp-primary text-xs font-semibold">{t('player.myGamesAll') ?? 'All ›'}</Text>
        </Button>
      </View>

      <FlatList
        data={visible}
        extraData={getClientLocale()}
        keyExtractor={(g) => String(g.id)}
        refreshControl={
          <RefreshControl refreshing={gamesQuery.isFetching} onRefresh={() => gamesQuery.refetch()} tintColor="#6B5BFF" />
        }
        contentContainerClassName="gap-3 pb-8"
        ListEmptyComponent={
          <Card>
            <Text className="text-bp-textSecondary text-center">
              {t('mobile.noGames') ?? 'No games available right now'}
            </Text>
          </Card>
        }
        renderItem={({ item }) => (
          <GameCard
            game={item}
            onOpen={() => goGame(item)}
            onRegister={() => register(item)}
            t={t}
          />
        )}
      />

      {pickerGame && (
        <CardPickerModal
          gameId={pickerGame.id}
          entryFee={pickerGame.entryFee}
          onClose={() => setPickerGame(null)}
          onRegistered={() => void handleRegistered()}
          onFailed={handleRegisterFailed}
        />
      )}
    </Screen>
  );
}

function GameCard({
  game,
  onOpen,
  onRegister,
  t,
}: {
  game: GameResponse;
  onOpen: () => void;
  onRegister: () => void;
  t: ReturnType<typeof useTranslate>;
}) {
  const open = game.status === GameStatus.REGISTRATION_OPEN || game.status === GameStatus.STARTING;
  const registered = !!game.registered;

  return (
    <Pressable onPress={onOpen} className="active:opacity-80">
      <Card className="gap-2">
        <View className="flex-row justify-between items-center">
          <Text className="text-bp-textPrimary font-semibold text-lg">
            {t('game.gameNumber', { id: String(game.id) }) ?? `Game #${game.id}`}
          </Text>
          <Text className="text-bp-accentInk font-medium">
            {t(`status.${game.status}`) ?? game.status}
          </Text>
        </View>
        <View className="flex-row justify-between mt-1">
          <Text className="text-bp-textSecondary">
            {t('common.entryFee') ??
              'Entry'}: {game.entryFee}
          </Text>
          <Text className="text-bp-textSecondary">
            {t('player.jackpotPool') ?? 'Pool'}: {game.prizePool.toLocaleString()}
          </Text>
          <Text className="text-bp-textSecondary">
            {game.registeredPlayers ?? 0}/{game.maxPlayers}
          </Text>
        </View>
        {open && (
          <Button variant={registered ? 'primary' : 'secondary'} onPress={registered ? onOpen : onRegister}>
            {registered
              ? t('player.registered') ?? 'Registered! Tap to view your card'
              : t('player.open') ?? 'Open'}
          </Button>
        )}
      </Card>
    </Pressable>
  );
}

function FilterChip({ active, label, onPress }: { active: boolean; label: string; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      className={`px-4 py-2 rounded-full border ${active ? 'bg-bp-primary border-bp-primary' : 'bg-bp-surface border-bp-borderInactive'}`}
    >
      <Text className={`${active ? 'text-white' : 'text-bp-textSecondary'}`}>{label}</Text>
    </Pressable>
  );
}