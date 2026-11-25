package dev.project.anatorius.utils.font;

import com.google.common.base.Preconditions;
import dev.project.anatorius.utils.IMinecraft;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.texture.TextureSetup;
import dev.project.anatorius.utils.font.GlyphMap.PageTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.TextColor;
import net.minecraft.text.TextVisitFactory;
import org.joml.Matrix4f;

import java.awt.*;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

public class FontRenderer implements Closeable, IMinecraft {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final List<GlyphMap> RETIRED = new ArrayList<>();
    private static boolean retireHook;

    private final ObjectList<GlyphMap> maps = new ObjectArrayList<>();
    @SuppressWarnings("unchecked")
    private final ObjectList<GlyphMap>[] variantMaps = new ObjectList[]{new ObjectArrayList<>(), new ObjectArrayList<>(), new ObjectArrayList<>(), new ObjectArrayList<>()};
    private final Font[][] variantFonts = new Font[4][];
    private final Font[] baseFonts;
    private Font[] fonts;
    private final float size;
    private final int charsPerPage;
    private final int padding;
    private int multiplier;
    private float topOffset;

    public FontRenderer(Font[] fonts, float size) {
        this(fonts, size, 256, 5);
    }

    public FontRenderer(Font[] fonts, float size, int charactersPerPage, int paddingBetweenCharacters) {
        Preconditions.checkArgument(size > 0, "size <= 0");
        Preconditions.checkArgument(fonts.length > 0, "fonts.length <= 0");
        Preconditions.checkArgument(charactersPerPage > 4, "Unreasonable charactersPerPage count");
        Preconditions.checkArgument(paddingBetweenCharacters > 0, "paddingBetweenCharacters <= 0");
        this.baseFonts = fonts.clone();
        this.size = size;
        this.charsPerPage = charactersPerPage;
        this.padding = paddingBetweenCharacters;
    }

    public void drawString(DrawContext context, String text, float x, float y, int color, boolean shadow) {
        drawGui(context, visitor -> TextVisitFactory.visitFormatted(clean(text), Style.EMPTY, visitor), x, y, color, shadow, Float.NaN);
    }

    public void drawText(DrawContext context, OrderedText text, float x, float y, int color, boolean shadow) {
        drawGui(context, text::accept, x, y, color, shadow, Float.NaN);
    }

    public void drawStringWithShadow(DrawContext context, String text, float x, float y, int color, float shadowOffset) {
        drawGui(context, visitor -> TextVisitFactory.visitFormatted(clean(text), Style.EMPTY, visitor), x, y, color, false, shadowOffset);
    }

    public void drawTextWithShadow(DrawContext context, OrderedText text, float x, float y, int color, float shadowOffset) {
        drawGui(context, text::accept, x, y, color, false, shadowOffset);
    }

    private void drawGui(DrawContext context, Source source, float x, float y, int color, boolean darken, float shadowOffset) {
        drawGui(context.state, new org.joml.Matrix3x2f(context.getMatrices()), context.scissorStack.peekLast(), source, x, y, color, darken, shadowOffset);
    }

    public void drawState(net.minecraft.client.gui.render.state.GuiRenderState state, org.joml.Matrix3x2fc matrix, ScreenRect scissor,
                          OrderedText text, float x, float y, int color, float shadowOffset) {
        drawGui(state, new org.joml.Matrix3x2f(matrix), scissor, text::accept, x, y, color, false, shadowOffset);
    }

