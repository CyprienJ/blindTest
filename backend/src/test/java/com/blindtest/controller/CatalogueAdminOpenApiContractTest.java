package com.blindtest.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.controller.admin.AdminImportController;
import com.blindtest.controller.admin.AdminMediaController;
import com.blindtest.controller.admin.AdminPlaylistController;
import com.blindtest.controller.admin.AdminTrackController;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class CatalogueAdminOpenApiContractTest {

    private static final Set<Class<?>> CONTROLLERS = Set.of(
            CatalogueController.class,
            AdminImportController.class,
            AdminMediaController.class,
            AdminPlaylistController.class,
            AdminTrackController.class);

    private static Map<String, Object> specification;

    @BeforeAll
    static void loadSpecification() throws IOException {
        Path specificationPath = locateSpecification();
        try (InputStream input = Files.newInputStream(specificationPath)) {
            specification = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
        }
    }

    @Test
    void openApiDocumentsEveryDeliveredCatalogueAndAdminOperation() {
        assertThat(documentedOperations()).containsExactlyElementsOf(controllerOperations());
    }

    @Test
    void publicCatalogueIsExplicitlyAnonymousAndDoesNotExposePrivateMediaFields() {
        for (String path : List.of("/tracks", "/playlists", "/playlists/{playlistId}")) {
            Map<String, Object> operation = operation(path, "get");
            assertThat(operation.get("security")).as(path).isEqualTo(List.of());
        }

        Map<String, Object> properties = schemaProperties("Track");
        assertThat(properties).containsKeys(
                "id", "title", "artist", "album", "composer", "work", "category",
                "decade", "durationSeconds", "imageUrl");
        assertThat(properties).doesNotContainKeys(
                "audioUrl", "audioObjectKey", "audioChecksum", "audioSizeBytes",
                "youtubeUrl", "status", "excerptStartSeconds");
    }

    @Test
    void everyAdminOperationDeclaresAuthenticationAndAuthorizationFailures() {
        paths().forEach((path, pathItem) -> {
            if (!path.startsWith("/admin/")) {
                return;
            }
            httpOperations(pathItem).forEach((method, operation) -> {
                Map<String, Object> responses = map(operation.get("responses"));
                assertThat(responses).as(method.toUpperCase() + " " + path).
                        containsKeys("401", "403");
            });
        });
    }

    @Test
    void adminPlaylistDetailsUseAdministratorTrackSnapshots() {
        Map<String, Object> properties = schemaProperties("PlaylistAdminDetail");
        Map<String, Object> tracks = map(properties.get("tracks"));
        Map<String, Object> items = map(tracks.get("items"));

        assertThat(items.get("$ref")).isEqualTo("#/components/schemas/TrackAdmin");
        assertThat(responseSchema("/admin/playlists/{playlistId}", "get", "200").get("$ref")).
                isEqualTo("#/components/schemas/PlaylistAdminDetail");
    }

    private Set<Operation> controllerOperations() {
        Set<Operation> operations = new TreeSet<>();
        for (Class<?> controller : CONTROLLERS) {
            RequestMapping baseMapping = AnnotatedElementUtils.findMergedAnnotation(
                    controller, RequestMapping.class);
            String basePath = firstPath(baseMapping);
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(
                        method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String path = stripApiPrefix(basePath + firstPath(mapping));
                for (RequestMethod requestMethod : mapping.method()) {
                    operations.add(new Operation(normalizePath(path), requestMethod.name()));
                }
            }
        }
        return operations;
    }

    private Set<Operation> documentedOperations() {
        Set<Operation> operations = new TreeSet<>();
        paths().forEach((path, pathItem) -> {
            if (path.equals("/tracks") || path.startsWith("/playlists")
                    || path.startsWith("/admin/")) {
                httpOperations(pathItem).forEach((method, operation) ->
                        operations.add(new Operation(normalizePath(path), method.toUpperCase())));
            }
        });
        return operations;
    }

    private Map<String, Object> operation(String path, String method) {
        return map(paths().get(path)).containsKey(method)
                ? map(map(paths().get(path)).get(method)) : Map.of();
    }

    private Map<String, Object> responseSchema(String path, String method, String status) {
        Map<String, Object> responses = map(operation(path, method).get("responses"));
        Map<String, Object> response = map(responses.get(status));
        Map<String, Object> content = map(response.get("content"));
        return map(map(content.get("application/json")).get("schema"));
    }

    private Map<String, Object> schemaProperties(String schemaName) {
        Map<String, Object> components = map(specification.get("components"));
        Map<String, Object> schemas = map(components.get("schemas"));
        return map(map(schemas.get(schemaName)).get("properties"));
    }

    private Map<String, Map<String, Object>> httpOperations(Object pathItemValue) {
        Map<String, Map<String, Object>> operations = new LinkedHashMap<>();
        map(pathItemValue).forEach((method, value) -> {
            if (Set.of("get", "post", "put", "delete", "patch").contains(method)) {
                operations.put(method, map(value));
            }
        });
        return operations;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> paths() {
        return (Map<String, Object>) specification.get("paths");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    private String firstPath(RequestMapping mapping) {
        if (mapping == null || mapping.path().length == 0) {
            return "";
        }
        return mapping.path()[0];
    }

    private String stripApiPrefix(String path) {
        return path.startsWith("/api") ? path.substring(4) : path;
    }

    private String normalizePath(String path) {
        return path.replaceAll("\\{[^/]+}", "{}");
    }

    private static Path locateSpecification() {
        List<Path> candidates = new ArrayList<>();
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        candidates.add(workingDirectory.resolve("docs/swagger.yaml"));
        candidates.add(workingDirectory.resolve("../docs/swagger.yaml"));
        return candidates.stream().map(Path::normalize).filter(Files::isRegularFile).findFirst().
                orElseThrow(() -> new IllegalStateException("Cannot locate docs/swagger.yaml"));
    }

    private record Operation(String path, String method) implements Comparable<Operation> {
        @Override
        public int compareTo(Operation other) {
            int pathComparison = path.compareTo(other.path);
            return pathComparison != 0 ? pathComparison : method.compareTo(other.method);
        }
    }
}
