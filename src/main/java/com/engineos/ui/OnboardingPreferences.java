package com.engineos.ui;

import java.util.prefs.Preferences;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

final class OnboardingPreferences {
    private static final String PREPARED_KEY = "engineering-partner-prepared";
    private static final String MODEL_KEY = "execution-model";
    private static final String WORKSPACE_KEY = "execution-workspace";
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
}
