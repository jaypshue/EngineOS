package com.engineos.history;

public final class HistoryPresentation {
    private HistoryPresentation() {
    }

    public static String summary(EngineeringHistoryRecord record) {
        return record == null ? "Engineering run" : record.displaySummary();
    }

    public static String format(EngineeringHistoryRecord record) {
        if (record == null) {
            return "History record unavailable.";
        }
        var usage = record.aiUsage() == null ? "AI usage unavailable" : record.aiUsage().display();
        return formatWithReport(record, usage);
    }

    public static String handoff(EngineeringHistoryRecord record) {
        if (record == null) {
            return "ENGINEERING RESULT\n\nHistory record unavailable.";
        }
        var assessment = EngineeringAssessment.from(record);
        return "ENGINEERING RESULT"
                + "\nWorkspace: " + value(record.workspace())
                + "\nRun ID: " + value(record.runId())
                + "\nTimestamp: " + value(record.completedAt())
                + "\nDuration: " + value(record.duration())
                + "\n\nORIGINAL ENGINEOS_REQUEST_V1\n" + value(record.engineeringRequest())
                + "\n\nENGINEERING ASSESSMENT"
                + "\nImplementation status: " + assessment.implementationStatus()
                + "\nVerification status: " + assessment.verificationStatus()
                + "\nEvidence status: " + assessment.evidenceStatus()
                + "\nKey finding: " + assessment.keyFinding()
                + "\n\nACTUAL FINAL WORKSPACE CHANGES\n" + assessment.actualChanges()
                + "\nFiles: " + record.modifiedFiles()
                + "\n\nVERIFICATION\n" + assessment.verification()
                + "\n\nEVIDENCE / OBSERVATION\n" + assessment.evidence()
                + "\n\nCODING AGENT ACTIVITY\n" + activity(record)
                + "\n\nUNCERTAINTIES / EVIDENCE GAPS\n"
                + (assessment.uncertainties().isEmpty() ? "None recorded."
                : String.join("\n", assessment.uncertainties()))
                + (assessment.nextAction() == null ? "" : "\n\nNEXT ACTION\n" + assessment.nextAction());
    }

    private static String formatWithReport(EngineeringHistoryRecord record, String usage) {
        var assessment = record.assessment();
        return "Run: " + value(record.runId())
                + "\nCompleted: " + value(record.completedAt())
                + "\nDuration: " + value(record.duration())
                + "\nStatus: " + value(record.finalStatus())
                + "\nWorkspace: " + value(record.workspace())
                + "\n\nENGINEERING ASSESSMENT"
                + "\nKey finding: " + assessment.keyFinding()
                + "\nImplementation: " + assessment.implementationStatus()
                + "\nVerification: " + assessment.verificationStatus()
                + "\nEvidence: " + assessment.evidenceStatus()
                + (assessment.nextAction() == null ? "" : "\nNext action: " + assessment.nextAction())
                + "\n\nENGINEERING REQUEST\n" + value(record.engineeringRequest())
                + "\n\nEXECUTION\nCoding agent: " + value(record.codingAgent())
                + " (" + value(record.provider()) + ")"
                + "\nRequested model: " + value(record.requestedModel())
                + "\nActual model: " + value(record.actualModel())
                + "\nExit code: " + value(record.exitCode())
                + "\nSession: " + value(record.sessionId())
                + "\nSemantic result: " + (record.semanticResultComplete() ? "complete"
                : record.semanticResultAvailable() ? "incomplete" : "unavailable")
                + "\n" + usage
                + "\n\nCHANGES\nChanges: +" + value(record.linesAdded())
                + " / -" + value(record.linesRemoved())
                + " lines; files " + record.modifiedFiles()
                + "\n\nVERIFICATION\nTarget: " + value(record.verificationTarget())
                + "\n" + value(record.verificationSummary())
                + "\nCommand: " + value(record.verificationCommand())
                + "\nExit code: " + value(record.verificationExitCode())
                + "\nDuration: " + value(record.verificationDuration())
                + (record.verificationOutput() == null || record.verificationOutput().isBlank()
                ? "" : "\nOutput:\n" + record.verificationOutput())
                + (record.verificationError() == null || record.verificationError().isBlank()
                ? "" : "\nError:\n" + record.verificationError())
                + "\n\nOBSERVATION\nStatus: " + value(record.observationStatus())
                + "\nAvailable: " + record.observationAvailable()
                + (record.observationLocation() == null ? "" : "\nLocation: " + record.observationLocation())
                + "\n\nCODING AGENT REPORT / ENGINEERING RESULT\n" + value(record.engineeringResult())
                + "\n\nCODING AGENT ACTIVITY\n" + activity(record)
                + "\n\nSUPERVISION\nStatus: " + value(record.finalStatus())
                + "\nEvidence: " + evidenceStatus(record)
                + "\n\nUNCERTAINTIES\n" + uncertainties(record);
    }

    private static String uncertainties(EngineeringHistoryRecord record) {
        var uncertainties = new java.util.ArrayList<String>();
        if (!record.semanticResultAvailable()) {
            uncertainties.add("Semantic Copilot result unavailable.");
        } else if (!record.semanticResultComplete()) {
            uncertainties.add("Semantic Copilot result incomplete.");
        }
        if (record.sessionId() == null) {
            uncertainties.add("Copilot session unavailable.");
        }
        if ("FAILED".equalsIgnoreCase(record.observationStatus())) {
            String reason = record.verificationError();
            uncertainties.add(reason == null || reason.isBlank()
                    ? "Verification failed; requested behavior remains unverified."
                    : "Verification failed: " + concise(reason));
        } else if ("NOT_RUN".equalsIgnoreCase(record.observationStatus())) {
            uncertainties.add("Verification did not run; requested behavior remains unverified.");
        }
        return uncertainties.isEmpty() ? "None recorded." : String.join("\n", uncertainties);
    }

    private static String evidenceStatus(EngineeringHistoryRecord record) {
        return record.assessment().evidenceStatus().toLowerCase();
    }

    private static String activity(EngineeringHistoryRecord record) {
        if (record.activity() == null || record.activity().isEmpty()) {
            return "No persisted Coding Agent activity was recorded for this run."
                    + (record.engineeringResult() == null
                    ? "" : " This may be a legacy history record.");
        }
        return record.activity().stream()
                .map(entry -> "- " + entry)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String concise(String value) {
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() > 300 ? compact.substring(0, 297) + "..." : compact;
    }

    private static String value(Object value) {
        return value == null ? "unavailable" : value.toString();
    }
}
