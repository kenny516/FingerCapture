package com.zkteco.enrollment;

import com.zkteco.biometric.FingerprintSensorErrorCode;

/** Distinguishes a retryable acquisition from a new finger placement. */
final class CaptureCycle {
    private boolean waitingForRemoval;

    byte[] accept(int result, byte[] buffer, int length) throws Exception {
        if (result == FingerprintSensorErrorCode.ZKFP_ERR_CAPTURE) {
            waitingForRemoval = false;
            return null;
        }
        // A poor extraction or an in-progress capture is not a fatal session error,
        // and neither proves that the operator removed the finger.
        if (result == FingerprintSensorErrorCode.ZKFP_ERR_EXTRACT_FP
                || result == FingerprintSensorErrorCode.ZKFP_ERR_BUSY) return null;
        if (result != 0) throw new Exception("Capture impossible (code SDK " + result + ").");
        if (waitingForRemoval) return null;
        byte[] sample = ZkReader.exactTemplate(buffer, length);
        waitingForRemoval = true;
        return sample;
    }
}
