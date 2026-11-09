package com.zkteco.enrollment;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JOptionPane;
import javax.swing.Timer;

public final class EnrollmentApp {
    private final AtomicBoolean closing = new AtomicBoolean();
    private EnrollmentController controller;
    private WindowsEvents events;
    private ProgressWindow progressWindow;
    private Timer preparationTimer;
    private ListenPreparation preparation;
    private enum Phase { IDLE, PREPARING, CAPTURING, CANCELLING, CLOSED }
    private Phase phase = Phase.IDLE;
    private long preparationId;

    public static void main(String[] args) {
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            System.err.println("Cet utilitaire nécessite Windows.");
            return;
        }
        if (args.length == 1 && "--check-sdk".equals(args[0])) {
            try (ZkReader reader = new ZkReader()) {
                reader.open();
                System.out.println("SDK chargé, lecteur ouvert et dimensions lues. Aucune empreinte capturée.");
            } catch (Exception | LinkageError e) {
                System.err.println("Vérification SDK en échec : " + e.getMessage());
                System.exit(1);
            }
            return;
        }
        EventQueue.invokeLater(() -> new EnrollmentApp().start());
    }

    private void start() {
        try {
            progressWindow = new ProgressWindow(this::listenButton, this::close);
            progressWindow.setIconImage(icon());
            controller = new EnrollmentController(Executors.newSingleThreadExecutor(r -> new Thread(r, "finger-reader")),
                    this::status, () -> EventQueue.invokeLater(this::listeningFinished));
            events = new WindowsEvents(this::status);
            events.start();
            preparation = new ListenPreparation(seconds -> status(seconds
                    + " s · Sélectionnez le champ cible."), this::beginCapture);
            preparationTimer = new Timer(1000, event -> preparation.tick());
            progressWindow.setVisible(true);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (controller != null) controller.close();
                if (events != null) events.close();
            }, "enrollment-shutdown"));
            status("Commencez, puis cliquez dans le champ cible.");
        } catch (Exception | LinkageError e) {
            close();
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            JOptionPane.showMessageDialog(null, cause.getMessage(), "Démarrage impossible", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void listenButton() {
        if (phase == Phase.CAPTURING) {
            phase = Phase.CANCELLING;
            progressWindow.clearFingerprint();
            progressWindow.listeningAction("Annulation…", false);
            controller.cancel();
            status("Annulation en cours…");
        } else if (phase == Phase.PREPARING) {
            preparationId++;
            preparationTimer.stop();
            preparation.cancel();
            listeningFinished();
            status("Capture annulée.");
        } else if (phase == Phase.IDLE) {
            preparationId++;
            phase = Phase.PREPARING;
            progressWindow.clearFingerprint();
            progressWindow.accepted(0);
            progressWindow.listeningAction("Annuler", true);
            preparation.start();
            preparationTimer.start();
        }
    }

    private void beginCapture() {
        long attempt = preparationId;
        preparationTimer.stop();
        progressWindow.listeningAction("Préparation…", false);
        events.captureForeground().whenComplete((target, error) -> EventQueue.invokeLater(() -> {
            if (closing.get() || phase != Phase.PREPARING || attempt != preparationId) return;
            if (error != null) {
                status(error.getMessage());
                listeningFinished();
                return;
            }
            if (target.belongsToThisProcess() || !target.active()) {
                status("Recommencez, puis sélectionnez le champ cible.");
                listeningFinished();
                return;
            }
            phase = Phase.CAPTURING;
            progressWindow.listeningAction("Annuler", true);
            controller.toggle(cancelled -> {
                WindowsDesktop desktop = new WindowsDesktop(target);
                EnrollmentSession.Clock clock = new EnrollmentSession.Clock() {
                    public long nanoTime() { return System.nanoTime(); }
                    public void pause() throws InterruptedException { Thread.sleep(100); }
                };
                new EnrollmentSession(new ZkReader(this::status, image -> EventQueue.invokeLater(() -> {
                    if (closing.get() || phase != Phase.CAPTURING || attempt != preparationId) {
                        FingerprintPreview.erase(image);
                        return;
                    }
                    progressWindow.showFingerprint(image);
                })), desktop, clock, this::status,
                        count -> EventQueue.invokeLater(() -> {
                            if (!closing.get() && phase == Phase.CAPTURING && attempt == preparationId)
                                progressWindow.accepted(count);
                        })).run(cancelled::get);
            });
        }));
    }

    private void listeningFinished() {
        if (closing.get()) return;
        phase = Phase.IDLE;
        progressWindow.clearFingerprint();
        progressWindow.listeningAction("Commencer", true);
    }

    private void status(String message) {
        // Operational states only: never print a fingerprint image, sample or Base64 template.
        System.out.println("[enrolement] " + message);
        EventQueue.invokeLater(() -> {
            if (closing.get() || progressWindow == null) return;
            progressWindow.showStatus(message);
        });
    }

    private void close() {
        if (!closing.compareAndSet(false, true)) return;
        phase = Phase.CLOSED;
        if (preparationTimer != null) preparationTimer.stop();
        if (preparation != null) preparation.cancel();
        if (controller != null) controller.close();
        if (events != null) events.close();
        if (progressWindow != null) progressWindow.dispose();
    }

    private static Image icon() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(20, 94, 90)); g.fillRoundRect(0, 0, 32, 32, 8, 8);
            g.setColor(Color.WHITE); g.setStroke(new BasicStroke(1.7f));
            for (int inset : new int[]{6, 10, 14}) g.drawArc(inset, inset, 32 - inset * 2, 36 - inset * 2, -25, 260);
        } finally { g.dispose(); }
        return image;
    }
}
