package com.hisabx.update;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class UpdateInstallerLauncher {
    private static final Logger logger = LoggerFactory.getLogger(UpdateInstallerLauncher.class);

    private UpdateInstallerLauncher() {
    }

    public static void launchInstallerAndRestart(Path installerExe) {
        if (installerExe == null) {
            throw new IllegalArgumentException("installerExe is null");
        }

        try {
            String appDir = System.getProperty("user.dir");
            Path exeToStart = Path.of(appDir, "HisabX.exe");
            Path vbs = Path.of(appDir, "HisabX.vbs");
            Path restartTarget = Files.exists(exeToStart) ? exeToStart : vbs;

            String silentArgs = "/VERYSILENT /SUPPRESSMSGBOXES /NORESTART";

            String batName = "hisabx_update_" + UUID.randomUUID() + ".bat";
            Path bat = Path.of(System.getProperty("java.io.tmpdir"), batName);

            String script = "@echo off\r\n"
                    + "setlocal\r\n"
                    + "timeout /t 2 /nobreak >nul\r\n"
                    + "powershell -NoProfile -ExecutionPolicy Bypass -Command \"Start-Process -FilePath '" + escapeForPowerShell(installerExe.toString()) + "' -ArgumentList '" + silentArgs + "' -Verb RunAs -Wait\"\r\n"
                    + "timeout /t 2 /nobreak >nul\r\n"
                    + "if exist \"" + restartTarget.toString() + "\" (\r\n"
                    + "  start \"\" \"" + restartTarget.toString() + "\"\r\n"
                    + ")\r\n"
                    + "del \"%~f0\"\r\n";

            Files.writeString(bat, script, StandardCharsets.UTF_8);

            new ProcessBuilder("cmd.exe", "/c", bat.toString())
                    .inheritIO()
                    .start();

        } catch (IOException e) {
            logger.error("Failed to launch installer", e);
            throw new RuntimeException(e);
        }
    }

    private static String escapeForPowerShell(String s) {
        return s.replace("'", "''");
    }
}
