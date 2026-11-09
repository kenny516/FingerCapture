package com.zkteco.enrollment;

import com.zkteco.biometric.FingerprintSensorEx;
import com.zkteco.biometric.FingerprintSensorErrorCode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.function.Consumer;
import java.awt.image.BufferedImage;

/** All calls belong to the single enrollment worker, including close(). */
final class ZkReader implements EnrollmentSession.Reader {
    private boolean initialized;
    private long device, database;
    private byte[] image;
    private int imageWidth, imageHeight;
    private CaptureCycle captureCycle = new CaptureCycle();
    private final Consumer<String> status;
    private final Consumer<BufferedImage> preview;
    private int lastCaptureResult = Integer.MIN_VALUE;

    ZkReader() { this(message -> {}); }
    ZkReader(Consumer<String> status) { this(status, FingerprintPreview::erase); }
    ZkReader(Consumer<String> status, Consumer<BufferedImage> preview) {
        this.status = status;
        this.preview = preview;
    }

    public void open() throws Exception {
        int result = FingerprintSensorEx.Init();
        if (result != 0) throw error("Initialisation du SDK", result);
        initialized = true;
        int count = FingerprintSensorEx.GetDeviceCount();
        if (count <= 0) throw new Exception("Aucun lecteur disponible. Vérifiez le branchement USB et le pilote.");
        device = FingerprintSensorEx.OpenDevice(0);
        if (device == 0) throw new Exception("Impossible d'ouvrir le lecteur. Fermez les autres logiciels de capture.");
        database = FingerprintSensorEx.DBInit();
        if (database == 0) throw new Exception("Impossible d'initialiser le moteur biométrique.");
        int width = dimension(1), height = dimension(2);
        if (width <= 0 || height <= 0 || (long) width * height > 16_000_000)
            throw new Exception("Dimensions d'image invalides retournées par le lecteur.");
        image = new byte[width * height];
        imageWidth = width;
        imageHeight = height;
        captureCycle = new CaptureCycle();
        lastCaptureResult = Integer.MIN_VALUE;
    }

    private int dimension(int parameter) throws Exception {
        byte[] value = new byte[4];
        int[] length = {4};
        int result = FingerprintSensorEx.GetParameters(device, parameter, value, length);
        if (result != 0 || length[0] != 4) throw error("Lecture des dimensions", result);
        return ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    public byte[] capture() throws Exception {
        byte[] buffer = new byte[2048];
        int[] length = {buffer.length};
        try {
            int result = FingerprintSensorEx.AcquireFingerprint(device, image, buffer, length);
            if (result == FingerprintSensorErrorCode.ZKFP_ERR_CAPTURE) {
                if (FingerprintSensorEx.GetDeviceCount() <= 0)
                    throw new Exception("Lecteur débranché. Rebranchez-le puis recommencez.");
            }
            if (result != lastCaptureResult) {
                if (result == FingerprintSensorErrorCode.ZKFP_ERR_EXTRACT_FP)
                    status.accept("Lecture insuffisante : repositionnez le même doigt.");
                else if (result == FingerprintSensorErrorCode.ZKFP_ERR_BUSY)
                    status.accept("Lecture en cours : gardez le doigt posé.");
                lastCaptureResult = result;
            }
            byte[] sample = captureCycle.accept(result, buffer, length[0]);
            if (sample != null) preview.accept(FingerprintPreview.image(image, imageWidth, imageHeight));
            return sample;
        } finally {
            Arrays.fill(buffer, (byte) 0);
            if (image != null) Arrays.fill(image, (byte) 0);
        }
    }

    public boolean matches(byte[] first, byte[] next) throws Exception {
        int score = FingerprintSensorEx.DBMatch(database, first, next);
        if (score < 0) throw error("Comparaison des lectures", score);
        return score > 0;
    }

    public byte[] merge(byte[][] samples) throws Exception {
        byte[] buffer = new byte[2048];
        int[] length = {buffer.length};
        try {
            int result = FingerprintSensorEx.DBMerge(database, samples[0], samples[1], samples[2], buffer, length);
            if (result != 0) throw error("Fusion des trois lectures", result);
            return exactTemplate(buffer, length[0]);
        } finally { Arrays.fill(buffer, (byte) 0); }
    }

    static byte[] exactTemplate(byte[] buffer, int length) throws Exception {
        if (length <= 0 || length > buffer.length) throw new Exception("Longueur de gabarit SDK invalide.");
        return Arrays.copyOf(buffer, length);
    }

    public void close() {
        try {
            if (database != 0) FingerprintSensorEx.DBFree(database);
        } finally {
            database = 0;
            try {
                if (device != 0) FingerprintSensorEx.CloseDevice(device);
            } finally {
                device = 0;
                try {
                    if (initialized) FingerprintSensorEx.Terminate();
                } finally {
                    initialized = false;
                    if (image != null) Arrays.fill(image, (byte) 0);
                    image = null;
                }
            }
        }
    }

    private static Exception error(String operation, int code) {
        return new Exception(operation + " (code SDK " + code + ").");
    }
}
