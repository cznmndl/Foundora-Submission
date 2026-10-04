package com.foundora.service;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks an uploaded photo by actually decoding it, then re-encodes it as a JPEG of at most 1600 px.
 * Re-encoding also drops all metadata, including the GPS location phones store in photos.
 */
@Component
public class ImageProcessor {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    static final long MAX_PIXELS = 40_000_000L;
    static final int MAX_EDGE = 1600;
    static final float JPEG_QUALITY = 0.82f;

    /** Declared content type -> the decoder format we expect to find in the bytes. */
    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", "jpeg",
            "image/jpg", "jpeg",
            "image/png", "png",
            "image/webp", "webp");
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "png", "webp");

    /** Returns the photo as JPEG bytes ready to store. */
    public byte[] toJpeg(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidImageException("The photo must be 5 MB or smaller.");
        }
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED.containsKey(declared)) {
            throw new InvalidImageException("The photo must be a JPEG, PNG or WebP image.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidImageException("We couldn't read that photo. Please try again.", e);
        }
        return toJpeg(bytes);
    }

    byte[] toJpeg(byte[] bytes) {
        BufferedImage image = decode(bytes);
        int orientation = ExifOrientation.read(bytes);
        BufferedImage upright = applyOrientation(flatten(image), orientation);
        return encode(scaleDown(upright, MAX_EDGE));
    }

    private static BufferedImage decode(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new InvalidImageException("That file isn't a photo we can read. Use a JPEG, PNG or WebP image.");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!ALLOWED_FORMATS.contains(format)) {
                    throw new InvalidImageException("The photo must be a JPEG, PNG or WebP image.");
                }
                reader.setInput(in, true, true);
                // Check the size from the header before decoding, so a tiny file claiming to be a huge
                // image can't exhaust memory.
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new InvalidImageException("That photo's resolution is too large. Please use a smaller photo.");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidImageException invalid) {
                throw invalid;
            }
            throw new InvalidImageException("We couldn't read that photo. It may be damaged; try another one.", e);
        }
    }

    /** Copies onto a plain RGB canvas with a white background (removes transparency, normalises colour types). */
    private static BufferedImage flatten(BufferedImage src) {
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, src.getWidth(), src.getHeight());
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    /** Rotates/flips according to the EXIF orientation value (1 = already upright). */
    static BufferedImage applyOrientation(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5;
        // AffineTransform(m00, m10, m01, m11, m02, m12): maps source (x, y) into the upright image.
        AffineTransform t = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // mirror horizontally
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // rotate 180
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // mirror vertically
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // transpose
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // rotate 90 clockwise
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // transverse
            default -> new AffineTransform(0, -1, 1, 0, 0, w);  // 8: rotate 90 counter-clockwise
        };
        BufferedImage dst = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.drawImage(src, t, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    /** Shrinks so the longer edge is at most maxEdge, halving in steps first for a sharper result. */
    static BufferedImage scaleDown(BufferedImage src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        double factor = Math.min(1.0, (double) maxEdge / Math.max(w, h));
        if (factor >= 1.0) {
            return src;
        }
        int targetW = Math.max(1, (int) Math.round(w * factor));
        int targetH = Math.max(1, (int) Math.round(h * factor));
        BufferedImage current = src;
        while (current.getWidth() / 2 >= targetW && current.getHeight() / 2 >= targetH) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return resize(current, targetW, targetH);
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    /** Writes a JPEG with no metadata (no EXIF, no GPS). */
    private static byte[] encode(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode JPEG", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
