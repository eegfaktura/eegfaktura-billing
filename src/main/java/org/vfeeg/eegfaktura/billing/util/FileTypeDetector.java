package org.vfeeg.eegfaktura.billing.util;

import java.util.Optional;

/**
 * Detects the file type from the leading bytes (magic numbers). Content-Type header and file
 * extension come from the client and are not trusted.
 */
public final class FileTypeDetector {

    public static final String PNG = "image/png";
    public static final String JPEG = "image/jpeg";
    public static final String GIF = "image/gif";
    public static final String PDF = "application/pdf";

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF87_MAGIC = {'G', 'I', 'F', '8', '7', 'a'};
    private static final byte[] GIF89_MAGIC = {'G', 'I', 'F', '8', '9', 'a'};
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    private FileTypeDetector() {
    }

    /** The detected MIME type, or empty if the content is none of PNG, JPEG, GIF or PDF. */
    public static Optional<String> detect(byte[] data) {
        if (data == null) {
            return Optional.empty();
        }
        if (startsWith(data, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        if (startsWith(data, JPEG_MAGIC)) {
            return Optional.of(JPEG);
        }
        if (startsWith(data, GIF87_MAGIC) || startsWith(data, GIF89_MAGIC)) {
            return Optional.of(GIF);
        }
        if (startsWith(data, PDF_MAGIC)) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, byte[] magic) {
        if (data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
