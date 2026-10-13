package com.engineos.ui;

public final class EngineeringRequestValidator {
    public static final String MARKER = "ENGINEOS_REQUEST_V1";

    private EngineeringRequestValidator() {
    }

    public static boolean isValid(String request) {
        return request != null && !request.isBlank();
    }
}
