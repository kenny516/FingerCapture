package com.zkteco.enrollment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZkReaderTest {
    @Test void templateContainsOnlyReturnedBytesAndOwnsItsBuffer() throws Exception {
        byte[] buffer = {1, 2, 3, 99, 99};
        byte[] template = ZkReader.exactTemplate(buffer, 3);
        buffer[0] = 0;
        assertArrayEquals(new byte[]{1, 2, 3}, template);
    }
    @Test void invalidSdkLengthsAreRejected() {
        for (int length : new int[]{-1, 0, 4})
            assertThrows(Exception.class, () -> ZkReader.exactTemplate(new byte[3], length));
    }
}
