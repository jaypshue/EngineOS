package com.engineos.agent;

import com.engineos.domain.CopilotResultDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsageFileReaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsUsageAndSemanticFieldsFromObservedSidecar() throws Exception {
        Path sidecar = temporaryDirectory.resolve("usage.json");
        Files.writeString(sidecar, """
                {
                  "totalNanoAiu": 1053237000,
                  "currentModel": "gpt-5.6-luna",
                  "inputTokens": 155211,
                  "codeChanges": {
                    "linesAdded": 2,
                    "linesRemoved": 0,
                    "filesModifiedCount": 1,
                    "filesModified": [
                      "C:\\\\projects\\\\EngineOS\\\\history-verification.txt"
                    ]
                  },
                  "totalApiDurationMs": 17592
                }
                """);

        var result = UsageFileReader.read(sidecar, "auto");

        assertEquals("gpt-5.6-luna", result.usage().model());
        assertEquals("155211", result.usage().inputTokens());
        assertEquals(17592L, result.totalApiDurationMs());
        assertEquals(2, result.linesAdded());
        assertEquals(0, result.linesRemoved());
        assertEquals(1, result.filesModified());
        assertEquals(List.of("C:\\projects\\EngineOS\\history-verification.txt"), result.modifiedFiles());
    }

    @Test
    void missingSemanticFieldsRemainUnavailable() throws Exception {
        Path sidecar = temporaryDirectory.resolve("usage.json");
        Files.writeString(sidecar, "{\"currentModel\":\"gpt-5.6-luna\"}");

        var result = UsageFileReader.read(sidecar, "auto");

        assertFalse(result.semanticAvailable());
        assertEquals(null, result.totalApiDurationMs());
        assertEquals(null, result.linesAdded());
        assertEquals(List.of(), result.modifiedFiles());
    }

    @Test
    void doesNotRepresentZeroCreditsAsMeasuredUsage() throws Exception {
        Path sidecar = temporaryDirectory.resolve("usage-zero.json");
        Files.writeString(sidecar, """
                {"currentModel":"gpt-5.6-luna","totalNanoAiu":0,"inputTokens":155211}
                """);

        var result = UsageFileReader.read(sidecar, "auto");

        assertEquals(null, result.usage().aiCredits());
        assertEquals("155211", result.usage().inputTokens());
        assertTrue(result.usage().available());
    }

    @Test
    void stdoutFieldsTakePrecedenceAndSidecarFillsMissingFields() throws Exception {
        Path sidecar = temporaryDirectory.resolve("usage.json");
        Files.writeString(sidecar, """
                {
                  "currentModel": "sidecar-model",
                  "totalApiDurationMs": 17592,
                  "codeChanges": {
                    "linesAdded": 2,
                    "linesRemoved": 0,
                    "filesModifiedCount": 1,
                    "filesModified": ["sidecar.txt"]
                  }
                }
                """);
        var sidecarData = UsageFileReader.read(sidecar, "auto");
        var stdout = CopilotResultDetailsTestSupport.details(
                "stdout-model", 9000L, null, 7, null, 9, List.of("stdout.txt"));

        var merged = CopilotCoderAgent.mergeDetails(stdout, sidecarData);

        assertTrue(merged.complete());
        assertEquals("stdout-model", merged.actualModel());
        assertEquals(9000L, merged.totalApiDurationMs());
        assertEquals(7, merged.linesAdded());
        assertEquals(0, merged.linesRemoved());
        assertEquals(9, merged.filesModified());
        assertEquals(List.of("stdout.txt"), merged.modifiedFiles());
    }

    private static final class CopilotResultDetailsTestSupport {
        private static CopilotResultDetails details(
                String model, Long apiDuration, Long sessionDuration,
                Integer added, Integer removed, Integer files, List<String> modified) {
            return new CopilotResultDetails(true, model, apiDuration, sessionDuration,
                    added, removed, files, modified);
        }
    }
}
