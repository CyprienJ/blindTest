package com.blindtest.service;

/** Internal import failure carrying only a predefined client-safe message. */
final class ImportJobFailureException extends RuntimeException {

    enum Reason {
        PROVIDER("The media provider could not complete the import"),
        TIMEOUT("The media import timed out"),
        SIZE_LIMIT("The imported audio exceeds the maximum allowed size"),
        INVALID_MEDIA("The imported media is not a valid MP3 file"),
        UPLOAD_MISSING("The uploaded file is no longer available; upload it again"),
        TRANSCODE("The uploaded audio could not be converted");

        private final String safeMessage;

        Reason(String safeMessage) {
            this.safeMessage = safeMessage;
        }
    }

    private final Reason reason;

    ImportJobFailureException(Reason reason) {
        super(reason.safeMessage);
        this.reason = reason;
    }

    String safeMessage() {
        return reason.safeMessage;
    }
}
