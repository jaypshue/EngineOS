package com.engineos;

import com.engineos.application.EngineOsService;
import com.engineos.application.EngineOsServiceConfiguration;
import com.engineos.application.HttpEngineOsService;
import com.engineos.ui.DesktopDiagnostics;
import com.engineos.ui.ObservationWindow;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.net.URI;

/**
 * EngineOS desktop client. The desktop deliberately contains no service
 * implementation or HTTP server; execution is owned by ServiceHostApplication.
 */
public final class EngineOsApplication {
    private EngineOsApplication() {
    }

    public static void main(String[] args) {
        DesktopDiagnostics.installUncaughtExceptionHandler();
        DesktopDiagnostics.stage("desktop-started");
        URI serviceUri = serviceUri();
        var observationsRoot = Path.of("observations").toAbsolutePath().normalize();
        EngineOsService engineOsService = new HttpEngineOsService(serviceUri);
        SwingUtilities.invokeLater(() -> new ObservationWindow(observationsRoot, engineOsService).show());
    }

    private static URI serviceUri() {
        return EngineOsServiceConfiguration.desktopServiceUri();
    }
}
