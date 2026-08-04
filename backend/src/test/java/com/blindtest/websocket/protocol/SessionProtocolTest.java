package com.blindtest.websocket.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blindtest.model.game.GameAnswerType;
import com.blindtest.model.game.GameStatus;
import com.blindtest.model.game.RoundOutcome;
import com.blindtest.model.game.RoundStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionProtocolTest {

    private static final UUID SESSION_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PARTICIPANT_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID COMMAND_ID =
            UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID EVENT_ID =
            UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final Instant NOW = Instant.parse("2026-08-04T13:00:00Z");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().
            findAndRegisterModules().
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void exposesCanonicalConnectionDestinations() {
        assertThat(SessionProtocol.WEB_SOCKET_ENDPOINT).isEqualTo("/ws");
        assertThat(SessionProtocol.SOCK_JS_ENDPOINT).isEqualTo("/ws-sockjs");
        assertThat(SessionProtocol.commandDestination(SESSION_ID)).
                isEqualTo("/app/sessions/" + SESSION_ID);
        assertThat(SessionProtocol.publicEventDestination(SESSION_ID)).
                isEqualTo("/topic/sessions/" + SESSION_ID);
        assertThat(SessionProtocol.privateEventDestination(SESSION_ID)).
                isEqualTo("/user/queue/sessions/" + SESSION_ID);
    }

    @Test
    void serializesAndDeserializesEveryCommandWithIdempotencyMetadata() throws Exception {
        for (SessionCommand command : List.of(
                command(SessionCommandType.START_GAME, null),
                command(SessionCommandType.SUBMIT_ANSWER, "Take on me"),
                command(SessionCommandType.SKIP_ROUND, null),
                command(SessionCommandType.REQUEST_SNAPSHOT, null))) {
            String json = OBJECT_MAPPER.writeValueAsString(command);
            SessionCommand restored = OBJECT_MAPPER.readValue(json, SessionCommand.class);

            assertThat(restored).isEqualTo(command);
            JsonNode tree = OBJECT_MAPPER.readTree(json);
            assertThat(tree.get("protocolVersion").asInt()).isEqualTo(SessionProtocol.VERSION);
            assertThat(tree.get("commandId").asText()).isEqualTo(COMMAND_ID.toString());
            assertThat(tree.get("expectedSessionVersion").asLong()).isEqualTo(7L);
        }
    }

    @Test
    void rejectsUnsupportedOrMalformedCommands() {
        assertThatThrownBy(() -> new SessionCommand(
                2, COMMAND_ID, 0, SessionCommandType.START_GAME, null)).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("unsupported");
        assertThatThrownBy(() -> new SessionCommand(
                SessionProtocol.VERSION, null, 0, SessionCommandType.START_GAME, null)).
                isInstanceOf(NullPointerException.class).
                hasMessageContaining("commandId");
        assertThatThrownBy(() -> new SessionCommand(
                SessionProtocol.VERSION, COMMAND_ID, -1, SessionCommandType.START_GAME, null)).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("expectedSessionVersion");
        assertThatThrownBy(() -> command(SessionCommandType.SUBMIT_ANSWER, "  ")).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("answer");
        assertThatThrownBy(() -> command(SessionCommandType.SKIP_ROUND, "not allowed")).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("only allowed");
    }

    @Test
    void serializesSharedRoundEventWithAuthoritativeOrderingAndTiming() throws Exception {
        var payload = new PublicSessionEventPayload.RoundStarted(
                2,
                10,
                NOW.plusSeconds(2),
                NOW.plusSeconds(32),
                15,
                URI.create("https://media.example.test/audio.mp3?signature=safe"));
        var event = new PublicSessionEvent(
                metadata(), PublicSessionEventType.ROUND_STARTED, payload);

        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(event));

        assertThat(json.get("type").asText()).isEqualTo("ROUND_STARTED");
        assertThat(json.at("/metadata/protocolVersion").asInt()).isEqualTo(1);
        assertThat(json.at("/metadata/sessionVersion").asLong()).isEqualTo(8L);
        assertThat(json.at("/payload/roundIndex").asInt()).isEqualTo(2);
        assertThat(json.at("/payload/startsAt").asText()).isEqualTo("2026-08-04T13:00:02Z");
        assertThat(json.toString()).doesNotContain("objectKey");
    }

    @Test
    void keepsSecondaryFeedbackInThePrivateEnvelope() throws Exception {
        var payload = new PrivateSessionEventPayload.SecondaryMatch(
                GameAnswerType.ARTIST, "a-ha", false);
        var event = new PrivateSessionEvent(
                metadata(),
                PARTICIPANT_ID,
                Optional.of(COMMAND_ID),
                PrivateSessionEventType.SECONDARY_MATCH,
                payload);

        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(event));

        assertThat(json.get("type").asText()).isEqualTo("SECONDARY_MATCH");
        assertThat(json.get("recipientParticipantId").asText()).
                isEqualTo(PARTICIPANT_ID.toString());
        assertThat(json.get("commandId").asText()).isEqualTo(COMMAND_ID.toString());
        assertThat(json.at("/payload/answerType").asText()).isEqualTo("ARTIST");
        assertThat(json.at("/payload/matchedValue").asText()).isEqualTo("a-ha");
    }

    @Test
    void validatesPublicAndPrivateDiscriminatorPayloadPairs() {
        var publicPayload = new PublicSessionEventPayload.RoundPreparing(0, 10);
        assertThatThrownBy(() -> new PublicSessionEvent(
                metadata(), PublicSessionEventType.SESSION_READY, publicPayload)).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("does not match");

        var privatePayload = new PrivateSessionEventPayload.Error(
                "SESSION_EXPIRED", "The session has expired.", false);
        assertThatThrownBy(() -> new PrivateSessionEvent(
                metadata(),
                PARTICIPANT_ID,
                Optional.of(COMMAND_ID),
                PrivateSessionEventType.ANSWER_REJECTED,
                privatePayload)).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("does not match");
    }

    @Test
    void requiresCommandCorrelationForPrivateCommandFeedback() {
        var rejected = new PrivateSessionEventPayload.AnswerRejected(
                AnswerRejectionReason.WRONG_ANSWER, Optional.empty());

        assertThatThrownBy(() -> new PrivateSessionEvent(
                metadata(),
                PARTICIPANT_ID,
                Optional.empty(),
                PrivateSessionEventType.ANSWER_REJECTED,
                rejected)).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("commandId");

        var asynchronousError = new PrivateSessionEvent(
                metadata(),
                PARTICIPANT_ID,
                Optional.empty(),
                PrivateSessionEventType.ERROR,
                new PrivateSessionEventPayload.Error(
                        "PLAYBACK_UNAVAILABLE", "Playback is temporarily unavailable.", true));
        assertThat(asynchronousError.commandId()).isEmpty();
    }

    @Test
    void modelsCooldownAndRejectsAmbiguousRetryTiming() {
        Instant retryAt = NOW.plusMillis(750);
        assertThat(new PrivateSessionEventPayload.AnswerRejected(
                AnswerRejectionReason.COOLDOWN, Optional.of(retryAt)).retryAt()).
                contains(retryAt);

        assertThatThrownBy(() -> new PrivateSessionEventPayload.AnswerRejected(
                AnswerRejectionReason.COOLDOWN, Optional.empty())).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("retryAt");
        assertThatThrownBy(() -> new PrivateSessionEventPayload.AnswerRejected(
                AnswerRejectionReason.WRONG_ANSWER, Optional.of(retryAt))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("retryAt");
    }

    @Test
    void validatesReconnectSnapshotAndKeepsDiscoveriesSecondary() {
        var round = new PrivateSessionEventPayload.CurrentRound(
                1,
                RoundStatus.PLAYING,
                Optional.of(NOW),
                Optional.of(NOW.plusSeconds(30)),
                Optional.of(URI.create("https://media.example.test/audio.mp3?signature=safe")),
                Optional.of(12),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0);
        var snapshot = new PrivateSessionEventPayload.CurrentSnapshot(
                GameStatus.PLAYING,
                1,
                10,
                900,
                Optional.of(round),
                List.of(GameAnswerType.ARTIST, GameAnswerType.ALBUM),
                NOW.plusSeconds(600));

        assertThat(snapshot.discoveredAnswerTypes()).
                containsExactly(GameAnswerType.ARTIST, GameAnswerType.ALBUM);
        assertThatThrownBy(() -> new PrivateSessionEventPayload.CurrentSnapshot(
                GameStatus.PLAYING,
                1,
                10,
                900,
                Optional.of(round),
                List.of(GameAnswerType.TITLE),
                NOW.plusSeconds(600))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("secondary");
    }

    @Test
    void validatesSharedPayloadLifecycleShapes() {
        assertThatThrownBy(() -> new PublicSessionEventPayload.RoundStarted(
                0, 5, NOW, NOW, 0, URI.create("https://media.example.test/audio.mp3"))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("deadline");
        assertThatThrownBy(() -> new PublicSessionEventPayload.GameFinished(
                1000, 5, 2, 1, 1)).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("inconsistent");
        assertThatThrownBy(() -> new PublicSessionEventPayload.RoundRevealed(
                0,
                5,
                RoundOutcome.TITLE_FOUND,
                "Take On Me",
                "a-ha",
                Optional.of("  "),
                Optional.empty(),
                800,
                800,
                NOW.plusSeconds(4))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("composer");
    }

    private SessionCommand command(SessionCommandType type, String answer) {
        return new SessionCommand(
                SessionProtocol.VERSION, COMMAND_ID, 7, type, answer);
    }

    private SessionEventMetadata metadata() {
        return new SessionEventMetadata(
                SessionProtocol.VERSION, EVENT_ID, SESSION_ID, 8, NOW);
    }
}
