import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 * Generates the image fixtures of corpus C-16 (images-relative/img/*) and C-17 (images-oversized.md).
 * Everything is computed from formulas, so the output depends only on the JDK's PNG and JPEG encoders.
 * Written for Bifolio from scratch (docs/TESTING.md section 3, SYN-08, PAG-07).
 *
 * Usage, from the repository root (JDK 11 or newer):
 *   java testdata/tools/GenerateImages.java testdata/corpus
 */
public final class GenerateImages {
    public static void main(String[] args) throws IOException {
        File corpus = new File(args.length > 0 ? args[0] : "testdata/corpus");
        File img = new File(corpus, "images-relative/img");
        if (!img.isDirectory() && !img.mkdirs()) throw new IOException("cannot create " + img);

        writePng(gradient(120, 40), new File(img, "wide.png"));
        writePng(gradient(30, 90), new File(img, "tall.png"));
        writePng(gradient(24, 24), new File(img, "with space.png"));
        writeJpeg(gradient(40, 30), new File(img, "plain.jpg"), false);
        // Stored 48 x 16 with EXIF orientation 6: a viewer shows it rotated, 16 wide and 48 tall.
        writeJpeg(gradient(48, 16), new File(img, "rotated-exif.jpg"), true);

        writeOversized(new File(corpus, "images-oversized.md"));
    }

    /** Smooth colour field with a marker in the top-left corner, so a rotation is visible to the eye. */
    private static BufferedImage gradient(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 40 + 200 * x / Math.max(1, w - 1);
                int g = 40 + 200 * y / Math.max(1, h - 1);
                int b = 150;
                if (x < 4 && y < 4) { r = 255; g = 255; b = 255; }
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, out)) throw new IOException("no writer for " + format);
        return out.toByteArray();
    }

    private static void writePng(BufferedImage image, File file) throws IOException {
        Files.write(file.toPath(), encode(image, "png"));
    }

    private static void writeJpeg(BufferedImage image, File file, boolean orientation6) throws IOException {
        byte[] jpeg = encode(image, "jpg");
        if (orientation6) jpeg = withOrientation(jpeg, 6);
        Files.write(file.toPath(), jpeg);
    }

    /** Inserts an Exif APP1 segment with only the Orientation tag right after the SOI marker. */
    private static byte[] withOrientation(byte[] jpeg, int orientation) {
        byte[] tiff = {
            'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, // big endian, first IFD at offset 8
            0x00, 0x01, // one entry
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, (byte) orientation, 0x00, 0x00, // Orientation, SHORT
            0x00, 0x00, 0x00, 0x00, // no next IFD
        };
        byte[] header = {'E', 'x', 'i', 'f', 0, 0};
        int length = 2 + header.length + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8);
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.write(header, 0, header.length);
        out.write(tiff, 0, tiff.length);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static String dataUri(int w, int h, boolean solid) throws IOException {
        BufferedImage image = solid ? new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY) : gradient(w, h);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(encode(image, "png"));
    }

    private static final String PROSE =
        "Text that fills the page so that a picture which does not fit the rest of it has to move to the next page, "
            + "leaving the space below this paragraph empty, as the pagination rule for atomic blocks says.";

    private static void writeOversized(File file) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# Oversized images\n\n");
        md.append("Every picture below is larger than any page. Each one is scaled to the column and to 85 percent of ");
        md.append("the page height, and moves whole to the next page when it does not fit.\n\n");
        md.append(PROSE).append("\n\n");
        md.append("![Very wide banner, 4000 by 300](").append(dataUri(4000, 300, true)).append(")\n\n");
        md.append(PROSE).append(" ").append(PROSE).append("\n\n");
        md.append("![Very tall strip, 300 by 6000](").append(dataUri(300, 6000, true)).append(")\n\n");
        md.append(PROSE).append("\n\n");
        md.append("![Huge square, 5000 by 5000](").append(dataUri(5000, 5000, true)).append(")\n\n");
        md.append("![Small icon, 32 by 32](").append(dataUri(32, 32, false)).append(")\n\n");
        md.append("The last paragraph comes after a small picture that is never enlarged.\n");
        Files.write(file.toPath(), md.toString().getBytes(StandardCharsets.UTF_8));
    }
}
