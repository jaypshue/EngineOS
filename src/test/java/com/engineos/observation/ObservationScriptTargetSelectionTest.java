package com.engineos.observation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservationScriptTargetSelectionTest {
    @Test
    void excludesJavaProcessesThatExistedBeforeTargetLaunch() throws IOException {
        String script = Files.readString(Path.of(
                "scripts", "capture-intassist-observation.ps1"));

        assertTrue(script.contains("($ExcludedProcessIds -notcontains $_.Id)"));
        assertTrue(script.contains("$existingJavaProcessIds"));
        assertTrue(script.contains(
                "Wait-ForTargetWindow -ExcludedProcessIds $existingJavaProcessIds"));
        assertTrue(script.contains("Ambiguous $TargetKind target"));
        assertTrue(script.contains("$targetProcessId = $targetIdentity.ProcessId"));
        assertTrue(script.contains("$targetProcessStartTime = $targetIdentity.ProcessStartTime"));
        assertTrue(script.contains("$targetProcess.StartTime -eq $targetProcessStartTime"));
        assertTrue(script.contains("Get-Process -Id $targetProcessId"));
        assertTrue(script.contains("Stop-Process -Id $targetProcessId -Force"));
        assertTrue(script.contains("target-process-cleanup-verified"));
        assertTrue(script.contains("target-process-selected"));
    }
}
