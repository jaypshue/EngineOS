package com.engineos.observation;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public final class ObservationAttachmentPreparer {
    private final ObservationPackageService packages;

    public ObservationAttachmentPreparer(ObservationPackageService packages) {
        this.packages = packages;
    }

    public PreparationResult prepare(CompletedObservation observation) {
        return prepare(observation, ignored -> {
        });
    }

    public PreparationResult prepare(CompletedObservation observation, Consumer<String> progress) {
        Path zip = packages.createZip(observation);
        progress.accept("Sending/preparing attachment...");
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new FileListTransferable(List.of(new File(zip.toString()))), null);
        ChatGptWindowActivator.ActivationResult activation = ChatGptWindowActivator.activate();
        return new PreparationResult(zip, activation.activated(), activation.detail());
    }

    public record PreparationResult(Path zip, boolean chatGptForegrounded, String foregroundDetail) {
    }
}
