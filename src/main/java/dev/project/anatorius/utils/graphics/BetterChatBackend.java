package dev.project.anatorius.utils.graphics;

import dev.project.anatorius.modules.impl.miscellaneous.BetterChatModule;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.text.OrderedText;
import org.joml.Matrix3x2f;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public final class BetterChatBackend implements ChatHud.Backend {
    private static final Map<OrderedText, Long> FIRST_DRAWN = new WeakHashMap<>();
    private static final float SLIDE = 40.0f;

    private final ChatHud.Backend delegate;
    private final BetterChatModule module;
    private final boolean animate;
    private boolean poseSet;

    public BetterChatBackend(ChatHud.Backend delegate, BetterChatModule module) {
        this.delegate = delegate;
        this.module = module;
        this.animate = module.animation.getValue() && module.delay.getValue().intValue() > 0;
    }

    @Override
    public void updatePose(Consumer<Matrix3x2f> transformer) {
        if (poseSet) {
            delegate.updatePose(transformer);
            return;
        }
        poseSet = true;
        float offset = module.offset.getValue().floatValue();
        delegate.updatePose(pose -> {
            pose.translate(offset, 0.0f);
            transformer.accept(pose);
        });
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        if (x1 == -4 || x1 == -2) {
            switch (module.background.getValue()) {
                case "Clear" -> { return; }
                case "Custom" -> {
                    java.awt.Color custom = module.color.getColor();
                    float fade = (color >>> 24) / 255.0f / Math.max(0.01f, vanillaBackgroundAlpha());
                    int alpha = Math.round(custom.getAlpha() * Math.min(1.0f, fade));
                    color = alpha << 24 | (custom.getRGB() & 0xFFFFFF);
                }
                default -> {}
            }
        }
        delegate.fill(x1, y1, x2, y2, color);
    }

    @Override
    public boolean text(int y, float opacity, OrderedText text) {
        opacity *= module.textAlpha.getValue().floatValue() / 255.0f;
        if (!animate) return delegate.text(y, opacity, text);

        long now = System.currentTimeMillis();
        long start;
        synchronized (FIRST_DRAWN) {
            start = FIRST_DRAWN.computeIfAbsent(text, t -> now);
        }
        float progress = Math.min(1.0f, (now - start) / module.delay.getValue().floatValue());
        if (progress >= 1.0f) return delegate.text(y, opacity, text);

        float eased = 1.0f - (float) Math.pow(1.0f - progress, 3);
        float dx = -SLIDE * (1.0f - eased);
        delegate.updatePose(pose -> pose.translate(dx, 0.0f));
        boolean styled = delegate.text(y, opacity * eased, text);
        delegate.updatePose(pose -> pose.translate(-dx, 0.0f));
        return styled;
    }

    @Override
    public void indicator(int x1, int y1, int x2, int y2, float opacity, MessageIndicator indicator) {
        if (!module.noIndicators.getValue()) delegate.indicator(x1, y1, x2, y2, opacity, indicator);
    }

    @Override
    public void indicatorIcon(int left, int bottom, boolean forceDraw, MessageIndicator indicator, MessageIndicator.Icon icon) {
        if (!module.noIndicators.getValue()) delegate.indicatorIcon(left, bottom, forceDraw, indicator, icon);
    }

    private static float vanillaBackgroundAlpha() {
        return net.minecraft.client.MinecraftClient.getInstance().options.getTextBackgroundOpacity().getValue().floatValue();
    }
}