    private void drawGui(net.minecraft.client.gui.render.state.GuiRenderState state, org.joml.Matrix3x2f base, ScreenRect scissor, Source source,
                         float x, float y, int color, boolean darken, float shadowOffset) {
        if (alpha(color) == 0) return;
        ensureScale();
        int m = multiplier;

        org.joml.Matrix3x2f pose = base
                .translate(Math.round(x * m) / (float) m, Math.round((y + topOffset) * m) / (float) m)
                .scale(1.0f / m, 1.0f / m);

        java.util.Map<GlyphMap, Batch> text = new java.util.LinkedHashMap<>(2);
        emit(source, text, 0, 0, color, darken);
        if (!Float.isNaN(shadowOffset) && shadowOffset != 0.0f) {
            int offset = Math.round(shadowOffset * m);
            java.util.Map<GlyphMap, Batch> shadow = new java.util.LinkedHashMap<>(2);
            emit(source, shadow, offset, offset, color, true);
            if (text.size() == 1 && shadow.size() == 1 && text.keySet().equals(shadow.keySet())) {
                shadow.values().iterator().next().append(text.values().iterator().next());
                addRuns(state, pose, scissor, shadow);
                return;
            }
            addRuns(state, pose, scissor, shadow);
            state.goUpLayer();
        }
        addRuns(state, pose, scissor, text);
    }

    private static void addRuns(net.minecraft.client.gui.render.state.GuiRenderState state, org.joml.Matrix3x2f pose, ScreenRect scissor, java.util.Map<GlyphMap, Batch> batches) {
        for (java.util.Map.Entry<GlyphMap, Batch> entry : batches.entrySet()) {
            Batch batch = entry.getValue();
            if (batch.count == 0) continue;
            ScreenRect local = new ScreenRect(batch.minX, batch.minY, Math.max(1, batch.maxX - batch.minX), Math.max(1, batch.maxY - batch.minY));
            ScreenRect bounds = local.transformEachVertex(pose);
            if (scissor != null) bounds = scissor.intersection(bounds);
            if (bounds == null) continue;
            PageTexture page = entry.getKey().view();
            if (page == null) continue;
            state.addSimpleElement(new GlyphRun(TextureSetup.of(page.view(), page.sampler()), pose,
                    batch.quads, batch.colors, batch.count, scissor, bounds));
        }
    }

    private void emit(Source source, java.util.Map<GlyphMap, Batch> batches, int dx, int dy, int color, boolean darken) {
        int[] cursor = {dx};
        source.visit((index, style, codePoint) -> {
            Glyph glyph = locateGlyph(codePoint, style);
            if (glyph == null) return true;
            if (!Character.isWhitespace(codePoint)) {
                GlyphMap page = glyph.parent();
                Batch batch = batches.computeIfAbsent(page, p -> new Batch());
                float pw = page.getWidth(), ph = page.getHeight();
                batch.add(cursor[0], dy, cursor[0] + glyph.width(), dy + glyph.height(),
                        glyph.u() / pw, glyph.v() / ph, (glyph.u() + glyph.width()) / pw, (glyph.v() + glyph.height()) / ph,
                        glyphColor(style, color, darken));
            }
            cursor[0] += glyph.width();
            return true;
        });
    }

    private static final class Batch {
        float[] quads = new float[GlyphRun.STRIDE * 16];
        int[] colors = new int[16];
        int count;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        void add(int x1, int y1, int x2, int y2, float u1, float v1, float u2, float v2, int color) {
            int o = count * GlyphRun.STRIDE;
            if (o + GlyphRun.STRIDE > quads.length) quads = java.util.Arrays.copyOf(quads, quads.length * 2);
            if (count >= colors.length) colors = java.util.Arrays.copyOf(colors, colors.length * 2);
            quads[o] = x1;
            quads[o + 1] = y1;
            quads[o + 2] = x2;
            quads[o + 3] = y2;
            quads[o + 4] = u1;
            quads[o + 5] = v1;
            quads[o + 6] = u2;
            quads[o + 7] = v2;
            colors[count] = color;
            count++;
            minX = Math.min(minX, x1);
            minY = Math.min(minY, y1);
            maxX = Math.max(maxX, x2);
            maxY = Math.max(maxY, y2);
        }

