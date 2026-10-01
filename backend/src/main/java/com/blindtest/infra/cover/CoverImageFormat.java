package com.blindtest.infra.cover;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/** Raster formats accepted as cover sources, detected from their magic bytes. */
public enum CoverImageFormat {
    JPEG("jpeg_pipe"),
    PNG("png_pipe"),
    WEBP("webp_pipe");

    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private final String ffmpegDemuxer;

    CoverImageFormat(String ffmpegDemuxer) {
        this.ffmpegDemuxer = ffmpegDemuxer;
    }

    /** Returns the FFmpeg demuxer forced for this format, so no other parser is reachable. */
    public String ffmpegDemuxer() {
        return ffmpegDemuxer;
    }

    /** Detects the format from the content; the declared name and type are never trusted. */
    public static Optional<CoverImageFormat> detect(byte[] content) {
        if (content == null || content.length < 12) {
            return Optional.empty();
        }
        if ((content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8
                && (content[2] & 0xff) == 0xff) {
            return Optional.of(JPEG);
        }
        if (Arrays.equals(content, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, PNG_SIGNATURE.length)) {
            return Optional.of(PNG);
        }
        if ("RIFF".equals(ascii(content, 0)) && "WEBP".equals(ascii(content, 8))) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static String ascii(byte[] content, int offset) {
        return new String(content, offset, 4, StandardCharsets.US_ASCII);
    }
}
