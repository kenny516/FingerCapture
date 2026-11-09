package com.zkteco.enrollment;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

/** Compact Swing window. All updates run on the EDT and preserve foreground focus. */
final class ProgressWindow extends JFrame {
    private static final Color BG = new Color(246, 248, 247);
    private static final Color PRIMARY = new Color(20, 94, 90);
    private static final Color TEXT = new Color(28, 40, 38);
    private static final Color MUTED = new Color(80, 98, 94);
    private final JProgressBar progress = new JProgressBar(0, 3);
    private final JLabel countLabel = new JLabel();
    private final FingerprintPreview preview = new FingerprintPreview();
    private final JTextArea message = new JTextArea(2, 26);
    private final JButton listen = new ActionButton("Commencer", true);

    ProgressWindow(Runnable onListen, Runnable onClose) {
        super("ZKFinger — Empreinte");
        setName("Progression de l'enrôlement");
        setAutoRequestFocus(false);
        setAlwaysOnTop(true);
        setResizable(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { onClose.run(); }
        });
        getAccessibleContext().setAccessibleName("Enregistrement d'une empreinte");

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(BG);
        content.setBorder(new EmptyBorder(14, 14, 14, 14));
        content.setPreferredSize(new Dimension(360, 400));

        JPanel instructions = panel(new BorderLayout(0, 8));
        JLabel title = new JLabel("Empreinte");
        title.setFont(font(Font.BOLD, 18));
        title.setForeground(TEXT);
        instructions.add(title, BorderLayout.NORTH);
        message.setEditable(false);
        message.setFocusable(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setFont(font(Font.PLAIN, 14));
        message.setForeground(TEXT);
        message.setBackground(BG);
        message.setBorder(new EmptyBorder(0, 0, 0, 8));
        message.getAccessibleContext().setAccessibleName("Consigne et résultat de l'enrôlement");
        JScrollPane messageScroll = new JScrollPane(message,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        messageScroll.setBorder(null);
        messageScroll.setPreferredSize(new Dimension(280, 44));
        messageScroll.setMinimumSize(new Dimension(100, 44));
        instructions.add(messageScroll, BorderLayout.CENTER);
        content.add(instructions, BorderLayout.NORTH);

        preview.setFont(font(Font.PLAIN, 13));
        content.add(preview, BorderLayout.CENTER);

        JPanel footer = panel(new BorderLayout(0, 10));
        progress.setStringPainted(false);
        progress.setBorderPainted(false);
        progress.setBackground(new Color(223, 230, 228));
        progress.setForeground(PRIMARY);
        progress.setFont(font(Font.BOLD, 12));
        progress.setFocusable(false);
        progress.setPreferredSize(new Dimension(280, 7));
        progress.getAccessibleContext().setAccessibleName("Nombre de lectures acceptées");
        countLabel.setFont(font(Font.BOLD, 12));
        countLabel.setForeground(MUTED);
        accepted(0);
        JPanel progressGroup = panel(new BorderLayout(0, 3));
        progressGroup.add(countLabel, BorderLayout.NORTH);
        progressGroup.add(progress, BorderLayout.SOUTH);
        footer.add(progressGroup, BorderLayout.NORTH);

        JPanel actions = panel(new BorderLayout(12, 0));
        listen.setActionCommand("listen");
        listen.addActionListener(event -> onListen.run());
        actions.add(listen, BorderLayout.CENTER);
        JButton quit = new ActionButton("Quitter", false);
        quit.setActionCommand("quit");
        quit.addActionListener(event -> onClose.run());
        actions.add(quit, BorderLayout.EAST);
        footer.add(actions, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);

        setContentPane(content);
        showStatus("Commencez, puis cliquez dans le champ cible.");
        pack();
        positionOnScreen();
    }

    void listeningAction(String label, boolean enabled) {
        listen.setText(label);
        listen.setEnabled(enabled);
    }

    void showFingerprint(BufferedImage image) { preview.showImage(image); }
    void clearFingerprint() { preview.clear(); }

    @Override public void dispose() {
        clearFingerprint();
        super.dispose();
    }

    void accepted(int count) {
        int value = Math.max(0, Math.min(3, count));
        progress.setValue(value);
        progress.setString(value + " / 3 lectures");
        countLabel.setText(progress.getString());
    }

    void showStatus(String text) {
        message.setText(text);
        message.setCaretPosition(0);
    }

    private static JPanel panel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static Font font(int style, int size) {
        return UIManager.getFont("Label.font").deriveFont(style, (float) size);
    }

    /** Flat buttons retain Swing's keyboard, accessible name and action handling. */
    private static final class ActionButton extends JButton {
        private final boolean primary;

        ActionButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setFont(font(Font.BOLD, 14));
            setForeground(primary ? Color.WHITE : TEXT);
            setBorder(new EmptyBorder(12, 20, 12, 20));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean pressed = getModel().isPressed() && getModel().isArmed();
                boolean hover = getModel().isRollover();
                Color fill;
                if (!isEnabled()) fill = primary ? new Color(198, 216, 213) : new Color(236, 240, 239);
                else if (primary) fill = pressed ? new Color(12, 65, 62)
                        : hover ? new Color(16, 78, 75) : PRIMARY;
                else fill = pressed ? new Color(220, 229, 226)
                        : hover ? new Color(232, 239, 236) : Color.WHITE;
                g.setColor(fill);
                g.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 14, 14);
                if (!primary) {
                    g.setColor(new Color(207, 218, 214));
                    g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 14, 14);
                }
                if (hasFocus()) {
                    g.setStroke(new BasicStroke(2));
                    g.setColor(primary ? Color.WHITE : PRIMARY);
                    g.drawRoundRect(4, 4, getWidth() - 9, getHeight() - 9, 9, 9);
                }
            } finally { g.dispose(); }
            super.paintComponent(graphics);
        }
    }

    private void positionOnScreen() {
        Rectangle bounds = getGraphicsConfiguration().getBounds();
        Insets reserved = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
        int left = bounds.x + reserved.left;
        int top = bounds.y + reserved.top;
        int width = Math.max(1, bounds.width - reserved.left - reserved.right - 32);
        int height = Math.max(1, bounds.height - reserved.top - reserved.bottom - 32);
        setMinimumSize(new Dimension(Math.min(340, width), Math.min(360, height)));
        setSize(Math.min(getWidth(), width), Math.min(getHeight(), height));
        setLocation(left + width - getWidth() + 16, top + 16);
    }
}
