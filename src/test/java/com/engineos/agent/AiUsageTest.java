package com.engineos.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiUsageTest {
	@Test
	void formatsOnlyValuesReportedByTheRuntime() {
		var usage = new AiUsage("runtime-model", "1.25", "10", "4", "2", "1", null, null);

		var display = usage.display();

		assertTrue(display.contains("AI USAGE (actual)"));
		assertTrue(display.contains("Model: runtime-model"));
		assertTrue(display.contains("AI credits: 1.25"));
		assertTrue(display.contains("Input tokens: 10"));
		assertTrue(!display.contains("MONTHLY / ACCOUNT"));
	}
}
