package com.zkteco.enrollment;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.util.Arrays;

/** Owns the displayed SDK image. All UI methods run on the EDT. */
final class FingerprintPreview extends JPanel {
    private BufferedImage image;
    private final JLabel placeholder = new JLabel(
            "Aperçu",
            SwingConstants.CENTER);

    FingerprintPreview() {
        super(new BorderLayout());
        setBackground(new Color(248, 250, 249));
        setBorder(BorderFactory.createLineBorder(new Color(223, 230, 228)));
        setPreferredSize(new Dimension(280, 170));
        setMinimumSize(new Dimension(100, 100));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        placeholder.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        placeholder.setForeground(new Color(100, 116, 112));
        add(placeholder);
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
        placeholder.setVisible(next == null);
        getAccessibleContext().setAccessibleDescription(next == null
                ? "Aucune empreinte affichée." : "Dernière capture du lecteur, affichée temporairement.");
        repaint();
    }

    void clear() { showImage(null); }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (image == null) return;
        int availableWidth = Math.max(1, getWidth() - 24);
        int availableHeight = Math.max(1, getHeight() - 24);
        double scale = Math.min((double) availableWidth / image.getWidth(),
                (double) availableHeight / image.getHeight());
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, (getWidth() - width) / 2, (getHeight() - height) / 2, width, height, null);
        } finally { g.dispose(); }
    }
}
