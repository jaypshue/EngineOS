package com.engineos.observation;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

final class ChatGptWindowActivator {
    private ChatGptWindowActivator() {
    }

    static ActivationResult activate() {
        String script = """
                $window = Get-Process | Where-Object {
                    $_.MainWindowTitle -and $_.MainWindowTitle -match 'ChatGPT'
                } | Select-Object -First 1
                if ($null -eq $window) { exit 1 }
                $shell = New-Object -ComObject WScript.Shell
                if ($shell.AppActivate($window.Id)) { exit 0 } else { exit 1 }
                """;
        try {
            Process process = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new ActivationResult(false, "ChatGPT foregrounding timed out.");
            }
            int exitCode = process.exitValue();
            return exitCode == 0
                    ? new ActivationResult(true, "ChatGPT window brought to the foreground.")
                    : new ActivationResult(false, "No visible ChatGPT window was found.");
        } catch (IOException exception) {
            return new ActivationResult(false, "Unable to foreground ChatGPT: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ActivationResult(false, "ChatGPT foregrounding was interrupted.");
        }
    }

    record ActivationResult(boolean activated, String detail) {
    }
}
