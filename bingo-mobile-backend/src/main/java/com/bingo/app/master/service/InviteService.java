package com.bingo.app.master.service;

import com.bingo.app.master.exception.InviteRegistrationException;
import com.bingo.app.common.exception.BadRequestException;
import com.bingo.app.common.exception.ForbiddenException;
import com.bingo.app.common.exception.NotFoundException;
import com.bingo.app.infrastructure.persistence.TenantManagementService;
import com.bingo.app.master.dto.mapper.MasterMapper;
import com.bingo.app.master.dto.request.CreateAdminRequest;
import com.bingo.app.master.dto.request.CreatePlayerRequest;
import com.bingo.app.master.dto.response.InviteCodeResponse;
import com.bingo.app.master.dto.response.InviteCodeStatsResponse;
import com.bingo.app.master.entity.InviteCode;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.repository.InviteCodeRepository;
import com.bingo.app.master.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InviteService {

    private final InviteCodeRepository inviteCodeRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final TenantManagementService tenantManagementService;
    private final MasterMapper masterMapper;
    private final NotificationService notificationService;

    /**
     * Registration-only bot username (from {@code REGISTRATION_BOT_USERNAME}).
     * Invite links point here when configured so players onboard (register, share
     * phone, set a password), then use the game bot/web app for the game itself.
     */
    @Value("${app.telegram.registration-bot.username:}")
    private String registrationBotUsername;

    /**
     * Generate an invite link for a user.
     * Links point at the registration-only bot when configured; otherwise they
     * fall back to the game bot username passed by the caller.
     */
    @Transactional
    public String generateInviteLinkForUser(Long creatorId, String botUsername) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (creator.getRole() == Role.ADMIN && !creator.isAdminApproved()) {
            throw new ForbiddenException(
                    "Your admin account is awaiting super admin approval. You cannot generate invite links until your account is approved.");
        }

        Role targetRole = creator.getRole() == Role.SUPER_ADMIN ? Role.ADMIN : Role.PLAYER;

        String targetBot = (registrationBotUsername != null && !registrationBotUsername.isBlank())
                ? registrationBotUsername
                : botUsername;

        // Admin player invites are permanent — reuse existing active code if any
        if (targetRole == Role.PLAYER) {
            List<InviteCode> existing = inviteCodeRepository.findByCreatorIdAndActiveTrue(creatorId);
            if (!existing.isEmpty()) {
                String code = existing.get(0).getCode();
                return "https://t.me/" + targetBot + "?start=" + code;
            }
        }

        String code = generateUniqueCode();

        InviteCode inviteCode = InviteCode.builder()
                .code(code)
                .creatorId(creatorId)
                .role(targetRole)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        inviteCodeRepository.save(inviteCode);

        return "https://t.me/" + targetBot + "?start=" + code;
    }

    /**
     * Register a user with an invite code
     *
     * NOT @Transactional — CREATE DATABASE inside the chain cannot run in a transaction.
     * Individual JpaRepository calls handle their own implicit transactions.
     */
    public User registerWithInvite(Long telegramId, String code) {
        log.info("Registering user telegramId={} with code={}", telegramId, code);

        // Validate invite code
        InviteCode inviteCode = inviteCodeRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> InviteRegistrationException.invalidCode());

        // Check if user already exists
        User existingUser = userRepository.findByTelegramId(telegramId).orElse(null);

        // Get creator info
        User creator = userRepository.findById(inviteCode.getCreatorId())
                .orElseThrow(() -> InviteRegistrationException.inviterNotFound());

        if (existingUser != null) {
            // Allow upgrading PLAYER to ADMIN if the invite code is for ADMIN
            if (existingUser.getRole() == Role.PLAYER && inviteCode.getRole() == Role.ADMIN) {
                existingUser.setRole(Role.ADMIN);
                existingUser.setParentId(creator.getId());
                existingUser.setAdminApproved(false);
                userRepository.save(existingUser);
                tenantManagementService.createTenant(existingUser.getId());
                if (inviteCode.getRole() == Role.ADMIN) {
                    inviteCode.setActive(false);
                    inviteCodeRepository.save(inviteCode);
                }
                log.info("PLAYER upgraded to ADMIN: id={}, telegramId={}", existingUser.getId(), telegramId);
                return existingUser;
            }
            throw InviteRegistrationException.alreadyRegistered();
        }

        // Get Telegram user info (will be updated from Telegram later)
        String username = "user_" + telegramId;
        String firstName = "User";
        String lastName = "";

        User newUser;

        if (inviteCode.getRole() == Role.ADMIN) {
            // Create new admin (also creates tenant database)
            newUser = userService.createAdmin(new CreateAdminRequest(
                    creator.getId(),
                    telegramId,
                    username,
                    firstName,
                    lastName
            ));

            log.info("New admin registered: id={}, telegramId={}", newUser.getId(), telegramId);

        } else {
            // Create new player
            Long adminUserId = creator.getRole() == Role.ADMIN ? creator.getId() : creator.getAdminUserId();

            if (adminUserId == null) {
                throw new BadRequestException("Cannot create player: no admin assigned");
            }

            tenantManagementService.createTenant(adminUserId);

            newUser = userService.createPlayer(new CreatePlayerRequest(
                    adminUserId,
                    telegramId,
                    username,
                    firstName,
                    lastName,
                    adminUserId
            ));

            log.info("New player registered: id={}, telegramId={}, adminUserId={}",
                    newUser.getId(), telegramId, adminUserId);

            try {
                notificationService.notify(adminUserId, "NEW_PLAYER",
                        "New player joined",
                        "A new player (@" + username + ") joined your room.",
                        "notify.invite.newPlayer", "{\"username\":\"" + username + "\"}",
                        "PLAYER", newUser.getId(), null);
            } catch (Exception e) {
                log.warn("Failed to notify admin about new player: {}", e.getMessage());
            }
        }

        // Deactivate ADMIN invite codes after use (single-use per admin).
        // PLAYER invite codes remain active forever so admins can share a permanent link.
        if (inviteCode.getRole() == Role.ADMIN) {
            inviteCode.setActive(false);
            inviteCodeRepository.save(inviteCode);
        }

        return newUser;
    }

    /**
     * Validate an invite code
     */
    public InviteCodeResponse validateInviteCode(String code) {
        return masterMapper.toDto(inviteCodeRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> InviteRegistrationException.invalidCode()));
    }

    /**
     * Generate a unique invite code
     */
    private String generateUniqueCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (inviteCodeRepository.existsByCode(code));
        return code;
    }

    /**
     * Deactivate an invite code
     */
    @Transactional
    public void deactivateInviteCode(String code) {
        inviteCodeRepository.deactivateInviteCode(code);
        log.info("Deactivated invite code: {}", code);
    }

    /**
     * Get all active invite codes for a creator
     */
    public List<InviteCodeResponse> getActiveInviteCodesForCreator(Long creatorId) {
        return inviteCodeRepository.findByCreatorIdAndActiveTrue(creatorId).stream()
                .map(masterMapper::toDto)
                .toList();
    }

    /**
     * Get invite code statistics
     */
    public InviteCodeStatsResponse getInviteCodeStats(Long creatorId) {
        List<InviteCode> codes = inviteCodeRepository.findByCreatorId(creatorId);
        long totalCodes = codes.size();
        long usedCodes = codes.stream().filter(c -> !c.isActive()).count();
        long activeCodes = totalCodes - usedCodes;

        // Count registrations from this creator's invites
        long registrations = 0;
        if (creatorId != null) {
            User creator = userRepository.findById(creatorId).orElse(null);
            if (creator != null && creator.getRole() == Role.ADMIN) {
                registrations = userRepository.countByParentId(creatorId);
            } else if (creator != null && creator.getRole() == Role.SUPER_ADMIN) {
                registrations = userRepository.countByRole(Role.ADMIN);
            }
        }

        return InviteCodeStatsResponse.builder()
                .totalCodes(totalCodes)
                .activeCodes(activeCodes)
                .usedCodes(usedCodes)
                .totalRegistrations(registrations)
                .build();
    }
}
