package com.engineos.history;

import com.engineos.domain.EngineeringRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Deterministic assessment of a persisted run, derived only from recorded evidence.
 */
public record EngineeringAssessment(
        String implementationStatus,
        String verificationStatus,
        String evidenceStatus,
        String keyFinding,
        String actualChanges,
        String verification,
        String evidence,
        List<String> uncertainties,
        String nextAction) {

    public EngineeringAssessment {
        implementationStatus = status(implementationStatus);
        verificationStatus = status(verificationStatus);
        evidenceStatus = status(evidenceStatus);
        uncertainties = uncertainties == null ? List.of() : List.copyOf(uncertainties);
    }

    public static EngineeringAssessment from(EngineeringHistoryRecord record) {
        String implementation = record == null || record.codingResultStatus() == null
                ? "UNKNOWN"
                : record.exitCode() != null && record.exitCode() == 0
                ? "COMPLETE" : "NOT COMPLETE";
        String verificationStatus;
        if (record == null || record.observationStatus() == null
                || "NOT_RUN".equalsIgnoreCase(record.observationStatus())) {
            verificationStatus = "NOT PERFORMED";
        } else if (record.observationAvailable()
                && "SUCCEEDED".equalsIgnoreCase(record.observationStatus())
                && (record.verificationExitCode() == null || record.verificationExitCode() == 0)) {
            verificationStatus = verificationEvidencePresent(record)
                    ? "VERIFIED" : "INSUFFICIENT_EVIDENCE";
        } else if (isBlocked(record)) {
            verificationStatus = "BLOCKED";
        } else {
            verificationStatus = "FAILED";
        }

        boolean sufficient = "COMPLETE".equals(implementation)
                && "VERIFIED".equals(verificationStatus)
                && record.semanticResultComplete()
                && record.observationAvailable()
                && verificationEvidencePresent(record);
        String evidenceStatus = sufficient ? "SUFFICIENT" : "INSUFFICIENT";
        String diagnostic = diagnostic(record);
        String finding = keyFinding(implementation, verificationStatus, diagnostic);
        var uncertainties = new ArrayList<String>();
        if (!record.semanticResultAvailable()) {
            uncertainties.add("Semantic result unavailable.");
        } else if (!record.semanticResultComplete()) {
            uncertainties.add("Semantic result incomplete.");
        }
        if ("NOT PERFORMED".equals(verificationStatus)) {
            uncertainties.add("Verification did not run.");
        } else if ("INSUFFICIENT_EVIDENCE".equals(verificationStatus)) {
            uncertainties.add("Verification completed, but recorded output does not demonstrate the acceptance criteria.");
        } else if (!"VERIFIED".equals(verificationStatus) && !diagnostic.isBlank()) {
            uncertainties.add("Verification evidence is incomplete: " + diagnostic);
        }
        if (record.sessionId() == null) {
            uncertainties.add("Coding-agent session unavailable.");
        }
        String nextAction = "VERIFIED".equals(verificationStatus) && "COMPLETE".equals(implementation)
                ? null
                : record.verificationCommand() == null || record.verificationCommand().isBlank()
                ? "Resolve the recorded implementation or verification gap, then run targeted verification."
                : "Resolve the recorded verification gap, then rerun: " + record.verificationCommand();
        return new EngineeringAssessment(
                implementation, verificationStatus, evidenceStatus, finding,
                changes(record), verification(record), evidence(record), uncertainties, nextAction);
    }

    private static String keyFinding(String implementation, String verification, String diagnostic) {
        if ("COMPLETE".equals(implementation) && "VERIFIED".equals(verification)) {
            return "Requested implementation completed and targeted verification passed.";
        }
        if ("COMPLETE".equals(implementation) && "BLOCKED".equals(verification)) {
            return "Requested implementation completed; behavioral verification was blocked"
                    + suffix(diagnostic) + ".";
        }
        if ("COMPLETE".equals(implementation) && "FAILED".equals(verification)) {
            return "Requested implementation completed; behavioral verification failed"
                    + suffix(diagnostic) + ".";
        }
        if ("NOT PERFORMED".equals(verification)) {
            return "Implementation status is " + implementation + "; behavioral verification was not performed.";
        }
        return "Implementation status is " + implementation + "; requested behavior is not verified.";
    }

    private static String changes(EngineeringHistoryRecord record) {
        return "+" + value(record.linesAdded()) + " / -" + value(record.linesRemoved())
                + " lines across " + value(record.filesModified()) + " files: "
                + record.modifiedFiles();
    }

    private static String verification(EngineeringHistoryRecord record) {
        return "Command: " + value(record.verificationCommand())
                + "\nExit code: " + value(record.verificationExitCode())
                + "\nDuration: " + value(record.verificationDuration())
                + diagnostics(record);
    }

    private static String evidence(EngineeringHistoryRecord record) {
        return "Semantic result: " + (record.semanticResultComplete() ? "complete"
                : record.semanticResultAvailable() ? "incomplete" : "unavailable")
                + "\nObservation: " + value(record.observationStatus())
                + "\nObservation available: " + record.observationAvailable()
                + "\nAcceptance criteria: " + acceptanceCriteria(record)
                + "\nBehavioral evidence captured: " + verificationEvidencePresent(record);
    }

    private static boolean verificationEvidencePresent(EngineeringHistoryRecord record) {
        if (record == null || !record.observationAvailable()
                || !"SUCCEEDED".equalsIgnoreCase(record.observationStatus())
                || (record.verificationExitCode() != null && record.verificationExitCode() != 0)) {
            return false;
        }
        String output = record.verificationOutput() == null ? "" : record.verificationOutput().trim();
        if (output.isBlank() || record.verificationCommand() == null
                || record.verificationCommand().isBlank()) {
            return false;
        }
        List<String> criteria = EngineeringRequest.parse(record.engineeringRequest()).acceptanceCriteria();
        if (criteria.isEmpty()) {
            return true;
        }
        String evidence = output.toLowerCase(Locale.ROOT);
        return criteria.stream().allMatch(criterion -> {
            Set<String> terms = Arrays.stream(criterion.toLowerCase(Locale.ROOT)
                    .split("\\W+"))
                    .filter(term -> term.length() >= 4)
                    .collect(Collectors.toSet());
            return !terms.isEmpty() && terms.stream().anyMatch(evidence::contains);
        });
    }

    private static String acceptanceCriteria(EngineeringHistoryRecord record) {
        if (record == null) {
            return "unavailable";
        }
        List<String> criteria = EngineeringRequest.parse(record.engineeringRequest()).acceptanceCriteria();
        return criteria.isEmpty() ? "none recorded" : criteria.toString();
    }

    private static boolean isBlocked(EngineeringHistoryRecord record) {
        String text = diagnostic(record).toLowerCase();
        return text.contains("compilation") || text.contains("compile")
                || text.contains("cannot find symbol") || text.contains("baseline")
                || text.contains("does not exist");
    }

    private static String diagnostic(EngineeringHistoryRecord record) {
        if (record == null) {
            return "";
        }
        String error = record.verificationError();
        if (error != null && !error.isBlank()) {
            return concise(error);
        }
        return record.verificationOutput() == null ? "" : concise(record.verificationOutput());
    }

    private static String diagnostics(EngineeringHistoryRecord record) {
        String output = record.verificationOutput();
        String error = record.verificationError();
        return (output == null || output.isBlank() ? "" : "\nOutput:\n" + output)
                + (error == null || error.isBlank() ? "" : "\nError:\n" + error);
    }

    private static String suffix(String diagnostic) {
        return diagnostic.isBlank() ? "" : ": " + diagnostic;
    }

    private static String concise(String value) {
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() > 300 ? compact.substring(0, 297) + "..." : compact;
    }

    private static String status(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private static String value(Object value) {
        return value == null ? "unavailable" : value.toString();
    }
}
