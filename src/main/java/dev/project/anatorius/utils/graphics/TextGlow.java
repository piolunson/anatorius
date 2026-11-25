package dev.project.anatorius.utils.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TextGlow implements IMinecraft {
    private static final int PAGE_SIZE = 1024;
    private static final int LINE = 9;
    private static final float BASELINE = 7.0f;
    private static final float GAIN = 2.4f;

    private record Config(boolean custom, int radius, int scale) {}
    private record Cell(Page page, int u, int v, int width, int height, int pad) {}

    private static final class Page {
        final Identifier id;
        final NativeImage image;
        final NativeImageBackedTexture texture;
        int x, y, rowHeight;
        boolean dirty;

        Page(int index) {
            id = Identifier.of("anatorius", "text_glow/" + index + "_" + System.nanoTime());
            image = new NativeImage(NativeImage.Format.RGBA, PAGE_SIZE, PAGE_SIZE, true);
            texture = new NativeImageBackedTexture(id::toString, image) {
                {
                    this.sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
                }
            };
            mc.getTextureManager().registerTexture(id, texture);
        }

        int[] place(int w, int h) {
            if (x + w > PAGE_SIZE) {
                x = 0;
                y += rowHeight + 1;
                rowHeight = 0;
            }
            if (y + h > PAGE_SIZE || w > PAGE_SIZE) return null;
            int[] at = {x, y};
            x += w + 1;
            rowHeight = Math.max(rowHeight, h);
            return at;
        }
    }

    private static Config config;
    private static final List<Page> PAGES = new ArrayList<>();
    private static final Map<Long, Cell> CELLS = new HashMap<>();

    private TextGlow() {
    }

    public static void draw(DrawContext context, String text, Color color, int radius, float strength) {
        if (text.isEmpty() || strength <= 0.0f || mc.getWindow() == null) return;
        Config wanted = new Config(Anatorius.FONT_MANAGER.customFont(), Math.max(1, Math.min(4, radius)),
                Math.max(1, (int) Math.round(mc.getWindow().getScaleFactor())));
        if (!wanted.equals(config)) reset(wanted);

        int alpha = Math.min(255, Math.round(strength * 255.0f));
        int argb = (alpha << 24) | (color.getRGB() & 0xFFFFFF);
        boolean uploaded = false;
        int start = 0;
        int x = 0;
        while (start < text.length()) {
            int codePoint = text.codePointAt(start);
            int end = start + Character.charCount(codePoint);
            int next = Anatorius.FONT_MANAGER.getWidth(text.substring(0, end));
            int advance = next - x;
            if (advance > 0 && !Character.isWhitespace(codePoint)) {
                Cell cell = cell(codePoint, advance);
                if (cell != null) {
                    if (cell.page().dirty) {
                        cell.page().texture.upload();
                        cell.page().dirty = false;
                        uploaded = true;
                    }
                    int scale = config.scale();
                    context.drawTexture(RenderPipelines.GUI_TEXTURED, cell.page().id, x - cell.pad(), -cell.pad(),
                            cell.u(), cell.v(), cell.width() / scale, cell.height() / scale, cell.width(), cell.height(), PAGE_SIZE, PAGE_SIZE, argb);
                }
            }
            x = next;
            start = end;
        }
        if (uploaded && AUTO_LOG) Anatorius.LOGGER.info("[glow] glyph cells={} pages={}", CELLS.size(), PAGES.size());
    }

    public static volatile boolean AUTO_LOG;

    public static String stats() {
        return "cells=" + CELLS.size() + " pages=" + PAGES.size() + " config=" + config;
    }

    private static void reset(Config wanted) {
        for (Page page : PAGES) mc.getTextureManager().destroyTexture(page.id);
        PAGES.clear();
        CELLS.clear();
        config = wanted;
    }

    private static Cell cell(int codePoint, int advance) {
        long key = ((long) codePoint << 8) | (advance & 0xFF);
        Cell cell = CELLS.get(key);
        if (cell != null) return cell;

        int scale = config.scale();
        float sigma = (0.5f + config.radius() * 0.55f) * scale;
        int pad = (int) Math.ceil(3.0f * sigma / scale) + 1;
        int w = (advance + 2 * pad) * scale;
        int h = (LINE + 2 * pad) * scale;

        float[] mask = rasterize(codePoint, advance, pad, scale, w, h);
        blur(mask, w, h, sigma);

        Page page = PAGES.isEmpty() ? null : PAGES.get(PAGES.size() - 1);
        int[] at = page == null ? null : page.place(w, h);
        if (at == null) {
            page = new Page(PAGES.size());
            PAGES.add(page);
            at = page.place(w, h);
            if (at == null) return null;
        }
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int a = Math.min(255, Math.round(Math.min(1.0f, mask[py * w + px] * GAIN) * 255.0f));
                page.image.setColorArgb(at[0] + px, at[1] + py, (a << 24) | 0xFFFFFF);
            }
        }
        page.dirty = true;
        cell = new Cell(page, at[0], at[1], w, h, pad);
        CELLS.put(key, cell);
        return cell;
    }

    private static float[] rasterize(int codePoint, int advance, int pad, int scale, int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(Color.WHITE);

        Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(7.0f * scale / 0.72f));
        String s = new String(Character.toChars(codePoint));
        GlyphVector vector = font.createGlyphVector(g.getFontRenderContext(), s);
        Rectangle2D bounds = vector.getVisualBounds();
        double target = Math.max(1, advance - 1) * scale;
        double sx = bounds.getWidth() > 0.0 ? Math.max(0.55, Math.min(1.6, target / bounds.getWidth())) : 1.0;
        AffineTransform transform = new AffineTransform();
        double left = pad * scale + (target - bounds.getWidth() * sx) * 0.5 - bounds.getX() * sx;
        transform.translate(left, (pad + BASELINE) * scale);
        transform.scale(sx, 1.0);
        g.fill(transform.createTransformedShape(vector.getOutline()));
        g.dispose();

        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        float[] mask = new float[w * h];
        for (int i = 0; i < pixels.length; i++) mask[i] = (pixels[i] >>> 24) / 255.0f;
        return mask;
    }

    private static void blur(float[] data, int w, int h, float sigma) {
        int r = Math.max(1, (int) Math.ceil(3.0f * sigma));
        float[] kernel = new float[2 * r + 1];
        float sum = 0.0f;
        for (int i = -r; i <= r; i++) {
            kernel[i + r] = (float) Math.exp(-(i * i) / (2.0 * sigma * sigma));
            sum += kernel[i + r];
        }
        for (int i = 0; i < kernel.length; i++) kernel[i] /= sum;

        float[] tmp = new float[data.length];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float acc = 0.0f;
                for (int k = -r; k <= r; k++) {
                    int xx = x + k;
                    if (xx >= 0 && xx < w) acc += data[row + xx] * kernel[k + r];
                }
                tmp[row + x] = acc;
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                float acc = 0.0f;
                for (int k = -r; k <= r; k++) {
                    int yy = y + k;
                    if (yy >= 0 && yy < h) acc += tmp[yy * w + x] * kernel[k + r];
                }
                data[y * w + x] = acc;
            }
        }
    }
}
