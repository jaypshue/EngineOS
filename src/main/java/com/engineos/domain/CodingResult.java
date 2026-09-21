package com.engineos.domain;

import com.engineos.agent.AiUsage;
import java.time.Duration;

public record CodingResult(
        int exitCode,
        String standardOutput,
        String standardError,
        Duration duration,
        String requestedModel,
        AiUsage usage,
        String sessionId,
        CopilotResultDetails details) {

    public CodingResult(int exitCode, String standardOutput, String standardError, Duration duration) {
        this(exitCode, standardOutput, standardError, duration, null, AiUsage.unavailable(), null,
                CopilotResultDetails.unavailable());
    }

    public CodingResult(int exitCode, String standardOutput, String standardError, Duration duration,
                        String requestedModel, AiUsage usage) {
        this(exitCode, standardOutput, standardError, duration, requestedModel, usage, null,
                CopilotResultDetails.unavailable());
    }

    public CodingResult(int exitCode, String standardOutput, String standardError, Duration duration,
                        String requestedModel, AiUsage usage, String sessionId) {
        this(exitCode, standardOutput, standardError, duration, requestedModel, usage, sessionId,
                CopilotResultDetails.unavailable());
    }

    public boolean succeeded() {
        return exitCode == 0;
    }

    public boolean semanticResultAvailable() {
        return details != null && details.available();
    }

    public boolean semanticResultComplete() {
        return details != null && details.complete();
    }

    public String summary() {
        if (!succeeded()) {
            return "Coder failed (exit code " + exitCode + ").";
        }
        if (!semanticResultAvailable()) {
            return "Coder process completed (exit code 0); semantic result unavailable.";
        }
        if (!semanticResultComplete()) {
            return "Coder process completed (exit code 0); semantic result incomplete.";
        }
        return "Coder completed successfully (exit code 0).";
    }

    public String report() {
        return report(details);
    }

    public String report(CopilotResultDetails reportedDetails) {
        String usageReport = usage != null && usage.available()
                ? usage.display()
                : "AI USAGE (actual)\n"
                + "Model: " + (requestedModel == null ? "unavailable" : requestedModel) + "\n"
                + "AI credits: unavailable";
        boolean resultAvailable = reportedDetails != null && reportedDetails.available();
        String semanticReport = resultAvailable
                ? "\nSemantic result: " + (reportedDetails.complete() ? "complete" : "incomplete")
                + "\nCode changes: +" + value(reportedDetails.linesAdded())
                + " / -" + value(reportedDetails.linesRemoved())
                + " lines, " + value(reportedDetails.filesModified()) + " files"
                : "\nSemantic Copilot result: unavailable";
        return summary() + "\n" + usageReport + semanticReport;
    }

    private static String value(Object value) {
        return value == null ? "unavailable" : value.toString();
    }
}
