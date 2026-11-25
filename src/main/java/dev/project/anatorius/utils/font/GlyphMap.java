package dev.project.anatorius.utils.font;

import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import lombok.Getter;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.project.anatorius.mixins.accessors.NativeImageAccessor;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class GlyphMap implements IMinecraft {
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    private NativeImageBackedTexture texture;
    private final Identifier id = Identifier.of("anatorius", "font/page_" + NEXT_ID.getAndIncrement());

    private final Font[] fonts;
    private int width;
    private int height;

    private final char include;
    private final char exclude;

    private final int padding;
    private final Char2ObjectArrayMap<Glyph> glyphs = new Char2ObjectArrayMap<>();

    boolean generated = false;

    private final int ascent;

    public GlyphMap(Font[] fonts, int ascent, char include, char exclude, int padding) {
        this.ascent = ascent;
        this.fonts = fonts;
        this.include = include;
        this.exclude = exclude;
        this.padding = padding;
    }

    public void generate() {
        synchronized (this) {
            privateGenerate();
        }
    }

    private void privateGenerate() {
        if (generated) return;

        int range = exclude - include - 1;
        int charsVert = (int) (Math.ceil(Math.sqrt(range)) * 1.5);
        int generatedChars = 0;
        int charNX = 0;
        int maxX = 0, maxY = 0;
        int currentX = 0, currentY = 0;
        int currentRowMaxY = 0;

        List<Glyph> glyphs = new ArrayList<>();

        AffineTransform affineTransform = new AffineTransform();
        FontRenderContext context = new FontRenderContext(affineTransform, true, true);

        while (generatedChars <= range) {
            char currentChar = (char) (include + generatedChars);
            Font font = getFontForGlyph(currentChar);
            Rectangle2D stringBounds = font.getStringBounds(String.valueOf(currentChar), context);

            int width = (int) Math.ceil(stringBounds.getWidth());
            int height = (int) Math.ceil(stringBounds.getHeight());
            generatedChars++;
            maxX = Math.max(maxX, currentX + width);
            maxY = Math.max(maxY, currentY + height);

            if (charNX >= charsVert) {
                currentX = 0;
                currentY += currentRowMaxY + padding;
                charNX = 0;
                currentRowMaxY = 0;
            }

            currentRowMaxY = Math.max(currentRowMaxY, height);
            glyphs.add(new Glyph(this, currentX, currentY, width, height, currentChar));
            currentX += width + padding;
            charNX++;
        }

        BufferedImage bufferedImage = new BufferedImage(Math.max(maxX + padding, 1), Math.max(maxY + padding, 1), BufferedImage.TYPE_INT_ARGB);
        width = bufferedImage.getWidth();
        height = bufferedImage.getHeight();

        Graphics2D graphics = bufferedImage.createGraphics();

        graphics.setColor(new Color(255, 255, 255, 0));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.WHITE);

        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        for (Glyph glyph : glyphs) {
            graphics.setFont(getFontForGlyph(glyph.value()));
            graphics.drawString(String.valueOf(glyph.value()), glyph.u(), glyph.v() + ascent);
            this.glyphs.put(glyph.value(), glyph);
        }

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, bufferedImage.getWidth(), bufferedImage.getHeight(), false);
        IntBuffer backingBuffer = MemoryUtil.memIntBuffer(((NativeImageAccessor) (Object) image).getPointer(), image.getWidth() * image.getHeight());

        int[] pixels = bufferedImage.getRGB(0, 0, width, height, null, 0, width);
        for (int argb : pixels) {
            backingBuffer.put((argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16));
        }

        graphics.dispose();

        NativeImageBackedTexture texture = new NativeImageBackedTexture(id::toString, image) {
            {
                this.sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            }
        };
        mc.getTextureManager().registerTexture(id, texture);
        this.texture = texture;

        generated = true;
    }

    public Glyph getGlyph(char character) {
        synchronized (this) {
            if (!generated) privateGenerate();
            return glyphs.get(character);
        }
    }

    public void destroy() {
        synchronized (this) {
            generated = false;

            if (texture != null) mc.getTextureManager().destroyTexture(id);
            texture = null;
            glyphs.clear();

            this.width = -1;
            this.height = -1;
        }
    }

    public PageTexture view() {
        synchronized (this) {
            if (!generated || texture == null) return null;
            return new PageTexture(texture.getGlTextureView(), texture.getSampler());
        }
    }

    public record PageTexture(com.mojang.blaze3d.textures.GpuTextureView view, net.minecraft.client.gl.GpuSampler sampler) {
    }

    public boolean contains(char c) {
        return c >= include && c < exclude;
    }

    private Font getFontForGlyph(char c) {
        for (Font font1 : this.fonts) {
            if (font1.canDisplay(c)) {
                return font1;
            }
        }

        return this.fonts[0];
    }
}
