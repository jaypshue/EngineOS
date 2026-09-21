package com.engineos.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class CopilotModelCatalog {
	private static final Duration TIMEOUT = Duration.ofSeconds(20);

	private CopilotModelCatalog() {
	}

	public static List<String> discover() {
		try {
			var process = new ProcessBuilder("cmd.exe", "/d", "/c", "copilot", "-i", "/model",
					"--output-format", "json", "--no-color", "--no-auto-update")
					.redirectErrorStream(true)
					.start();
			var output = read(process);
			if (!process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return defaultModels();
			}
			return fromOutput(output);
		} catch (IOException exception) {
			return defaultModels();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
		return defaultModels();
	}

	static List<String> fromOutput(String output) {
		var models = new ArrayList<>(defaultModels());
		var activeModel = extract(output, "lastActiveModel");
		if (activeModel == null) {
			activeModel = extract(output, "currentModel");
		}
		if (activeModel != null && !activeModel.isBlank() && !models.contains(activeModel)) {
			models.add(activeModel);
		}
		return List.copyOf(models);
	}

	private static List<String> defaultModels() {
		return List.of("auto", "gpt-5.6-luna");
	}

	private static String read(Process process) throws IOException {
		try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			var output = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line).append('\n');
			}
			return output.toString();
		}
	}

	private static String extract(String output, String key) {
		var marker = "\"" + key + "\":\"";
		var start = output.lastIndexOf(marker);
		if (start < 0) {
			return null;
		}
		start += marker.length();
		var end = output.indexOf('"', start);
		return end < 0 ? null : output.substring(start, end);
	}
}
