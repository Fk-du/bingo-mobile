package com.bingo.app.master.exception;

/**
 * A delete was refused. Deleting an agent takes their tenant database, their
 * players and their history with it and cannot be undone, so every refusal
 * carries wording the super admin can act on instead of a raw error.
 */
public class AdminDeletionException extends RuntimeException {

    private final String userMessage;
    private final String code;

    private AdminDeletionException(String message, String userMessage, String code) {
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

    public static AdminDeletionException notFound(Long adminUserId) {
        return new AdminDeletionException(
                "Admin not found: " + adminUserId,
                "This agent no longer exists. Refresh the list.",
                "admin_not_found");
    }

    public static AdminDeletionException notAnAdmin(Long adminUserId) {
        return new AdminDeletionException(
                "User is not an agent: " + adminUserId,
                "Only an agent account can be deleted here.",
                "admin_not_an_admin");
    }

    public static AdminDeletionException cannotDeleteSelf() {
        return new AdminDeletionException(
                "A super admin cannot delete their own account",
                "You cannot delete the account you are signed in with.",
                "admin_delete_self");
    }

    /**
     * A live game still has players in it and money in its pot. Suspending the
     * agent ends those games; deleting one must be the super admin's deliberate
     * second step, never a side effect of tidying up an old account.
     */
    public static AdminDeletionException hasOpenGames(int openGames) {
        return new AdminDeletionException(
                "Agent still has " + openGames + " open game(s)",
                "This agent still has " + openGames
                        + " game(s) open. Suspend the agent or end those games first, then delete.",
                "admin_has_open_games");
    }

    /** Withdrawals and top-up requests hold real money the agent still owes. */
    public static AdminDeletionException hasPendingMoney(long withdrawals, long coinRequests) {
        return new AdminDeletionException(
                "Agent has " + withdrawals + " pending withdrawal(s) and " + coinRequests + " pending top-up(s)",
                "This agent still has " + withdrawals + " pending withdrawal(s) and " + coinRequests
                        + " pending top-up(s). Settle those first, then delete.",
                "admin_has_pending_money");
    }
}