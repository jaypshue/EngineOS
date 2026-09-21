package com.engineos.intelligence;

import java.util.ArrayList;

public final class ProjectOrientationPresentation {
    private ProjectOrientationPresentation() {
    }

    public static String format(ProjectOrientation orientation) {
        if (orientation == null) {
            return "PROJECT ORIENTATION\n\nUNKNOWN / UNAVAILABLE\n- Project orientation is unavailable.";
        }
        var whole = orientation.wholeRepository();
        var architecture = orientation.applicationArchitecture();
        var unknown = new ArrayList<String>();
        unknown.addAll(whole.uncertainties());
        unknown.addAll(architecture.uncertainties());
        unknown.addAll(orientation.verification().uncertainties());

        var output = new StringBuilder("PROJECT ORIENTATION\n\n");
        output.append("WHOLE REPOSITORY\n")
                .append("Identity: ").append(value(whole.identity())).append('\n')
                .append("Root: ").append(value(whole.root())).append('\n')
                .append("Languages: ").append(list(whole.languages())).append('\n')
                .append("Frameworks: ").append(list(whole.frameworks())).append('\n')
                .append("Major directories: ").append(list(whole.majorDirectories())).append('\n')
                .append("Build/configuration files: ").append(list(whole.buildAndConfigurationFiles()))
                .append("\n\n");

        output.append("APPLICATION ARCHITECTURE\n");
        lines(output, architecture.components());
        output.append("Relationships:\n");
        lines(output, architecture.relationships());
        output.append('\n');

        output.append("RELEVANT SUBSYSTEMS\n");
        for (var subsystem : orientation.relevantSubsystems()) {
            output.append("- ").append(subsystem.name()).append(" [")
                    .append(subsystem.path()).append("] — ").append(subsystem.evidence()).append('\n');
        }
        if (orientation.relevantSubsystems().isEmpty()) output.append("- unavailable\n");
        output.append('\n');

        output.append("RELEVANT TYPES\n");
        for (var type : orientation.relevantTypes()) {
            output.append("- ").append(type.kind()).append(' ').append(type.name())
                    .append(" (").append(type.path()).append(")");
            if (type.packageName() != null && !type.packageName().isBlank()) {
                output.append(" package ").append(type.packageName());
            }
            output.append('\n');
        }
        if (orientation.relevantTypes().isEmpty()) output.append("- unavailable\n");
        output.append('\n');

        output.append("EXECUTION PATH\n");
        lines(output, orientation.executionPath());
        output.append('\n');

        output.append("CURRENT CHANGE\n");
        for (var change : orientation.currentChange()) {
            output.append("- Run ").append(value(change.runId()))
                    .append(": ").append(value(change.status()))
                    .append("; verified=").append(change.verified())
                    .append("; changes +").append(value(change.linesAdded()))
                    .append("/-").append(value(change.linesRemoved()))
                    .append("; files ").append(list(change.modifiedFiles())).append('\n');
        }
        if (orientation.currentChange().isEmpty()) output.append("- unavailable\n");
        output.append('\n');

        output.append("RECENT ENGINEERING HISTORY\n");
        for (var change : orientation.recentEngineeringHistory()) {
            output.append("- Run ").append(value(change.runId()))
                    .append(": ").append(value(change.status()))
                    .append("; completed=").append(value(change.completedAt()))
                    .append("; outcome=").append(value(change.outcome())).append('\n');
        }
        if (orientation.recentEngineeringHistory().isEmpty()) output.append("- unavailable\n");
        output.append('\n');

        var verification = orientation.verification();
        output.append("VERIFICATION\n")
                .append("Status: ").append(value(verification.status())).append('\n');
        lines(output, verification.evidence());
        output.append('\n');

        output.append("UNKNOWN / UNAVAILABLE\n");
        if (unknown.isEmpty()) {
            output.append("- No additional unavailable information was established by the inspected evidence.\n");
        } else {
            unknown.stream().distinct().forEach(item -> output.append("- ").append(item).append('\n'));
        }
        return output.toString().trim();
    }

    private static void lines(StringBuilder output, java.util.List<String> values) {
        if (values.isEmpty()) {
            output.append("- unavailable\n");
            return;
        }
        values.forEach(value -> output.append("- ").append(value).append('\n'));
    }

    private static String list(java.util.List<?> values) {
        return values == null || values.isEmpty() ? "unavailable" : values.toString();
    }

    private static String value(Object value) {
        return value == null || value.toString().isBlank() ? "unavailable" : value.toString();
    }
}
