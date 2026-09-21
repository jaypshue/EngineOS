package com.engineos.agent;

import java.util.Map;

public record AiUsage(
		String model,
		String aiCredits,
		String inputTokens,
		String outputTokens,
		String cachedInput,
		String reasoning,
		String quotaUsed,
		String quotaRemaining) {

	public static AiUsage unavailable() {
		return new AiUsage(null, null, null, null, null, null, null, null);
	}

	public boolean available() {
		return model != null || aiCredits != null || inputTokens != null || outputTokens != null
				|| cachedInput != null || reasoning != null || quotaUsed != null || quotaRemaining != null;
	}

	public String display() {
		var lines = new StringBuilder("AI USAGE (actual)\n");
		append(lines, "Model", model);
		append(lines, "AI credits", aiCredits);
		append(lines, "Input tokens", inputTokens);
		append(lines, "Output tokens", outputTokens);
		append(lines, "Cached input", cachedInput);
		append(lines, "Reasoning", reasoning);
		if (quotaUsed != null || quotaRemaining != null) {
			lines.append("\nMONTHLY / ACCOUNT\n");
			append(lines, "Used", quotaUsed);
			append(lines, "Remaining", quotaRemaining);
		}
		return lines.toString().trim();
	}

	private static void append(StringBuilder lines, String name, String value) {
		if (value != null && !value.isBlank()) {
			lines.append(name).append(": ").append(value).append('\n');
		}
	}
}
