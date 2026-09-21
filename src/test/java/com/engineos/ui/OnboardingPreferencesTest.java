package com.engineos.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OnboardingPreferencesTest {
    @TempDir
    Path workspace;

    @Test
    void noPersistedExecutionStateUsesExistingDefaults() throws Exception {
        withPreferences(preferences -> {
            var store = new OnboardingPreferences(preferences);

            assertEquals("auto", store.restoreModel(List.of("auto", "gpt-5.6-luna"), "auto"));
            assertEquals("fallback", store.restoreWorkspace("fallback"));
        });
    }

    @Test
    void persistedModelAndWorkspaceAreRestoredAcrossStoreInstances() throws Exception {
        withPreferences(preferences -> {
            var first = new OnboardingPreferences(preferences);
            first.saveModel("gpt-5.6-luna");
            first.saveWorkspace(workspace.toString());

            var restored = new OnboardingPreferences(preferences);
            assertEquals("gpt-5.6-luna",
                    restored.restoreModel(List.of("auto", "gpt-5.6-luna"), "auto"));
            assertEquals(workspace.toString(), restored.restoreWorkspace("fallback"));
        });
    }

    @Test
    void laterChangesReplacePreviouslyPersistedValues() throws Exception {
        withPreferences(preferences -> {
            var store = new OnboardingPreferences(preferences);
            store.saveModel("auto");
            store.saveWorkspace(workspace.toString());
            store.saveModel("gpt-5.6-luna");

            var restored = new OnboardingPreferences(preferences);
            assertEquals("gpt-5.6-luna",
                    restored.restoreModel(List.of("auto", "gpt-5.6-luna"), "auto"));
            assertEquals(workspace.toString(), restored.restoreWorkspace("fallback"));
        });
    }

    @Test
    void invalidPersistedValuesFallBackSafely() throws Exception {
        withPreferences(preferences -> {
            preferences.put("execution-model", "retired-model");
            preferences.put("execution-workspace", "C:\\does-not-exist\\engineos");
            var store = new OnboardingPreferences(preferences);

            assertEquals("auto",
                    store.restoreModel(List.of("auto", "gpt-5.6-luna"), "auto"));
            assertEquals("fallback", store.restoreWorkspace("fallback"));
        });
    }

    private static void withPreferences(PreferenceAction action) throws Exception {
        String nodeName = "engineos-test-" + UUID.randomUUID();
        Preferences preferences = Preferences.userRoot().node(nodeName);
        try {
            action.accept(preferences);
        } finally {
            preferences.removeNode();
            preferences.flush();
        }
    }

    @FunctionalInterface
    private interface PreferenceAction {
        void accept(Preferences preferences) throws Exception;
    }
}
