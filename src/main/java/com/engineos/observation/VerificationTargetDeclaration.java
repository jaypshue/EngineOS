package com.engineos.observation;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record VerificationTargetDeclaration(
        Optional<VerificationTarget.Kind> requestedTarget,
        boolean explicit,
        String error) {
    private static final Pattern FIELD = Pattern.compile(
            "(?im)^\\s*Verification\\s+Target\\s*:\\s*(\\S.*)\\s*$");

    public VerificationTargetDeclaration {
        requestedTarget = requestedTarget == null ? Optional.empty() : requestedTarget;
        error = error == null ? "" : error;
    }

    public static VerificationTargetDeclaration parse(String request) {
        if (request == null) {
            return new VerificationTargetDeclaration(Optional.empty(), false, "");
        }
        Matcher matcher = FIELD.matcher(request);
        if (!matcher.find()) {
            return new VerificationTargetDeclaration(Optional.empty(), false, "");
        }
        String value = matcher.group(1).trim();
        for (var kind : VerificationTarget.Kind.values()) {
            if (kind.name().equalsIgnoreCase(value)) {
                return new VerificationTargetDeclaration(Optional.of(kind), true, "");
            }
        }
        return new VerificationTargetDeclaration(
                Optional.empty(),
                true,
                "Unsupported Verification Target: " + value);
    }
}
