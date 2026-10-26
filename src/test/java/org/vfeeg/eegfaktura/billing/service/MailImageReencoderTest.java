package org.vfeeg.eegfaktura.billing.service;

import org.junit.jupiter.api.Test;
import org.vfeeg.eegfaktura.billing.util.FileTypeDetector;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uploaded mail images are re-encoded: metadata and appended data dropped, bombs and corrupt files 400. */
class MailImageReencoderTest {

    private static byte[] encode(int width, int height, int type, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, type);
        image.setRGB(0, 0, 0xff336699);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out));
        return out.toByteArray();
    }

    /** The JPEG markers before the image data (SOS), e.g. 0xE0 for APP0, 0xE1 for APP1/EXIF. */
    private static List<Integer> jpegMarkers(byte[] jpeg) {
        List<Integer> markers = new ArrayList<>();
        int i = 2; // after SOI
        while (i + 3 < jpeg.length && (jpeg[i] & 0xff) == 0xff) {
            int marker = jpeg[i + 1] & 0xff;
            markers.add(marker);
            if (marker == 0xda) {
                break;
            }
            i += 2 + (((jpeg[i + 2] & 0xff) << 8) | (jpeg[i + 3] & 0xff));
        }
        return markers;
    }

    @Test
    void jpegWithExifLosesTheApp1Segment() throws Exception {
        byte[] plain = encode(4, 3, BufferedImage.TYPE_INT_RGB, "jpeg");
        byte[] tiff = "Exif\0\0II*\0\u0008\0\0\0\0\0GPS-secret".getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream withExif = new ByteArrayOutputStream();
        withExif.write(plain, 0, 2);
        withExif.write(new byte[]{(byte) 0xff, (byte) 0xe1, 0, (byte) (tiff.length + 2)});
        withExif.write(tiff);
        withExif.write(plain, 2, plain.length - 2);
        assertTrue(jpegMarkers(withExif.toByteArray()).contains(0xe1));

        byte[] out = MailImageReencoder.reencode(withExif.toByteArray(), FileTypeDetector.JPEG);

        assertFalse(jpegMarkers(out).contains(0xe1), jpegMarkers(out).toString());
        assertFalse(new String(out, StandardCharsets.ISO_8859_1).contains("GPS-secret"));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(out));
        assertEquals(4, decoded.getWidth());
        assertEquals(3, decoded.getHeight());
    }

    @Test
    void pngLosesDataAfterIend() throws Exception {
        byte[] png = encode(5, 2, BufferedImage.TYPE_INT_ARGB, "png");
        byte[] appended = Arrays.copyOf(png, png.length + 12);
        System.arraycopy("<?php evil?>".getBytes(StandardCharsets.US_ASCII), 0, appended, png.length, 12);

        byte[] out = MailImageReencoder.reencode(appended, FileTypeDetector.PNG);

        assertFalse(new String(out, StandardCharsets.ISO_8859_1).contains("evil"));
        assertEquals("IEND", new String(out, out.length - 8, 4, StandardCharsets.US_ASCII));
        assertEquals(5, ImageIO.read(new ByteArrayInputStream(out)).getWidth());
    }

    @Test
    void normalImagesRoundTrip() throws Exception {
        byte[] png = encode(3, 3, BufferedImage.TYPE_INT_ARGB, "png");
        BufferedImage pngOut = ImageIO.read(new ByteArrayInputStream(MailImageReencoder.reencode(png, FileTypeDetector.PNG)));
        assertEquals(0xff336699, pngOut.getRGB(0, 0));
        // ImageIO's own PNG output re-encodes to the same bytes
        assertArrayEquals(png, MailImageReencoder.reencode(png, FileTypeDetector.PNG));

        byte[] jpeg = encode(8, 8, BufferedImage.TYPE_INT_RGB, "jpeg");
        BufferedImage jpegOut = ImageIO.read(new ByteArrayInputStream(MailImageReencoder.reencode(jpeg, FileTypeDetector.JPEG)));
        assertEquals(8, jpegOut.getWidth());
    }

    @Test
    void tooLargeDimensionsAre400() throws Exception {
        for (int[] size : new int[][]{{1201, 1}, {1, 4001}, {1200, 4001}}) {
            byte[] png = encode(size[0], size[1], BufferedImage.TYPE_BYTE_GRAY, "png");
            var e = assertThrows(MailTemplateService.ValidationException.class,
                    () -> MailImageReencoder.reencode(png, FileTypeDetector.PNG));
            assertEquals("file", e.getErrors().get(0).field());
            assertTrue(e.getMessage().contains("zu groß"), e.getMessage());
        }
        // at the limit (1200 × 4000 = 4.8 million pixels): accepted
        byte[] limit = encode(1200, 4000, BufferedImage.TYPE_BYTE_GRAY, "png");
        assertTrue(MailImageReencoder.reencode(limit, FileTypeDetector.PNG).length > 0);
    }

    @Test
    void corruptFilesAre400() throws Exception {
        byte[] png = encode(3, 3, BufferedImage.TYPE_INT_RGB, "png");
        byte[] jpeg = encode(3, 3, BufferedImage.TYPE_INT_RGB, "jpeg");
        // (ImageIO decodes a file cut inside its pixel data leniently; the stored result is still a clean image)
        for (byte[] broken : List.of(Arrays.copyOf(png, 20),
                Arrays.copyOf(jpeg, 30), new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3})) {
            String type = broken[0] == (byte) 0x89 ? FileTypeDetector.PNG : FileTypeDetector.JPEG;
            var e = assertThrows(MailTemplateService.ValidationException.class,
                    () -> MailImageReencoder.reencode(broken, type), type + " " + broken.length + " bytes");
            assertEquals("Das Bild kann nicht gelesen werden.", e.getErrors().get(0).message());
        }
    }
}
