package com.zkteco.enrollment;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.geom.Path2D;
import java.util.Arrays;

/** Owns the displayed SDK image. All UI methods run on the EDT. */
final class FingerprintPreview extends JPanel {
    private BufferedImage image;
    private static final Color SURFACE = new Color(21, 38, 57);
    private static final Color ACCENT = new Color(91, 208, 193);
    private final JLabel caption = new JLabel("En attente d’une capture", SwingConstants.CENTER);

    FingerprintPreview() {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setPreferredSize(new Dimension(340, 300));
        setMinimumSize(new Dimension(100, 100));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        caption.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        caption.setForeground(new Color(190, 207, 223));
        add(caption, BorderLayout.SOUTH);
        getAccessibleContext().setAccessibleName("Aperçu de l'empreinte scannée");
        clear();
    }

    /** The SDK supplies top-to-bottom, 8-bit grayscale pixels; copy before its buffer is erased. */
    static BufferedImage image(byte[] pixels, int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height != pixels.length)
            throw new IllegalArgumentException("Dimensions d'image invalides.");
        // Match the demo's grayscale BMP palette. TYPE_BYTE_GRAY applies an sRGB
        // conversion that brightens midtones (SDK value 128 becomes RGB 188).
        byte[] levels = new byte[256];
        for (int i = 0; i < levels.length; i++) levels[i] = (byte) i;
        IndexColorModel palette = new IndexColorModel(8, 256, levels, levels, levels);
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, palette);
        byte[] target = ((DataBufferByte) result.getRaster().getDataBuffer()).getData();
        System.arraycopy(pixels, 0, target, 0, pixels.length);
        return result;
    }

    static void erase(BufferedImage image) {
        if (image != null) {
            Arrays.fill(((DataBufferByte) image.getRaster().getDataBuffer()).getData(), (byte) 0);
            image.flush();
        }
    }

    void showImage(BufferedImage next) {
        if (image != next) erase(image);
        image = next;
        caption.setText(next == null ? "En attente d’une capture" : "Dernière capture · aperçu temporaire");
        getAccessibleContext().setAccessibleDescription(next == null
                ? "Aucune empreinte affichée." : "Dernière capture du lecteur, affichée temporairement.");
        repaint();
    }

    void clear() { showImage(null); }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(SURFACE);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
            int availableWidth = Math.max(1, getWidth() - 64);
            int availableHeight = Math.max(1, getHeight() - 66);
            int areaCenterY = Math.max(1, (getHeight() - 24) / 2);
            int width = availableWidth;
            int height = availableHeight;
            if (image != null) {
                double scale = Math.min((double) availableWidth / image.getWidth(),
                        (double) availableHeight / image.getHeight());
                width = Math.max(1, (int) Math.round(image.getWidth() * scale));
                height = Math.max(1, (int) Math.round(image.getHeight() * scale));
            }
            int x = (getWidth() - width) / 2;
            int y = areaCenterY - height / 2;
            // Frame marks stay outside the SDK image, leaving every ridge visible.
            g.setColor(ACCENT);
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int mark = Math.min(16, Math.min(width, height) / 4);
            for (int sideX : new int[]{-1, 1}) {
                for (int sideY : new int[]{-1, 1}) {
                    int cx = sideX < 0 ? x - 7 : x + width + 7;
                    int cy = sideY < 0 ? y - 7 : y + height + 7;
                    g.drawLine(cx, cy, cx - sideX * mark, cy);
                    g.drawLine(cx, cy, cx, cy - sideY * mark);
                }
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (image != null) {
                g.drawImage(image, x, y, width, height, null);
            } else {
                // A symbolic fingerprint, never presented as a captured sample.
                double size = Math.min(94, Math.min(availableWidth, availableHeight));
                g.translate(getWidth() / 2.0 - size / 2, areaCenterY - size / 2);
                g.scale(size / 100, size / 100);
                g.setColor(new Color(109, 148, 169));
                g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int inset = 0; inset < 5; inset++) {
                    double left = 14 + inset * 8;
                    double right = 86 - inset * 8;
                    double top = 8 + inset * 9;
                    Path2D ridge = new Path2D.Double();
                    ridge.moveTo(left, 78 - inset * 3);
                    ridge.curveTo(left + 8, 60, left - 12, top, 50, top);
                    ridge.curveTo(right + 12, top, right, 56, right - 4, 72);
                    ridge.curveTo(right - 6, 84, right - 8, 88, right - 12, 94);
                    g.draw(ridge);
                }
            }
        } finally { g.dispose(); }
    }
}
