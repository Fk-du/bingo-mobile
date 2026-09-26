package com.bingo.app.tenant.controller;

import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.infrastructure.storage.LocalScreenshotStorage;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;

@RestController
@RequestMapping("/api/v1/screenshots")
@RequiredArgsConstructor
public class ScreenshotController {

    private final LocalScreenshotStorage screenshotStorage;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    public ApiResponse<String> upload(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        // Admins & superadmins file their fund-request proof under "unsorted";
        // players upload under their agent's folder: data/screenshots/<agent>/...
        String folder = switch (principal.getUser().getRole()) {
            case PLAYER -> resolveAgentFolderOf(principal.getUser());
            default -> "unsorted";
        };
        String filename = screenshotStorage.store(file, folder);
        return ApiResponse.ok("Screenshot uploaded", "/api/v1/screenshots/" + filename);
    }

    @GetMapping("/**")
    public ResponseEntity<UrlResource> serve(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) throws MalformedURLException {
        String prefix = "/api/v1/screenshots/";
        String filepath = request.getRequestURI().substring(request.getRequestURI().indexOf(prefix) + prefix.length());
        String decoded = java.net.URLDecoder.decode(filepath, java.nio.charset.StandardCharsets.UTF_8);

        // Ownership check: which folder may this user read? The folder is normalized
        // the same way files were stored, so business names with spaces, uppercase or
        // special characters match the physical folder on disk.
        String allowedFolder = resolveReadableFolder(principal.getUser());

        // Extract the first path segment (agent folder name)
        String folderSegment = decoded.contains("/") ? decoded.substring(0, decoded.indexOf('/')) : "";
        boolean superAdmin = principal.getUser().getRole() == Role.SUPER_ADMIN;
        if (!superAdmin && !folderSegment.equals(allowedFolder) && !"unsorted".equals(folderSegment)) {
            return ResponseEntity.status(403).build();
        }

        var stored = screenshotStorage.load(decoded);
        if (stored == null) {
            return ResponseEntity.notFound().build();
        }
        UrlResource resource = new UrlResource(stored.path().toUri());
        return ResponseEntity.ok()
                .contentType(stored.mediaType())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(resource);
    }

    /**
     * Folder an ADMIN/SUPER_ADMIN may read on serve: their own agent folder
     * (where their players' payment proofs live) or {@code unsorted} for admins
     * without players yet, and for fund-request proof uploaded by admins. Players
     * may only read their own agent's folder.
     */
    private String resolveReadableFolder(User user) {
        return switch (user.getRole()) {
            case SUPER_ADMIN -> "unsorted";
            case ADMIN -> resolveOwnFolder(user);
            case PLAYER -> resolveAgentFolderOf(user);
        };
    }

    /**
     * The folder a player's proof is stored under: their agent's (admin's)
     * business name or username, normalized exactly like
     * {@link LocalScreenshotStorage} stores it.
     */
    private String resolveAgentFolderOf(User player) {
        if (player.getAdminUserId() == null) {
            return "unsorted";
        }
        return userRepository.findById(player.getAdminUserId())
                .map(this::resolveOwnFolder)
                .orElse("agent-" + player.getAdminUserId());
    }

    /**
     * The folder an admin's own players upload to: their business name/username.
     */
    private String resolveOwnFolder(User admin) {
        String name = admin.getBusinessName() != null && !admin.getBusinessName().isBlank()
                ? admin.getBusinessName()
                : admin.getTelegramUsername() != null ? admin.getTelegramUsername() : "agent-" + admin.getId();
        return LocalScreenshotStorage.sanitizeFolder(name);
    }
}
