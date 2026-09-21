package com.engineos.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class UsageFileReader {
	private UsageFileReader() {
	}

	static SidecarData read(Path path, String requestedModel) {
		try {
			String json = Files.readString(path);
			String model = value(json, "currentModel");
			if (model == null) model = requestedModel;
			String credits = value(json, "totalNanoAiu");
			if (credits != null) {
				long nanoCredits = Long.parseLong(credits);
				credits = nanoCredits <= 0 ? null : String.format("%.4f", nanoCredits / 1_000_000_000d);
			}
			var usage = new AiUsage(model, credits, value(json, "inputTokens"), value(json, "outputTokens"),
					value(json, "cacheReadTokens"), value(json, "reasoningTokens"),
					value(json, "quotaUsed"), value(json, "quotaRemaining"));
			return new SidecarData(
					usage,
					numberLong(json, "totalApiDurationMs"),
					numberInt(json, "linesAdded"),
					numberInt(json, "linesRemoved"),
					numberInt(json, "filesModifiedCount"),
					strings(json, "filesModified"));
		} catch (IOException | NumberFormatException exception) {
			return SidecarData.unavailable();
		}
	}

	record SidecarData(
			AiUsage usage,
			Long totalApiDurationMs,
			Integer linesAdded,
			Integer linesRemoved,
			Integer filesModified,
			List<String> modifiedFiles) {
		SidecarData {
			modifiedFiles = modifiedFiles == null ? List.of() : List.copyOf(modifiedFiles);
		}

		static SidecarData unavailable() {
			return new SidecarData(AiUsage.unavailable(), null, null, null, null, List.of());
		}

		boolean semanticAvailable() {
			return totalApiDurationMs != null
					|| linesAdded != null
					|| linesRemoved != null
					|| filesModified != null
					|| !modifiedFiles.isEmpty();
		}
	}

	private static String value(String json, String key) {
		var matcher = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*(?:\\\"([^\\\"]*)\\\"|(\\d+(?:\\.\\d+)?))")
				.matcher(json);
		String found = null;
		while (matcher.find()) found = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
		return found;
	}

	private static Long numberLong(String json, String key) {
		String value = value(json, key);
		return value == null ? null : Long.valueOf(value);
	}

	private static Integer numberInt(String json, String key) {
		Long value = numberLong(json, key);
		return value == null ? null : value.intValue();
	}

	private static List<String> strings(String json, String key) {
		var matcher = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(json);
		if (!matcher.find()) {
			return List.of();
		}
		var values = new ArrayList<String>();
		var itemMatcher = Pattern.compile("\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(matcher.group(1));
		while (itemMatcher.find()) {
			values.add(itemMatcher.group(1).replace("\\\\", "\\").replace("\\\"", "\""));
		}
		return values;
	}
}