package com.budgetguardian.app;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

/**
 * Operating-system notification for the daily 8 PM reminder, via the desktop
 * tray icon (Windows Action Center toast, macOS Notification Center, Linux
 * notification daemons that back {@link SystemTray}).
 *
 * <p>Best-effort: on a platform or session without tray support (headless,
 * some minimal Linux setups) {@link #notify(String, String)} is a no-op
 * rather than a startup failure.</p>
 *
 * <p><b>Must be {@link #close() closed} on app exit.</b> AWT's tray icon
 * runs on its own non-daemon thread; leaving it registered after the JavaFX
 * window closes keeps that thread alive and the JVM process running in the
 * background indefinitely — the same class of orphaned-process problem as
 * an unstopped child process.</p>
 */
final class OsNotifier implements AutoCloseable {

    private TrayIcon trayIcon;

    OsNotifier() {
        if (!SystemTray.isSupported()) {
            return;
        }
        try {
            trayIcon = new TrayIcon(icon(), "Budget Guardian");
            trayIcon.setImageAutoSize(true);
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            System.err.println("Could not create system tray icon: " + e.getMessage());
            trayIcon = null;
        }
    }

    /** Shows an OS-level toast/balloon notification. No-op if tray isn't available. */
    void notify(String title, String message) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }

    /** Removes the tray icon so its background thread stops holding the JVM open. */
    @Override
    public void close() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }

    private static Image icon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(56, 142, 60));
        g.fillOval(0, 0, 16, 16);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 10f));
        g.drawString("B", 4, 12);
        g.dispose();
        return image;
    }
}
