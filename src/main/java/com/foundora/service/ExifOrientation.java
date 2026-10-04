package com.foundora.service;

/**
 * Reads the EXIF orientation tag from JPEG bytes. Phones often save photos sideways and set this tag to say
 * how to rotate them; since we strip all metadata when re-encoding, we must apply the rotation ourselves.
 */
final class ExifOrientation {

    private static final int TAG_ORIENTATION = 0x0112;

    private ExifOrientation() {
    }

    /** The orientation (1-8), or 1 ("upright") if the bytes have no readable tag. */
    static int read(byte[] jpeg) {
        try {
            return find(jpeg);
        } catch (RuntimeException e) {
            return 1; // malformed metadata: treat the photo as upright
        }
    }

    private static int find(byte[] b) {
        if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int pos = 2;
        while (pos + 4 <= b.length && (b[pos] & 0xFF) == 0xFF) {
            int marker = b[pos + 1] & 0xFF;
            if (marker == 0xDA || marker == 0xD9) {
                return 1; // start of image data / end of image: no EXIF segment before it
            }
            int length = u16(b, pos + 2, false);
            int body = pos + 4;
            if (marker == 0xE1 && length >= 8 && body + 6 <= b.length
                    && b[body] == 'E' && b[body + 1] == 'x' && b[body + 2] == 'i' && b[body + 3] == 'f'
                    && b[body + 4] == 0 && b[body + 5] == 0) {
                return fromTiff(b, body + 6, Math.min(b.length, pos + 2 + length));
            }
            pos += 2 + length;
        }
        return 1;
    }

    /** Parses the TIFF header and first directory (IFD0) of an EXIF block spanning tiff..end. */
    private static int fromTiff(byte[] b, int tiff, int end) {
        boolean little = b[tiff] == 'I' && b[tiff + 1] == 'I';
        if (!little && !(b[tiff] == 'M' && b[tiff + 1] == 'M')) {
            return 1;
        }
        int ifd = tiff + (int) u32(b, tiff + 4, little);
        int entries = u16(b, ifd, little);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) {
                return 1;
            }
            if (u16(b, entry, little) == TAG_ORIENTATION) {
                int value = u16(b, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] b, int at, boolean little) {
        int x = b[at] & 0xFF;
        int y = b[at + 1] & 0xFF;
        return little ? x | (y << 8) : (x << 8) | y;
    }

    private static long u32(byte[] b, int at, boolean little) {
        long value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = little ? 8 * i : 8 * (3 - i);
            value |= (long) (b[at + i] & 0xFF) << shift;
        }
        return value;
    }
}
