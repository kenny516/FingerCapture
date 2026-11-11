package com.zkteco.enrollment;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class FingerprintPreviewTest {
    @Test void renderedCaptureKeepsItsProportionsAndGrayValuesAtDifferentWindowSizes() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            FingerprintPreview preview = new FingerprintPreview();
            byte[] pixels = new byte[80 * 160];
            Arrays.fill(pixels, (byte) 128);
            BufferedImage source = FingerprintPreview.image(pixels, 80, 160);
            preview.showImage(source);
            try {
                for (int[] size : new int[][]{{340, 300}, {304, 96}, {600, 500}}) {
                    preview.setSize(size[0], size[1]);
                    preview.doLayout();
                    BufferedImage rendered = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = rendered.createGraphics();
                    try { preview.printAll(graphics); }
                    finally { graphics.dispose(); }
                    Rectangle capture = null;
                    int grayPixels = 0;
                    for (int y = 0; y < rendered.getHeight(); y++) {
                        for (int x = 0; x < rendered.getWidth(); x++) {
                            if ((rendered.getRGB(x, y) & 0xFFFFFF) == 0x808080) {
                                if (capture == null) capture = new Rectangle(x, y, 1, 1);
                                else capture.add(new Rectangle(x, y, 1, 1));
                                grayPixels++;
                            }
                        }
                    }
                    assertNotNull(capture, "The SDK image must remain visible");
                    assertEquals(0.5, (double) capture.width / capture.height, 0.04,
                            "Resizing must not stretch the fingerprint");
                    assertEquals(capture.width * capture.height, grayPixels,
                            "No frame, text or effect may cover the capture");
                    assertEquals(size[0] / 2.0, capture.getCenterX(), 1.0);
                    assertTrue(capture.getMaxY() < size[1] - 24,
                            "Caption must stay below the capture");
                    assertEquals(128, source.getRaster().getSample(40, 80, 0));
                }
            } finally { preview.clear(); }
        });
    }

    @Test void displayedGrayMatchesTheSdkDemoPaletteWithoutGammaBrightening() {
        BufferedImage image = FingerprintPreview.image(new byte[]{(byte) 128}, 1, 1);
        assertEquals(0x808080, image.getRGB(0, 0) & 0xFFFFFF);
    }

    @Test void copiesSdkPixelsWithoutChangingTheirOrderOrUnsignedValues() {
        byte[] pixels = {0, 64, (byte) 128, (byte) 255, 32, 96};
        BufferedImage image = FingerprintPreview.image(pixels, 3, 2);
        pixels[0] = 99;
        assertEquals(0, image.getRaster().getSample(0, 0, 0));
        assertEquals(128, image.getRaster().getSample(2, 0, 0));
        assertEquals(255, image.getRaster().getSample(0, 1, 0));
        assertEquals(96, image.getRaster().getSample(2, 1, 0));
    }

    @Test void replacingAndClearingPreviewEraseOwnedPixels() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            FingerprintPreview preview = new FingerprintPreview();
            BufferedImage first = FingerprintPreview.image(new byte[]{42}, 1, 1);
            BufferedImage second = FingerprintPreview.image(new byte[]{99}, 1, 1);
            preview.showImage(first);
            preview.showImage(second);
            assertEquals(0, first.getRaster().getSample(0, 0, 0));
            assertEquals(99, second.getRaster().getSample(0, 0, 0));
            preview.clear();
            assertEquals(0, second.getRaster().getSample(0, 0, 0));
            assertTrue(preview.getAccessibleContext().getAccessibleDescription().contains("Aucune"));
        });
    }
}
