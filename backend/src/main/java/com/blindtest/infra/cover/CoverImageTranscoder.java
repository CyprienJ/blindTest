package com.blindtest.infra.cover;

import com.blindtest.infra.ProcessFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Wraps {@code ffmpeg} to normalize a cover into a metadata-free JPEG of at most 600x600. */
@Component
public class CoverImageTranscoder {

    static final int MAX_DIMENSION = 600;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final ProcessFactory processFactory;

    /** Creates the transcoder over the replaceable process factory. */
    public CoverImageTranscoder(ProcessFactory processFactory) {
        this.processFactory = processFactory;
    }

    /**
     * Decodes {@code source} with the demuxer of its detected format only, keeps the first frame,
     * shrinks it to fit 600x600 without upscaling, and writes a JPEG to {@code target}.
     */
    public void toJpeg(Path source, CoverImageFormat format, Path target)
            throws IOException, InterruptedException {
        Process process = processFactory.start(List.of(
                "ffmpeg", "-nostdin", "-v", "error", "-hide_banner", "-threads", "1",
                "-f", format.ffmpegDemuxer(), "-i", source.toString(),
                "-frames:v", "1", "-map_metadata", "-1",
                "-vf", "scale=w='min(" + MAX_DIMENSION + ",iw)':h='min(" + MAX_DIMENSION
                        + ",ih)':force_original_aspect_ratio=decrease,format=yuvj420p",
                "-c:v", "mjpeg", "-q:v", "3", "-f", "image2", "-update", "1",
                "-y", target.toString()));
        // Stderr is merged into stdout; "-v error" keeps it far below the pipe buffer.
        try (InputStream ignored = process.getInputStream()) {
            if (!process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("Cover conversion timed out");
            }
            if (process.exitValue() != 0) {
                throw new IOException("FFmpeg could not decode the cover image");
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
