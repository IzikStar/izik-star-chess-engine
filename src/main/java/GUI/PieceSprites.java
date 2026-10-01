package GUI;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The piece images, decoded and scaled once (Phase 3). The old {@code pieces.Piece} re-read and
 * re-scaled {@code pieces.png} in an instance initialiser for every piece object it created.
 * Sheet layout: columns K Q B N R P, row 0 White, row 1 Black.
 */
public final class PieceSprites {

    private static final String KINDS = "kqbnrp";

    private final Map<Character, Image> images = new HashMap<>();

    public PieceSprites(int size) {
        BufferedImage sheet;
        try (InputStream in = PieceSprites.class.getResourceAsStream("/pieces.png")) {
            if (in == null) {
                throw new IllegalStateException("pieces.png missing from the classpath");
            }
            sheet = ImageIO.read(in);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read pieces.png", e);
        }
        int cell = sheet.getWidth() / 6;
        for (int i = 0; i < KINDS.length(); i++) {
            char kind = KINDS.charAt(i);
            images.put(Character.toUpperCase(kind), scaled(sheet, i, 0, cell, size));
            images.put(kind, scaled(sheet, i, 1, cell, size));
        }
    }

    private static Image scaled(BufferedImage sheet, int col, int row, int cell, int size) {
        return sheet.getSubimage(col * cell, row * cell, cell, cell)
                .getScaledInstance(size, size, Image.SCALE_SMOOTH);
    }

    /** The image for a FEN piece letter ({@code 'K'} = white king, {@code 'p'} = black pawn). */
    public Image get(char fenPiece) {
        return images.get(fenPiece);
    }
}
