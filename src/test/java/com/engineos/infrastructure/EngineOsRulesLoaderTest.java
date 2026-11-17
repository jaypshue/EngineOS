package com.engineos.infrastructure;

import com.engineos.domain.ProjectRules;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EngineOsRulesLoaderTest {
    @Test
    void loadsRulesWithStableHashAndIgnoresMissingFile() throws Exception {
        Path root = Files.createTempDirectory("engineos-rules-");
        assertFalse(new EngineOsRulesLoader().load(root).present());
        Path rules = root.resolve(".engineos/rules.md");
        Files.createDirectories(rules.getParent());
        Files.writeString(rules, "Keep changes bounded.\n");

        ProjectRules loaded = new EngineOsRulesLoader().load(root);
        assertEquals(rules.toAbsolutePath().normalize(), loaded.path());
        assertEquals("Keep changes bounded.\n", loaded.content());
        assertEquals(64, loaded.sha256().length(), "sha256");
        assertEquals(loaded.sha256(), new EngineOsRulesLoader().load(root).sha256());
    }

    @Test
    void rejectsOversizedAndInvalidUtf8Rules() throws Exception {
        Path root = Files.createTempDirectory("engineos-rules-");
        Path rules = root.resolve(".engineos/rules.md");
        Files.createDirectories(rules.getParent());
        Files.write(rules, new byte[EngineOsRulesLoader.MAX_BYTES + 1]);
        assertThrows(IllegalArgumentException.class, () -> new EngineOsRulesLoader().load(root));

        Files.write(rules, new byte[]{(byte) 0xc3, (byte) 0x28});
        assertThrows(IllegalArgumentException.class, () -> new EngineOsRulesLoader().load(root));
    }
}
