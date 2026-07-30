package com.blindtest.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InMemoryMediaStorageTest {

    @TempDir
    private Path tempDir;

    @Test
    void supportsTheCompleteStorageContractWithoutAnExternalService() throws Exception {
        Path source = tempDir.resolve("fixture.mp3");
        byte[] content = new byte[] {1, 2, 3, 4};
        Files.write(source, content);
        InMemoryMediaStorage storage = new InMemoryMediaStorage();

        var stored = storage.store("audio/fixture.mp3", source, "audio/mpeg");

        assertThat(stored.objectKey()).isEqualTo("audio/fixture.mp3");
        assertThat(stored.size()).isEqualTo(content.length);
        assertThat(storage.exists(stored.objectKey())).isTrue();
        assertThat(storage.content(stored.objectKey())).isEqualTo(content);
        assertThat(storage.contentType(stored.objectKey())).isEqualTo("audio/mpeg");
        assertThat(storage.presignedGetUrl(stored.objectKey()).toString()).
                isEqualTo("https://media.example.test/audio/fixture.mp3?signature=test");
        assertThat(storage.listObjectKeys()).containsExactly("audio/fixture.mp3");

        storage.delete(stored.objectKey());
        storage.delete(stored.objectKey());

        assertThat(storage.exists(stored.objectKey())).isFalse();
    }
}
