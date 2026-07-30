package com.blindtest.infra;

import com.blindtest.config.properties.ImportProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Wraps the {@code yt-dlp} CLI to download and extract audio from a YouTube URL. */
@Component
@RequiredArgsConstructor
public class YtDlpWrapper {

    private final ProcessFactory processFactory;
    private final ImportProperties importProperties;

    /**
     * Starts a {@code yt-dlp} process that extracts audio as MP3 to {@code outputTemplate}.
     * The actual file is written to {@code outputTemplate + ".mp3"}.
     */
    public Process start(String youtubeUrl, Path outputTemplate) throws IOException {
        return processFactory.start(List.of(
                "yt-dlp", "--no-playlist", "--max-filesize", Long.toString(importProperties.maxSizeBytes()),
                "-x", "--audio-format", "mp3", "--postprocessor-args", "ffmpeg:-threads 1",
                "--output", outputTemplate.toString(),
                "--newline",
                "--",
                youtubeUrl));
    }
}
