package com.budgetguardian.app;

/**
 * Thin launcher that does <em>not</em> extend {@code javafx.application.Application}.
 *
 * <p><b>Why it exists:</b> when the app is packaged as a single "fat" jar, the
 * JavaFX modules sit on the classpath rather than the module path. Launching a
 * class that directly extends {@code Application} in that setup triggers the
 * "JavaFX runtime components are missing" error. Delegating through a
 * non-{@code Application} main class sidesteps that check, so this is the
 * manifest {@code Main-Class} for the packaged build.</p>
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}
