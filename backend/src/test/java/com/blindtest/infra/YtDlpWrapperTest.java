package com.blindtest.infra;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class YtDlpWrapperTest {

    @Test
    void startInvokesYtDlpWithCorrectArgs() throws IOException {
        Process mockProcess = mock(Process.class);
        when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        ProcessFactory factory = mock(ProcessFactory.class);
        when(factory.start(anyList())).thenReturn(mockProcess);

        YtDlpWrapper wrapper = new YtDlpWrapper(factory);
        String url = "https://youtube.com/watch?v=test";
        Path output = Path.of("/tmp/test-output");
        wrapper.start(url, output);

        verify(factory).start(List.of(
                "yt-dlp", "-x", "--audio-format", "mp3",
                "--output", output.toString(),
                "--newline",
                url));
    }
}
