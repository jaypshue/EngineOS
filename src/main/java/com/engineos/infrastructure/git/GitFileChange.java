package com.engineos.infrastructure.git;

public record GitFileChange(
        String path,
        String originalPath,
        String indexStatus,
        String workTreeStatus) {
    public boolean staged() {
        return indexStatus != null && !indexStatus.isBlank() && !indexStatus.equals(" ");
    }

    public boolean unstaged() {
        return workTreeStatus != null && !workTreeStatus.isBlank() && !workTreeStatus.equals(" ");
    }
}
