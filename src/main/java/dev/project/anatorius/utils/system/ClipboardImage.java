package dev.project.anatorius.utils.system;

import net.minecraft.util.Util;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ClipboardImage {
    private ClipboardImage() {}

    public static boolean copy(File png) {
        String path = png.getAbsolutePath();
        try {
            return switch (Util.getOperatingSystem()) {
                case WINDOWS -> run(List.of("powershell", "-NoProfile", "-NonInteractive", "-STA", "-Command",
                        "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; "
                                + "$i = [System.Drawing.Image]::FromFile('" + path.replace("'", "''") + "'); "
                                + "[System.Windows.Forms.Clipboard]::SetImage($i); $i.Dispose()"), null);
                case OSX -> run(List.of("osascript", "-e",
                        "set the clipboard to (read (POSIX file \"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\") as «class PNGf»)"), null);
                default -> run(List.of("wl-copy", "--type", "image/png"), png)
                        || run(List.of("xclip", "-selection", "clipboard", "-t", "image/png", "-i", path), null);
            };
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean run(List<String> command, File stdin) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            if (stdin != null) builder.redirectInput(stdin);
            Process process = builder.start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
