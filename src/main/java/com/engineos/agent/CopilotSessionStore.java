package com.engineos.agent;

import com.engineos.domain.WorkspaceContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.regex.Pattern;

public final class CopilotSessionStore {
	private static final Pattern WORKSPACE = Pattern.compile("\\\"workspace\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
	private static final Pattern SESSION = Pattern.compile("\\\"sessionId\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");

	private final Path file;

	public CopilotSessionStore() {
		this(Path.of(System.getProperty("user.home"), ".engineos", "copilot-session.json"));
	}

	CopilotSessionStore(Path file) {
		this.file = file;
	}

	public Optional<String> sessionFor(WorkspaceContext workspace) {
		try {
			if (!Files.isRegularFile(file)) {
				return Optional.empty();
			}
			String json = Files.readString(file, StandardCharsets.UTF_8);
			String storedWorkspace = value(WORKSPACE, json);
			if (!normalize(workspace.root()).equals(storedWorkspace)) {
				return Optional.empty();
			}
			return Optional.ofNullable(value(SESSION, json)).filter(value -> !value.isBlank());
		} catch (IOException exception) {
			return Optional.empty();
		}
	}

	public void save(WorkspaceContext workspace, String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			String json = "{\n  \"workspace\": \"" + escape(normalize(workspace.root()))
					+ "\",\n  \"sessionId\": \"" + escape(sessionId) + "\"\n}\n";
			Files.writeString(file, json, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException ignored) {
			// Session persistence is best-effort and must not prevent a coding run.
		}
	}

	public Path file() {
		return file;
	}

	private static String normalize(Path path) {
		return path.toAbsolutePath().normalize().toString();
	}

	private static String value(Pattern pattern, String json) {
		var matcher = pattern.matcher(json);
		return matcher.find() ? matcher.group(1).replace("\\\\", "\\").replace("\\\"", "\"") : null;
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
