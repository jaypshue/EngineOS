package com.engineos.ui;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

public final class EngineeringPartnerClipboard {
    @FunctionalInterface
    interface ClipboardTarget {
        void setText(String text);
    }

    private EngineeringPartnerClipboard() {
    }

    public static boolean copyToClipboard() {
        return copyToClipboard(EngineeringPartnerInstructions.TEXT);
    }

    public static boolean copyToClipboard(String text) {
        return copy(text, value -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(value), null));
    }

    static boolean copy(ClipboardTarget target) {
        return copy(EngineeringPartnerInstructions.TEXT, target);
    }

    static boolean copy(String text, ClipboardTarget target) {
        try {
            target.setText(text);
            return true;
        } catch (HeadlessException | IllegalStateException | SecurityException exception) {
            return false;
        }
    }
}