        void append(Batch other) {
            for (int i = 0; i < other.count; i++) {
                int o = i * GlyphRun.STRIDE;
                float[] q = other.quads;
                add((int) q[o], (int) q[o + 1], (int) q[o + 2], (int) q[o + 3], q[o + 4], q[o + 5], q[o + 6], q[o + 7], other.colors[i]);
            }
        }
    }

    public void drawString(Matrix4f matrix, VertexConsumerProvider provider, String text, float x, float y, int color, boolean shadow, boolean seeThrough, int light) {
        drawWorld(matrix, provider, visitor -> TextVisitFactory.visitFormatted(clean(text), Style.EMPTY, visitor), x, y, color, shadow, seeThrough, light);
    }

    public void drawText(Matrix4f matrix, VertexConsumerProvider provider, OrderedText text, float x, float y, int color, boolean shadow, boolean seeThrough, int light) {
        drawWorld(matrix, provider, text::accept, x, y, color, shadow, seeThrough, light);
    }

    public void drawString(Matrix4f matrix, VertexConsumerProvider provider, String text, float x, float y, int color, boolean shadow) {
        drawString(matrix, provider, text, x, y, color, shadow, true, FULL_BRIGHT);
    }

    private void drawWorld(Matrix4f matrix, VertexConsumerProvider provider, Source source, float x, float y, int color, boolean shadow, boolean seeThrough, int light) {
        if (alpha(color) == 0) return;
        ensureScale();
        float scale = 1.0f / multiplier;

        float[] cursor = {x};
        source.visit((index, style, codePoint) -> {
            Glyph glyph = locateGlyph(codePoint, style);
            if (glyph == null) return true;
            float w = glyph.width() * scale;
            if (!Character.isWhitespace(codePoint)) {
                GlyphMap page = glyph.parent();
                VertexConsumer consumer = provider.getBuffer(seeThrough ? RenderLayers.textSeeThrough(page.getId()) : RenderLayers.text(page.getId()));
                float x1 = cursor[0], y1 = y + topOffset, x2 = x1 + w, y2 = y1 + glyph.height() * scale;
                float u1 = glyph.u() / (float) page.getWidth(), v1 = glyph.v() / (float) page.getHeight();
                float u2 = (glyph.u() + glyph.width()) / (float) page.getWidth(), v2 = (glyph.v() + glyph.height()) / (float) page.getHeight();
                int argb = glyphColor(style, color, shadow);
                consumer.vertex(matrix, x1, y1, 0.0f).color(argb).texture(u1, v1).light(light);
                consumer.vertex(matrix, x1, y2, 0.0f).color(argb).texture(u1, v2).light(light);
                consumer.vertex(matrix, x2, y2, 0.0f).color(argb).texture(u2, v2).light(light);
                consumer.vertex(matrix, x2, y1, 0.0f).color(argb).texture(u2, v1).light(light);
            }
            cursor[0] += w;
            return true;
        });
    }

    public float getTextWidth(String text) {
        return width(visitor -> TextVisitFactory.visitFormatted(clean(text), Style.EMPTY, visitor));
    }

    public float getTextWidth(OrderedText text) {
        return width(text::accept);
    }

    private float width(Source source) {
        ensureScale();
        int[] width = {0};
        source.visit((index, style, codePoint) -> {
            Glyph glyph = locateGlyph(codePoint, style);
            if (glyph != null) width[0] += glyph.width();
            return true;
        });
        return width[0] / (float) multiplier;
    }

    public float getHeight() {
        ensureScale();
        Glyph glyph = locateGlyph('A', Style.EMPTY);
        return glyph == null ? 0.0f : glyph.height() / (float) multiplier;
    }

    public static String stripControlCodes(String text) {
        if (text == null) return "";
        return text.indexOf('Â') < 0 ? text : text.replace("Â§", "§");
    }

    private static String clean(String text) {
        return stripControlCodes(text);
    }

    private static int alpha(int argb) {
        return argb >>> 24;
    }

