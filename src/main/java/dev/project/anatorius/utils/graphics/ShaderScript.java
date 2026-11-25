package dev.project.anatorius.utils.graphics;

import dev.project.anatorius.Anatorius;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ShaderScript {
    public static final Identifier TEMPLATE = Identifier.of("anatorius", "post/chams_default_v");
    private static final String PREFIX = "post/chams_script_";
    private static final String BEGIN = "//#paint-begin";
    private static final String END = "//#paint-end";

    private static final String EXAMPLE = """
            // Anatorius custom shader: Shaders module -> Shader: Custom.
            // Edit and save this file; it reloads by itself while the game runs.
            //
            // paint() gets the colour Anatorius would draw at this pixel and returns the one to draw instead.
            //   base  rgb + alpha of the pixel (fill inside the entity, or the outline/glow band)
            //   edge  true on the outline/glow band, false on the fill
            //
            // You can use: TIME (seconds, scaled by Speed), RES (screen size in px), UV (0..1 screen position),
            // FILL and EDGE (your Fill / Outline colors as vec3), gl_FragCoord, and any GLSL 330 function.
            vec4 paint(vec4 base, bool edge) {
                vec2 p = (2.0 * gl_FragCoord.xy - RES) / min(RES.x, RES.y);
                p += 0.6 * cos(2.5 * p.yx + TIME);
                float wave = abs(sin(TIME - p.x - p.y));
                vec3 tone = edge ? base.rgb : base.rgb / max(wave, 0.35);
                return vec4(tone, base.a);
            }
            """;

    private static String code;
    private static int version;
    private static int brokenVersion = -1;
    private static long lastModified = Long.MIN_VALUE;
    private static long lastPoll;

    private ShaderScript() {}

    public static Path file() {
        return Path.of(Anatorius.MOD_NAME, "Shaders", "paint.glsl");
    }

    public static Identifier currentId() {
        poll();
        if (code == null || version == brokenVersion) return null;
        return Identifier.of("anatorius", PREFIX + version);
    }

    public static boolean isScriptId(Identifier id) {
        return id != null && id.getNamespace().equals("anatorius") && id.getPath().startsWith(PREFIX);
    }

    public static String compose(String template) {
        if (template == null || code == null) return template;
        int begin = template.indexOf(BEGIN);
        int end = template.indexOf(END);
        if (begin < 0 || end < begin) return template;
        return template.substring(0, begin) + code + "\n" + template.substring(end + END.length());
    }

    public static void markBroken(Identifier id) {
        if (version == brokenVersion) return;
        brokenVersion = version;
        Anatorius.LOGGER.warn("[Anatorius] Script shader {} failed to compile; see the GL error above.", id);
        try {
            if (net.minecraft.client.MinecraftClient.getInstance().player != null) {
                Anatorius.CHAT_MANAGER.message("Custom shader (" + file() + ") has an error, so the default look is used. Fix it and save; the details are in the game log.", "shaders-script-error");
            }
        } catch (Throwable ignored) {
        }
    }

    private static void poll() {
        long now = System.currentTimeMillis();
        if (code != null && now - lastPoll < 1000L) return;
        lastPoll = now;
        try {
            Path path = file();
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, EXAMPLE, StandardCharsets.UTF_8);
            }
            long modified = Files.getLastModifiedTime(path).toMillis();
            if (modified == lastModified && code != null) return;
            lastModified = modified;
            String read = Files.readString(path, StandardCharsets.UTF_8);
            if (!read.equals(code)) {
                code = read;
                version++;
            }
        } catch (Throwable t) {
            if (code == null) code = EXAMPLE;
            Anatorius.LOGGER.warn("[Anatorius] could not read the Script shader: {}", t.toString());
        }
    }
}
