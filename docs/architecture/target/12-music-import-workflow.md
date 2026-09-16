# 12. Music Import Workflow

[Up: Target design](../target.md)

Historical target design, not an implementation reference. Validated roadmap
decisions and executable code take precedence. See [current architecture](../../architecture.md).

1. The admin pastes the YouTube URL into the form
2. **POST** `/api/admin/tracks/import` → the server creates a `TrackImportJob` and responds `202 { jobId }`
3. The admin subscribes to `/user/queue/import/{jobId}` to track progress
4. The server runs `yt-dlp` in the background to extract bounded metadata and
   download the audio, then FFmpeg converts it to MP3 and generates at most 800
   normalized waveform peaks from a bounded mono PCM stream
5. Once done, the `IMPORT_DONE` event contains `jobId` and `trackId`
6. The frontend requests `GET /api/admin/tracks/{trackId}/preview-url` and
   `GET /api/admin/tracks/{trackId}/waveform` once, draws the waveform as SVG, then
   immediately seeks and plays whenever the admin moves or clicks the start selector
7. The admin corrects the extracted metadata when necessary, previews the 60-second
   selection window, and saves it through the track endpoint
8. The admin validates (**POST** `/api/admin/tracks/{id}/validate`) → `status = PUBLISHED`
   or rejects (**POST** `/api/admin/tracks/{id}/reject`) → `status = REJECTED`
9. Only `PUBLISHED` tracks are available in the catalogue for sessions

```java
// YtDlpWrapper.java (simplified)
public Path downloadAudio(String youtubeUrl, UUID jobId) throws IOException {
    Path outputPath = mediaDir.resolve(jobId + ".%(ext)s");
    ProcessBuilder pb = new ProcessBuilder(
        "yt-dlp", "-x", "--audio-format", "mp3",
        "-o", outputPath.toString(),
        youtubeUrl
    );
    Process process = pb.start();
    // read stdout for progress, notify via WS
    int exit = process.waitFor();
    if (exit != 0) throw new ImportException("yt-dlp failed");
    return mediaDir.resolve(jobId + ".mp3");
}
```

---
