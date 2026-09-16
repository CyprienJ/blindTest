package com.blindtest.controller;

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

class SessionSummaryOpenApiContractTest {

    private static Map<String, Object> specification;

    @BeforeAll
    static void loadSpecification() throws IOException {
        try (InputStream input = Files.newInputStream(locateSpecification())) {
            specification = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
        }
    }

    @Test
    void documentsOwnerOnlyDurableSummaryAndSafeProjection() {
        Map<String, Object> get = map(map(paths().get("/sessions/{sessionId}/summary")).get("get"));
        Map<String, Object> responses = map(get.get("responses"));
        Map<String, Object> success = map(responses.get("200"));
        Map<String, Object> content = map(success.get("content"));
        Map<String, Object> responseSchema =
                map(map(content.get("application/json")).get("schema"));

        assertThat(responseSchema.get("$ref")).
                isEqualTo("#/components/schemas/CurrentGameSummary");
        assertThat(responses).containsKeys("401", "404");
        assertThat(map(success.get("headers"))).containsKey("Cache-Control");

        Map<String, Object> summaryProperties = properties("CurrentGameSummary");
        assertThat(summaryProperties).containsKeys(
                "sessionId", "totalScore", "totalRounds", "rounds");
        assertThat(summaryProperties).doesNotContainKeys(
                "youtubeUrl", "audioObjectKey", "audioChecksum", "rawGuess");

        Map<String, Object> roundProperties = properties("GameSummaryRound");
        assertThat(roundProperties).containsKeys(
                "roundIndex", "outcome", "points", "title", "artist", "composer", "album");
        assertThat(roundProperties).doesNotContainKeys(
                "trackId", "youtubeUrl", "audioObjectKey", "audioChecksum",
                "rawGuess", "answerAliases");
    }

    private Map<String, Object> paths() {
        return map(specification.get("paths"));
    }

    private Map<String, Object> properties(String schemaName) {
        Map<String, Object> schemas = map(map(specification.get("components")).get("schemas"));
        return map(map(schemas.get(schemaName)).get("properties"));
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
