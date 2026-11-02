package org.vfeeg.eegfaktura.billing.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileTypeDetectorTest {

    @Test
    void detectsFromContent() {
        assertEquals(Optional.of(FileTypeDetector.PNG),
                FileTypeDetector.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1}));
        assertEquals(Optional.of(FileTypeDetector.JPEG),
                FileTypeDetector.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
        assertEquals(Optional.of(FileTypeDetector.GIF),
                FileTypeDetector.detect("GIF89a...".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(Optional.of(FileTypeDetector.PDF),
                FileTypeDetector.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void unknownOrTooShortIsEmpty() {
        assertEquals(Optional.empty(), FileTypeDetector.detect(null));
        assertEquals(Optional.empty(), FileTypeDetector.detect(new byte[0]));
        assertEquals(Optional.empty(), FileTypeDetector.detect(new byte[]{(byte) 0x89, 'P'}));
        assertEquals(Optional.empty(),
                FileTypeDetector.detect("<html><script>".getBytes(StandardCharsets.US_ASCII)));
    }
}
