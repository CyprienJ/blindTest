package com.blindtest.infra;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/** Wraps {@code ffmpeg} to re-encode an uploaded audio file into a clean MP3. */
@Component
public class AudioTranscoder {

    private final ProcessFactory processFactory;

    public AudioTranscoder(ProcessFactory processFactory) {
        this.processFactory = processFactory;
    }

    /**
     * Starts an {@code ffmpeg} process that keeps only the first audio stream, drops all source
     * metadata, and writes a 160 kbit/s stereo MP3 to {@code target}. Progress is reported on
     * standard output as {@code key=value} lines, including {@code out_time_us}.
     */
    public Process start(Path source, Path target) throws IOException {
        return processFactory.start(List.of(
                "ffmpeg", "-nostdin", "-v", "error", "-hide_banner", "-threads", "1",
                "-i", source.toString(),
                "-map", "0:a:0", "-vn", "-sn", "-dn", "-map_metadata", "-1",
                "-ac", "2", "-ar", "44100", "-codec:a", "libmp3lame", "-b:a", "160k",
                "-f", "mp3", "-progress", "pipe:1", "-nostats", "-y", target.toString()));
    }
}
