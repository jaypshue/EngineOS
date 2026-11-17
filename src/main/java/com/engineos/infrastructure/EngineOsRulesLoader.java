package com.engineos.infrastructure;

import com.engineos.domain.ProjectRules;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class EngineOsRulesLoader {
    public static final int MAX_BYTES = 64 * 1024;
    private static final String RELATIVE_PATH = ".engineos/rules.md";

    public ProjectRules load(Path root) {
        Path rulesPath = root.resolve(RELATIVE_PATH).normalize();
        if (!Files.isRegularFile(rulesPath)) {
            return ProjectRules.NONE;
        }
        try {
            long size = Files.size(rulesPath);
            if (size > MAX_BYTES) {
                throw new IllegalArgumentException("EngineOS rules exceed " + MAX_BYTES + " bytes: " + rulesPath);
            }
            byte[] bytes = Files.readAllBytes(rulesPath);
            String content = decodeStrict(bytes);
            return new ProjectRules(rulesPath.toAbsolutePath().normalize(), content, sha256(bytes));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load EngineOS rules: " + exception.getMessage(), exception);
        }
    }

    private static String decodeStrict(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("EngineOS rules must be valid UTF-8", exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
