package com.blindtest.service;

import com.blindtest.model.dto.AdminUserResponse;
import com.blindtest.model.dto.UpdateUserRequest;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.repository.GameHistoryParticipantRepository;
import com.blindtest.repository.PlaylistRepository;
import com.blindtest.repository.RefreshTokenRepository;
import com.blindtest.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Administrator account management. Registration is invitation-only, so the account list stays
 * small enough to return in full and sort in the browser.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PlaylistRepository playlistRepository;
    private final GameHistoryParticipantRepository participantRepository;

    /** Returns every account with its completed-game and playlist counts, newest first. */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> list() {
        Map<UUID, Long> gamesByUser = new HashMap<>();
        for (Object[] row : participantRepository.countGamesByUser()) {
            gamesByUser.put((UUID) row[0], (Long) row[1]);
        }
        return userRepository.findAll().stream().
                sorted(Comparator.comparing(UserEntity::getCreatedAt).reversed()).
                map(user -> toResponse(user, gamesByUser.getOrDefault(user.getId(), 0L))).
                toList();
    }

    /**
     * Updates an account's username, email, and role. Changing the role signs the account out
     * of every device so the new role applies at the next sign-in.
     *
     * @throws ResponseStatusException 404 if unknown, 409 if the username or email is taken or
     *     an administrator changes their own role
     */
    @Transactional
    public AdminUserResponse update(UUID userId, UpdateUserRequest request, UUID adminId) {
        UserEntity user = findOrThrow(userId);
        if (userId.equals(adminId) && user.getRole() != request.role()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ADMIN_SELF_CHANGE_FORBIDDEN");
        }
        String username = request.username().trim();
        String email = InvitationIssuer.normalizeEmail(request.email());
        if (!username.equals(user.getUsername()) && userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "USERNAME_TAKEN");
        }
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_TAKEN");
        }
        boolean roleChanged = user.getRole() != request.role();
        user.setUsername(username);
        user.setEmail(email);
        user.setRole(request.role());
        UserEntity saved = userRepository.saveAndFlush(user);
        if (roleChanged) {
            refreshTokenRepository.deleteByUserId(userId);
        }
        return toResponse(saved, countGames(userId));
    }

    /**
     * Deletes an account. Its refresh tokens are removed and its game history is anonymized by
     * the database foreign keys.
     *
     * @throws ResponseStatusException 404 if unknown, 409 when an administrator deletes their own
     *     account or the account still owns playlists
     */
    @Transactional
    public void delete(UUID userId, UUID adminId) {
        UserEntity user = findOrThrow(userId);
        if (userId.equals(adminId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ADMIN_SELF_CHANGE_FORBIDDEN");
        }
        if (playlistRepository.countByCreatedById(userId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "USER_OWNS_PLAYLISTS");
        }
        refreshTokenRepository.deleteByUserId(userId);
        userRepository.delete(user);
    }

    private long countGames(UUID userId) {
        return participantRepository.countGamesByUser().stream().
                filter(row -> userId.equals(row[0])).
                map(row -> (Long) row[1]).
                findFirst().
                orElse(0L);
    }

    private UserEntity findOrThrow(UUID userId) {
        return userRepository.findById(userId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private AdminUserResponse toResponse(UserEntity user, long gamesPlayed) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isEmailVerified(),
                utc(user.getCreatedAt()),
                utc(user.getLastConnexion()),
                gamesPlayed,
                playlistRepository.countByCreatedById(user.getId()));
    }

    private static Instant utc(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.toInstant(ZoneOffset.UTC);
    }
}
