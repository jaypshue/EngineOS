package com.engineos.ui;

import java.util.prefs.Preferences;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

final class OnboardingPreferences {
    private static final String PREPARED_KEY = "engineering-partner-prepared";
    private static final String MODEL_KEY = "execution-model";
    private static final String WORKSPACE_KEY = "execution-workspace";
    private static final String WINDOW_X_KEY = "window-x";
    private static final String WINDOW_Y_KEY = "window-y";
    private static final String WINDOW_WIDTH_KEY = "window-width";
    private static final String WINDOW_HEIGHT_KEY = "window-height";
    private final Preferences preferences;

    OnboardingPreferences() {
        this(Preferences.userNodeForPackage(OnboardingPreferences.class));
    }

    OnboardingPreferences(Preferences preferences) {
        this.preferences = preferences;
    }

    boolean isPrepared() {
        return preferences.getBoolean(PREPARED_KEY, false);
    }

    void markPrepared() {
        preferences.putBoolean(PREPARED_KEY, true);
    }

    String restoreModel(List<String> availableModels, String defaultModel) {
        String saved = preferences.get(MODEL_KEY, null);
        return saved != null && availableModels.contains(saved) ? saved : defaultModel;
    }

    String restoreWorkspace(String defaultWorkspace) {
        String saved = preferences.get(WORKSPACE_KEY, null);
        if (saved == null) {
            return defaultWorkspace;
        }
        try {
            return Files.isDirectory(Path.of(saved)) ? saved : defaultWorkspace;
        } catch (InvalidPathException exception) {
            return defaultWorkspace;
        }
    }

    void saveModel(String model) {
        if (model != null && !model.isBlank()) {
            preferences.put(MODEL_KEY, model);
        }
    }

    void saveWorkspace(String workspace) {
        if (workspace != null && !workspace.isBlank()) {
            preferences.put(WORKSPACE_KEY, workspace);
        }
    }

    Rectangle restoreWindowBounds(Rectangle fallback, Dimension minimumSize) {
        int width = preferences.getInt(WINDOW_WIDTH_KEY, -1);
        int height = preferences.getInt(WINDOW_HEIGHT_KEY, -1);
        int x = preferences.getInt(WINDOW_X_KEY, Integer.MIN_VALUE);
        int y = preferences.getInt(WINDOW_Y_KEY, Integer.MIN_VALUE);
        if (width <= 0 || height <= 0 || x == Integer.MIN_VALUE || y == Integer.MIN_VALUE) {
            return fallback;
        }

        int safeWidth = Math.max(width, minimumSize.width);
        int safeHeight = Math.max(height, minimumSize.height);
        Rectangle saved = new Rectangle(x, y, safeWidth, safeHeight);
        return isVisible(saved) ? saved : fallback;
    }

    void saveWindowBounds(Rectangle bounds) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        preferences.putInt(WINDOW_X_KEY, bounds.x);
        preferences.putInt(WINDOW_Y_KEY, bounds.y);
        preferences.putInt(WINDOW_WIDTH_KEY, bounds.width);
        preferences.putInt(WINDOW_HEIGHT_KEY, bounds.height);
    }

    private static boolean isVisible(Rectangle bounds) {
        try {
            for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                Rectangle intersection = device.getDefaultConfiguration().getBounds().intersection(bounds);
                if (intersection.width >= 100 && intersection.height >= 100) {
                    return true;
                }
            }
        } catch (java.awt.HeadlessException exception) {
            return false;
        }
        return false;
    }
}
