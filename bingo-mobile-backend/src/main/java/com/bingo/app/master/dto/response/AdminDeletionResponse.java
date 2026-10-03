package com.bingo.app.master.dto.response;

/**
 * What a delete actually removed, so the super admin is told the scale of it
 * rather than getting a bare "done" for a tenant database full of games.
 */
public record AdminDeletionResponse(
        Long adminUserId,
        String businessName,
        int playersRemoved,
        String tenantDatabase
) {
}