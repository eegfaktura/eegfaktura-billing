package org.vfeeg.eegfaktura.billing.service;

import org.vfeeg.eegfaktura.billing.service.MailTemplateService.ValidationException;
import org.vfeeg.eegfaktura.billing.util.FileTypeDetector;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Re-encodes an uploaded mail image (PNG or JPEG, type already checked by content): the
 * dimensions are read first without decoding the pixels (decompression-bomb guard), then the image
 * is decoded and written again in the same format. Only the pixels survive — metadata (EXIF, GPS),
 * comments and data appended after the image are dropped. JDK ImageIO only, in memory (no cache
 * files).
 */
final class MailImageReencoder {

    static final int MAX_WIDTH = 1200;
    static final int MAX_HEIGHT = 4000;
    static final long MAX_PIXELS = 4_800_000L;
    static final float JPEG_QUALITY = 0.8f;

    private static final String NOT_READABLE = "Das Bild kann nicht gelesen werden.";

    private MailImageReencoder() {
    }

    /** The re-encoded bytes of {@code data}; {@code mimeType} is PNG or JPEG. 400 if too large or unreadable. */
    static byte[] reencode(byte[] data, String mimeType) {
        boolean jpeg = FileTypeDetector.JPEG.equals(mimeType);
        String format = jpeg ? "jpeg" : "png";
        BufferedImage decoded = read(data, format);
        BufferedImage image = jpeg ? toRgb(decoded) : decoded;
        return write(image, format, jpeg);
    }

    private static BufferedImage read(byte[] data, String format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
        if (!readers.hasNext()) {
            throw new IllegalStateException("No ImageIO reader for " + format);
        }
        ImageReader reader = readers.next();
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(in, true, true); // seek forward only, ignore metadata
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width <= 0 || height <= 0) {
                throw new ValidationException("file", NOT_READABLE);
            }
            if (width > MAX_WIDTH || height > MAX_HEIGHT || (long) width * height > MAX_PIXELS) {
                throw new ValidationException("file", "Das Bild ist zu groß (" + width + " × " + height
                        + " Pixel); erlaubt sind höchstens " + MAX_WIDTH + " Pixel Breite und " + MAX_HEIGHT
                        + " Pixel Höhe.");
            }
            return reader.read(0);
        } catch (IOException | RuntimeException e) {
            if (e instanceof ValidationException validation) {
                throw validation;
            }
            // a corrupt or unsupported file (e.g. a CMYK JPEG) is the client's problem, not a 500
            throw new ValidationException("file", NOT_READABLE);
        } finally {
            reader.dispose();
        }
    }

    /** JPEG has no alpha: other image types are drawn onto an RGB image. */
    private static BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB || image.getType() == BufferedImage.TYPE_3BYTE_BGR
                || image.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static byte[] write(BufferedImage image, String format, boolean jpeg) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (jpeg) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new ValidationException("file", NOT_READABLE);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
