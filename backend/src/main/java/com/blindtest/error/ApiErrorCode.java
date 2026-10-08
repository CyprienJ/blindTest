package com.blindtest.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/** Stable machine-readable REST error codes and their safe client-facing messages. */
public enum ApiErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Request body is malformed"),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Request parameter is invalid"),
    INVALID_OR_EXPIRED_TOKEN(HttpStatus.BAD_REQUEST, "Token is invalid or expired"),
    INVITATION_INVALID(HttpStatus.BAD_REQUEST, "Invitation is invalid, expired, or already used"),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "Access token is invalid or expired"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email address is not verified"),
    TURNSTILE_FAILED(HttpStatus.FORBIDDEN, "Anti-bot verification failed"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not allowed"),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "Email address is already registered"),
    USERNAME_TAKEN(HttpStatus.CONFLICT, "Username is already registered"),
    CONFLICT(HttpStatus.CONFLICT, "Request conflicts with current state"),
    INVITATION_ALREADY_ACCEPTED(HttpStatus.CONFLICT, "Invitation has already been accepted"),
    ADMIN_SELF_CHANGE_FORBIDDEN(
            HttpStatus.CONFLICT, "Administrators cannot delete their own account or change their own role"),
    USER_OWNS_PLAYLISTS(HttpStatus.CONFLICT, "Delete this user's playlists before deleting the user"),
    ANSWER_ALIAS_DUPLICATE(HttpStatus.CONFLICT, "Answer alias already exists"),
    TRACK_EXCERPT_INVALID(
            HttpStatus.CONFLICT, "Track excerpt window does not fit the playable audio"),
    TRACK_METADATA_INCOMPLETE(HttpStatus.CONFLICT, "Track metadata is incomplete"),
    IMPORT_ALREADY_ACTIVE(HttpStatus.CONFLICT, "An import for this YouTube video is already active"),
    IMPORT_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file exceeds the maximum allowed size"),
    IMPORT_FILE_UNSUPPORTED(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The uploaded file is not a supported audio file"),
    IMPORT_AUDIO_TOO_LONG(HttpStatus.BAD_REQUEST, "The uploaded audio exceeds the maximum duration"),
    IMPORT_METADATA_REQUIRED(
            HttpStatus.BAD_REQUEST, "Title and artist are required when the file has no embedded tags"),
    COVER_SUGGESTION_EXPIRED(HttpStatus.CONFLICT, "The cover suggestion expired; search again"),
    COVER_IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The cover image exceeds 5 MB"),
    COVER_IMAGE_UNSUPPORTED(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The cover must be a JPEG, PNG, or WebP image"),
    COVER_IMAGE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "The cover image could not be downloaded"),
    PLAYLIST_ORDER_INVALID(HttpStatus.BAD_REQUEST, "Playlist order must contain every track exactly once"),
    PLAYLIST_TRACK_DUPLICATE(HttpStatus.CONFLICT, "Track is already present in this playlist"),
    PLAYLIST_TRACK_NOT_PUBLISHED(HttpStatus.CONFLICT, "Only published tracks can be added to playlists"),
    GAME_CONFIGURATION_REQUIRED(HttpStatus.BAD_REQUEST, "A game configuration is required"),
    GAME_PLAYLIST_REQUIRED(HttpStatus.BAD_REQUEST, "A playlist must be selected"),
    GAME_TRACK_COUNT_INVALID(HttpStatus.BAD_REQUEST, "The requested track count is invalid"),
    GAME_ROUND_DURATION_INVALID(HttpStatus.BAD_REQUEST, "The maximum round duration is invalid"),
    GAME_CONFIGURATION_EXTENSION_UNSUPPORTED(
            HttpStatus.BAD_REQUEST, "The requested game configuration option is not supported"),
    GAME_PLAYLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "The selected playlist is unavailable"),
    GAME_PLAYLIST_INELIGIBLE(
            HttpStatus.CONFLICT, "The selected playlist has no eligible tracks"),
    GAME_PLAYLIST_INSUFFICIENT_TRACKS(
            HttpStatus.CONFLICT, "The selected playlist does not contain enough eligible tracks"),
    GAME_DIFFICULTY_INVALID(
            HttpStatus.BAD_REQUEST, "Select at least one difficulty level between 1 and 5"),
    GAME_DIFFICULTY_INSUFFICIENT_TRACKS(
            HttpStatus.CONFLICT,
            "The selected difficulty levels do not contain enough eligible tracks"),
    LOBBY_NOT_FOUND(HttpStatus.NOT_FOUND, "This lobby code is invalid or no longer open"),
    LOBBY_FULL(HttpStatus.CONFLICT, "This lobby is full"),
    LOBBY_HOST_REQUIRED(HttpStatus.FORBIDDEN, "Only the lobby host can do this"),
    LOBBY_NOT_READY(
            HttpStatus.CONFLICT, "At least two players must be present and every guest player ready"),
    LOBBY_PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "This player is not in the lobby"),
    LOBBY_AUDIO_MODE_INVALID(HttpStatus.BAD_REQUEST, "Choose who plays the round audio"),
    LOBBY_GAME_IN_PROGRESS(HttpStatus.CONFLICT, "The game is still being played"),
    GUEST_NICKNAME_INVALID(
            HttpStatus.BAD_REQUEST, "Nicknames have 2 to 20 characters and no control characters"),
    GUEST_NICKNAME_TAKEN(HttpStatus.CONFLICT, "Another player in this lobby uses this nickname"),
    GUEST_JOIN_RATE_LIMITED(
            HttpStatus.TOO_MANY_REQUESTS, "Too many attempts to join as a guest; try again later"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Media type is not supported"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus status;
    private final String message;

    ApiErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    /** Maps a response status and optional known reason code without exposing arbitrary reason text. */
    public static ApiErrorCode from(HttpStatusCode status, String reason) {
        if (reason != null) {
            try {
                ApiErrorCode specificCode = ApiErrorCode.valueOf(reason);
                if (specificCode.status.value() == status.value()) {
                    return specificCode;
                }
            } catch (IllegalArgumentException ignored) {
                // Unknown reasons deliberately fall back to a generic safe code.
            }
        }
        return switch (status.value()) {
            case 400 -> MALFORMED_REQUEST;
            case 401 -> AUTHENTICATION_REQUIRED;
            case 403 -> ACCESS_DENIED;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> CONFLICT;
            case 413 -> IMPORT_FILE_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> INTERNAL_ERROR;
        };
    }
}