    private static int glyphColor(Style style, int color, boolean shadow) {
        int argb = color;
        TextColor textColor = style.getColor();
        if (textColor != null) argb = (color & 0xFF000000) | (textColor.getRgb() & 0xFFFFFF);
        if (shadow) {
            int r = ((argb >> 16) & 0xFF) / 4, g = ((argb >> 8) & 0xFF) / 4, b = (argb & 0xFF) / 4;
            argb = (argb & 0xFF000000) | r << 16 | g << 8 | b;
        }
        return argb;
    }

    private void ensureScale() {
        int scale = Math.max(1, mc.getWindow() == null ? 1 : mc.getWindow().getScaleFactor());
        if (scale == multiplier && fonts != null) return;
        destroyPages();
        multiplier = scale;
        fonts = new Font[baseFonts.length];
        for (int i = 0; i < baseFonts.length; i++) fonts[i] = baseFonts[i].deriveFont(size * scale);

        java.awt.font.FontRenderContext frc = new java.awt.font.FontRenderContext(new java.awt.geom.AffineTransform(), true, true);
        float capHeight = (float) fonts[0].createGlyphVector(frc, "H").getVisualBounds().getHeight();
        topOffset = 3.5f + capHeight / scale / 2.0f - imageAscent(fonts[0]) / (float) scale;
    }

    static int imageAscent(Font font) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setFont(font);
            return graphics.getFontMetrics().getAscent();
        } finally {
            graphics.dispose();
        }
    }

    public float getCharWidth(int codePoint, Style style) {
        ensureScale();
        Glyph glyph = locateGlyph(codePoint, style);
        return glyph == null ? 0.0f : glyph.width() / (float) multiplier;
    }

    private Glyph locateGlyph(int codePoint, Style style) {
        if (codePoint < 0 || codePoint >= Character.MAX_VALUE) codePoint = '?';
        char glyph = (char) codePoint;
        int variant = style == null ? 0 : (style.isBold() ? 1 : 0) | (style.isItalic() ? 2 : 0);
        ObjectList<GlyphMap> pages = variantMaps[variant];
        for (GlyphMap map : pages) {
            if (map.contains(glyph)) return map.getGlyph(glyph);
        }
        int base = charsPerPage * (glyph / charsPerPage);
        GlyphMap map = new GlyphMap(variantFonts(variant), imageAscent(fonts[0]), (char) base, (char) Math.min(Character.MAX_VALUE, base + charsPerPage), padding);
        map.generate();
        pages.add(map);
        maps.add(map);
        return map.getGlyph(glyph);
    }

    private Font[] variantFonts(int variant) {
        if (variant == 0) return fonts;
        Font[] derived = variantFonts[variant];
        if (derived == null) {
            int extra = ((variant & 1) != 0 ? Font.BOLD : 0) | ((variant & 2) != 0 ? Font.ITALIC : 0);
            derived = new Font[fonts.length];
            for (int i = 0; i < fonts.length; i++) derived[i] = fonts[i].deriveFont(fonts[i].getStyle() | extra);
            variantFonts[variant] = derived;
        }
        return derived;
    }

    private void destroyPages() {
        for (ObjectList<GlyphMap> pages : variantMaps) pages.clear();
        java.util.Arrays.fill(variantFonts, null);
        if (maps.isEmpty()) return;
        synchronized (RETIRED) {
            if (!retireHook) {
                retireHook = true;
                net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(client -> releaseRetired());
            }
            RETIRED.addAll(maps);
        }
        maps.clear();
    }

    private static void releaseRetired() {
        List<GlyphMap> pages;
        synchronized (RETIRED) {
            if (RETIRED.isEmpty()) return;
            pages = new ArrayList<>(RETIRED);
            RETIRED.clear();
        }
        for (GlyphMap map : pages) map.destroy();
    }

    @Override
    public void close() {
        destroyPages();
        fonts = null;
    }

    @FunctionalInterface
    private interface Source {
        void visit(CharacterVisitor visitor);
    }
}
