package com.foundora.reporting;

import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    @Test
    void shrinksLargePhotosToAtMost1600PixelsAsJpeg() throws Exception {
        byte[] out = processor.toJpeg(new MockMultipartFile("photo", "big.png", "image/png", encode(image(3000, 2000), "png")));

        BufferedImage result = read(out);
        assertThat(out).startsWith((byte) 0xFF, (byte) 0xD8);
        assertThat(result.getWidth()).isEqualTo(1600);
        assertThat(result.getHeight()).isEqualTo(1067);
    }

    @Test
    void neverEnlargesSmallPhotos() throws Exception {
        BufferedImage result = read(processor.toJpeg(encode(image(300, 200), "jpg")));
        assertThat(result.getWidth()).isEqualTo(300);
        assertThat(result.getHeight()).isEqualTo(200);
    }

    @Test
    void appliesTheExifRotationThenDropsAllMetadata() throws Exception {
        // 40x20, left half red: rotated 90° clockwise (orientation 6) the red half must end up on top.
        BufferedImage source = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 20, 20);
        g.setColor(Color.BLUE);
        g.fillRect(20, 0, 20, 20);
        g.dispose();
        byte[] withExif = withOrientation(encode(source, "jpg"), 6);
        assertThat(ExifOrientation.read(withExif)).isEqualTo(6);

        byte[] out = processor.toJpeg(withExif);

        BufferedImage result = read(out);
        assertThat(result.getWidth()).isEqualTo(20);
        assertThat(result.getHeight()).isEqualTo(40);
        Color top = new Color(result.getRGB(10, 8));
        Color bottom = new Color(result.getRGB(10, 32));
        assertThat(top.getRed()).isGreaterThan(150);
        assertThat(top.getBlue()).isLessThan(100);
        assertThat(bottom.getBlue()).isGreaterThan(150);
        assertThat(new String(out, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
        assertThat(ExifOrientation.read(out)).isEqualTo(1);
    }

    @Test
    void refusesFilesThatAreNotSupportedImages() throws Exception {
        assertThatThrownBy(() -> processor.toJpeg(new MockMultipartFile("photo", "a.png", "image/png", "hello".getBytes())))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("isn't a photo");
        // A GIF renamed to .png is caught by decoding, not by trusting the declared type.
        assertThatThrownBy(() -> processor.toJpeg(new MockMultipartFile("photo", "a.png", "image/png", encode(image(10, 10), "gif"))))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("JPEG, PNG or WebP");
        assertThatThrownBy(() -> processor.toJpeg(new MockMultipartFile("photo", "a.gif", "image/gif", encode(image(10, 10), "gif"))))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void refusesOversizedFilesAndHugeDimensionsBeforeDecoding() {
        byte[] tooBig = new byte[(int) ImageProcessor.MAX_BYTES + 1];
        assertThatThrownBy(() -> processor.toJpeg(new MockMultipartFile("photo", "a.jpg", "image/jpeg", tooBig)))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("5 MB");

        byte[] bomb = pngHeader(10_000, 10_000); // a few bytes claiming 100 megapixels
        assertThatThrownBy(() -> processor.toJpeg(new MockMultipartFile("photo", "a.png", "image/png", bomb)))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("too large");
    }

    @Test
    void canReadWebp() {
        assertThat(ImageIO.getImageReadersByFormatName("webp").hasNext()).isTrue();
    }

    private static BufferedImage image(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(30, 120, 60));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static byte[] encode(BufferedImage img, String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(img, format, out)).isTrue();
        return out.toByteArray();
    }

    private static BufferedImage read(byte[] bytes) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    /** Inserts an EXIF APP1 segment holding only an orientation tag right after the JPEG's start marker. */
    private static byte[] withOrientation(byte[] jpeg, int orientation) {
        byte[] tiff = {
                'M', 'M', 0, 42, 0, 0, 0, 8,              // big-endian TIFF header, first directory at offset 8
                0, 1,                                     // one entry
                0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0, // orientation, SHORT, count 1
                0, 0, 0, 0};                              // no next directory
        byte[] exif = new byte[6 + tiff.length];
        System.arraycopy(new byte[]{'E', 'x', 'i', 'f', 0, 0}, 0, exif, 0, 6);
        System.arraycopy(tiff, 0, exif, 6, tiff.length);
        int length = exif.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.write(exif, 0, exif.length);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static byte[] pngHeader(int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        byte[] ihdr = {'I', 'H', 'D', 'R',
                (byte) (width >> 24), (byte) (width >> 16), (byte) (width >> 8), (byte) width,
                (byte) (height >> 24), (byte) (height >> 16), (byte) (height >> 8), (byte) height,
                8, 2, 0, 0, 0};
        out.writeBytes(new byte[]{0, 0, 0, 13});
        out.writeBytes(ihdr);
        CRC32 crc = new CRC32();
        crc.update(ihdr);
        long c = crc.getValue();
        out.writeBytes(new byte[]{(byte) (c >> 24), (byte) (c >> 16), (byte) (c >> 8), (byte) c});
        out.writeBytes(new byte[]{0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82});
        return out.toByteArray();
    }
}
