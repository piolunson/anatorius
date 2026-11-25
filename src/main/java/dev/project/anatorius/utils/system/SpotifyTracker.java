package dev.project.anatorius.utils.system;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.ptr.IntByReference;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SpotifyTracker {
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    private static volatile String artist;
    private static volatile String song;
    private static volatile boolean started;
    private static volatile boolean broken;

    private SpotifyTracker() {}

    public static String getArtist() {
        start();
        return artist;
    }

    public static String getSong() {
        start();
        return song;
    }

    private static void start() {
        if (started || !WINDOWS) return;
        synchronized (SpotifyTracker.class) {
            if (started) return;
            started = true;
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Anatorius Spotify");
                thread.setDaemon(true);
                return thread;
            });
            executor.scheduleWithFixedDelay(SpotifyTracker::poll, 0, 1, TimeUnit.SECONDS);
        }
    }

    private static void poll() {
        if (broken) return;
        try {
            String title = findTitle();
            int split = title == null ? -1 : title.indexOf(" - ");
            if (split <= 0) {
                artist = null;
                song = null;
            } else {
                artist = title.substring(0, split).trim();
                song = title.substring(split + 3).trim();
            }
        } catch (Throwable throwable) {
            broken = true;
            artist = null;
            song = null;
        }
    }

    private static String findTitle() {
        Set<Long> pids = new HashSet<>();
        ProcessHandle.allProcesses().forEach(process -> process.info().command().ifPresent(command -> {
            String lower = command.toLowerCase();
            if (lower.endsWith("\\spotify.exe") || lower.endsWith("/spotify.exe") || lower.equals("spotify.exe")) pids.add(process.pid());
        }));
        if (pids.isEmpty()) return null;

        String[] found = {null};
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            IntByReference pid = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
            if (!pids.contains((long) pid.getValue())) return true;
            int length = User32.INSTANCE.GetWindowTextLength(hwnd);
            if (length <= 0) return true;
            char[] buffer = new char[length + 1];
            User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.length);
            String title = Native.toString(buffer).trim();
            if (title.contains(" - ") && !title.startsWith("GDI+")) {
                found[0] = title;
                return false;
            }
            return true;
        }, null);
        return found[0];
    }
}
