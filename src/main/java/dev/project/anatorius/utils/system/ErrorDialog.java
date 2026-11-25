package dev.project.anatorius.utils.system;

import javax.swing.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ErrorDialog {
    private static final AtomicBoolean SHOWING = new AtomicBoolean(false);

    private ErrorDialog() {
    }

    public static void registerGlobalHandler(String appName) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("[Anatorius] Uncaught exception on thread '" + thread.getName() + "':");
            throwable.printStackTrace();
            debugTrace("E", "ErrorDialog.registerGlobalHandler", "[DEBUG] default uncaught exception handler invoked: " + throwable.getClass().getName());
            String message = "A fatal error occurred while running " + appName + ".";
            showFatalError(appName + " - Fatal Error", message, throwable, true);
        });
    }

    public static void showFatalError(String title, String message, Throwable throwable, boolean exitAfterClose) {
        debugTrace("A", "ErrorDialog.showFatalError", "[DEBUG] showFatalError entered; exitAfterClose=" + exitAfterClose + "; throwable=" + (throwable == null ? "null" : throwable.getClass().getName()));
        String details = buildDetails(throwable);
        showSwingDialog(title, message, details, exitAfterClose);
    }

    public static void showFatalMessage(String title, String message, String details, boolean exitAfterClose) {
        debugTrace("A", "ErrorDialog.showFatalMessage", "[DEBUG] showFatalMessage entered; exitAfterClose=" + exitAfterClose);
        showSwingDialog(title, message, details, exitAfterClose);
    }

    private static void showSwingDialog(String title, String message, String details, boolean exitAfterClose) {
        debugTrace("B", "ErrorDialog.showSwingDialog", "[DEBUG] showSwingDialog entry; showing=" + SHOWING.get() + "; title=" + title);
        if (!SHOWING.compareAndSet(false, true)) {
            debugTrace("B", "ErrorDialog.showSwingDialog", "[DEBUG] dialog suppressed because SHOWING was already true");
            if (exitAfterClose) {
                Runtime.getRuntime().halt(1);
            }
            return;
        }

        try {
            String fullMessage = message;
            if (details != null && !details.isBlank()) {
                fullMessage += "\n\n" + truncate(details, 2000);
            }

            final String finalMessage = fullMessage;

            debugTrace("B", "ErrorDialog.showSwingDialog", "[DEBUG] before invokeAndWait; messageLength=" + finalMessage.length());
            SwingUtilities.invokeAndWait(() -> {
                debugTrace("C", "ErrorDialog.showSwingDialog.invokeAndWait", "[DEBUG] invokeAndWait entered on thread=" + Thread.currentThread().getName());
                JFrame frame = new JFrame();
                frame.setAlwaysOnTop(true);
                frame.setVisible(true);
                frame.toFront();

                debugTrace("C", "ErrorDialog.showSwingDialog.invokeAndWait", "[DEBUG] before JOptionPane.showMessageDialog");
                JOptionPane.showMessageDialog(
                        frame,
                        finalMessage,
                        title,
                        JOptionPane.ERROR_MESSAGE
                );

                debugTrace("C", "ErrorDialog.showSwingDialog.invokeAndWait", "[DEBUG] after JOptionPane.showMessageDialog");
                frame.dispose();
            });
            debugTrace("C", "ErrorDialog.showSwingDialog", "[DEBUG] invokeAndWait returned normally");
        } catch (Throwable ignored) {
            debugTrace("D", "ErrorDialog.showSwingDialog", "[DEBUG] showSwingDialog caught throwable=" + ignored.getClass().getName() + ":" + ignored.getMessage());
        } finally {
            SHOWING.set(false);
            debugTrace("D", "ErrorDialog.showSwingDialog", "[DEBUG] showSwingDialog finally; exitAfterClose=" + exitAfterClose);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n...";
    }

    private static String buildDetails(Throwable throwable) {
        if (throwable == null) {
            return "No additional details were provided.";
        }

        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        throwable.printStackTrace(printWriter);
        printWriter.flush();
        return writer.toString();
    }

    public static void debugTrace(String hypothesisId, String location, String msg) {
        try {
            String url = "http://127.0.0.1:7777/event";
            String sessionId = "error-popup-crash";
            try {
                for (String line : Files.readAllLines(Path.of(".dbg", "error-popup-crash.env"))) {
                    if (line.startsWith("DEBUG_SERVER_URL=")) {
                        url = line.substring("DEBUG_SERVER_URL=".length()).trim();
                    } else if (line.startsWith("DEBUG_SESSION_ID=")) {
                        sessionId = line.substring("DEBUG_SESSION_ID=".length()).trim();
                    }
                }
            } catch (Exception ignored) {
            }

            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(300);
            connection.setReadTimeout(300);
            connection.setRequestProperty("Content-Type", "application/json");
            String body = "{\"sessionId\":\"" + escapeJson(sessionId) + "\",\"runId\":\"pre\",\"hypothesisId\":\"" + escapeJson(hypothesisId) + "\",\"location\":\"" + escapeJson(location) + "\",\"msg\":\"" + escapeJson(msg) + "\",\"data\":{\"thread\":\"" + escapeJson(Thread.currentThread().getName()) + "\"},\"ts\":" + System.currentTimeMillis() + "}";
            connection.getOutputStream().write(body.getBytes());
            connection.getOutputStream().flush();
            connection.getResponseCode();
            connection.disconnect();
        } catch (Exception ignored) {
        }
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
