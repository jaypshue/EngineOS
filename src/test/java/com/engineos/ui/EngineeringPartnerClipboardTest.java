package com.engineos.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineeringPartnerClipboardTest {
    @Test
    void copiesTheSingleInstructionSource() {
        var copied = new String[1];

        assertTrue(EngineeringPartnerClipboard.copy(text -> copied[0] = text));
        assertEquals(EngineeringPartnerInstructions.TEXT, copied[0]);
    }

    @Test
    void reportsClipboardFailureWithoutPretendingItCopied() {
        assertFalse(EngineeringPartnerClipboard.copy(text -> {
            throw new IllegalStateException("clipboard unavailable");
        }));
    }

    @Test
    void copiesProvidedProjectOrientationPayload() {
        var copied = new String[1];
        String payload = "PROJECT ORIENTATION\n\nWHOLE REPOSITORY\nEngineOS";

        assertTrue(EngineeringPartnerClipboard.copy(payload, text -> copied[0] = text));
        assertEquals(payload, copied[0]);
    }
}
