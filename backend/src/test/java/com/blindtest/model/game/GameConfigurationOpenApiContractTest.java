package com.blindtest.model.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class GameConfigurationOpenApiContractTest {

    private static Map<String, Object> specification;

    @BeforeAll
    static void loadSpecification() throws IOException {
        try (InputStream input = Files.newInputStream(locateSpecification())) {
            specification = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
        }
    }

    @Test
    void openApiUsesTheAuthoritativeTrackCountContract() {
        Map<String, Object> trackCount = gameRule("trackCount");

        assertThat(trackCount.get("minimum")).isEqualTo(
                GameConfigurationLimits.MINIMUM_TRACK_COUNT);
        assertThat(trackCount.get("maximum")).isEqualTo(
                GameConfigurationLimits.MAXIMUM_TRACK_COUNT);
        assertThat(trackCount.get("multipleOf")).isEqualTo(
                GameConfigurationLimits.TRACK_COUNT_INCREMENT);
        assertThat(trackCount.get("default")).isEqualTo(
                GameConfigurationLimits.DEFAULT_TRACK_COUNT);
    }

    @Test
    void openApiUsesTheAuthoritativeRoundDurationContract() {
        Map<String, Object> duration = gameRule("timerSeconds");

        assertThat(duration.get("minimum")).isEqualTo(
                GameConfigurationLimits.MINIMUM_ROUND_DURATION_SECONDS);
        assertThat(duration.get("maximum")).isEqualTo(
                GameConfigurationLimits.MAXIMUM_ROUND_DURATION_SECONDS);
        assertThat(duration.get("multipleOf")).isEqualTo(
                GameConfigurationLimits.ROUND_DURATION_INCREMENT_SECONDS);
        assertThat(duration.get("default")).isEqualTo(
                GameConfigurationLimits.DEFAULT_ROUND_DURATION_SECONDS);
    }

    @Test
    void openApiKeepsGameConfigurationExtensionsReserved() {
        Map<String, Object> createSession = schema("CreateSessionRequest");
        Map<String, Object> extensions = map(map(createSession.get("properties")).get("extensions"));

        assertThat(extensions.get("maxProperties")).isEqualTo(0);
    }

    @Test
    void openApiUsesTheDeliveredFlatSoloSessionRequest() {
        Map<String, Object> createSession = schema("CreateSessionRequest");

        assertThat(createSession.get("required")).isEqualTo(
                List.of("playlistId", "trackCount", "maxRoundSeconds"));
        assertThat(map(createSession.get("properties"))).containsOnlyKeys(
                "playlistId", "trackCount", "maxRoundSeconds", "extensions");
    }

    @Test
    void createEndpointReturnsConnectionInstructions() {
        Map<String, Object> paths = map(specification.get("paths"));
        Map<String, Object> post = map(map(paths.get("/sessions")).get("post"));
        Map<String, Object> responses = map(post.get("responses"));
        Map<String, Object> created = map(responses.get("201"));
        Map<String, Object> content = map(created.get("content"));
        Map<String, Object> schema = map(map(content.get("application/json")).get("schema"));

        assertThat(schema.get("$ref")).isEqualTo(
                "#/components/schemas/SoloSessionResponse");
        assertThat(map(schema("SoloSessionResponse").get("properties"))).
                containsKeys("sessionId", "participantId", "expiresAt", "connection");
    }

    private Map<String, Object> gameRule(String property) {
        return map(map(schema("GameRules").get("properties")).get(property));
    }

    private Map<String, Object> schema(String name) {
        return map(map(map(specification.get("components")).get("schemas")).get(name));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static Path locateSpecification() {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        return List.of(
                        workingDirectory.resolve("docs/swagger.yaml"),
                        workingDirectory.resolve("../docs/swagger.yaml")).stream().
                map(Path::normalize).
                filter(Files::isRegularFile).
                findFirst().
                orElseThrow(() -> new IllegalStateException("Cannot locate docs/swagger.yaml"));
    }
}
