package com.bingo.app.master.exception;

public class InviteRegistrationException extends RuntimeException {

    private final String userMessage;
    private final String code;

    private InviteRegistrationException(String message, String userMessage, String code) {
        super(message);
        this.userMessage = userMessage;
        this.code = code;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getCode() {
        return code;
    }

    public static InviteRegistrationException invalidCode() {
        return new InviteRegistrationException(
                "Invite code does not exist",
                "This invite link is invalid. Ask your admin for a new link.",
                "invite_invalid"
        );
    }

    public static InviteRegistrationException inactiveCode() {
        return new InviteRegistrationException(
                "Invite code is inactive",
                "This invite link has already been used or was deactivated.",
                "invite_inactive"
        );
    }

    public static InviteRegistrationException inviterNotFound() {
        return new InviteRegistrationException(
                "Parent admin not found",
                "This invite link is no longer valid. Ask your admin for a new link.",
                "invite_inviter_missing"
        );
    }

    public static InviteRegistrationException invalidInviterRole() {
        return new InviteRegistrationException(
                "Inviter role is not allowed to create invite links",
                "This invite link is not valid for registration.",
                "invite_inviter_role"
        );
    }

    public static InviteRegistrationException alreadyRegistered() {
        return new InviteRegistrationException(
                "User is already registered",
                "Your account is already registered. Use /start to open your menu.",
                "invite_already_registered"
        );
    }
}
