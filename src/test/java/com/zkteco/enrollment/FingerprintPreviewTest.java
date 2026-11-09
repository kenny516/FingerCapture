package com.zkteco.enrollment;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class FingerprintPreviewTest {
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
