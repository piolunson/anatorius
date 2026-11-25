package dev.project.anatorius.hud;

import dev.project.anatorius.utils.animations.Animation;
import dev.project.anatorius.utils.animations.Easing;
import dev.project.anatorius.utils.graphics.Renderer2D;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public class HudEditor {
    public static final float SNAP = 5.0f;

    private final Map<String, HudElement> elements = new LinkedHashMap<>();
    private final GuideManager guides = new GuideManager();

    private boolean editing;

    public HudElement register(HudElement element) {
        elements.put(element.getId(), element);
        return element;
    }

    public HudElement get(String id) {
        return elements.get(id);
    }

    public Collection<HudElement> getElementList() {
        return elements.values();
    }

    public void setEditing(boolean editing) {
        this.editing = editing;
    }

    public void clearFrame() {
        for (HudElement element : elements.values()) element.clearFrame();
    }

    public HudElement elementAt(double mouseX, double mouseY) {
        HudElement found = null;
        for (HudElement element : elements.values()) {
            if (!element.isRenderedThisFrame() || !element.isEnabled()) continue;
            if (element.contains(mouseX, mouseY)) found = element;
        }
        return found;
    }

    public void applyDrag(HudElement dragging, float proposedX, float proposedY) {
        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();

        float w = dragging.getWidth();
        float h = dragging.getHeight();

        List<Float> vTargets = verticalTargets(dragging, screenW);
        List<Float> hTargets = horizontalTargets(dragging, screenH);

        float snappedX = proposedX;
        float bestDX = SNAP;
        for (float target : vTargets) {
            float[] anchors = {proposedX, proposedX + w / 2f, proposedX + w};
            for (float anchor : anchors) {
                float d = Math.abs(anchor - target);
                if (d < bestDX) {
                    bestDX = d;
                    snappedX = proposedX + (target - anchor);
                }
            }
        }

        float snappedY = proposedY;
        float bestDY = SNAP;
        for (float target : hTargets) {
            float[] anchors = {proposedY, proposedY + h / 2f, proposedY + h};
            for (float anchor : anchors) {
                float d = Math.abs(anchor - target);
                if (d < bestDY) {
                    bestDY = d;
                    snappedY = proposedY + (target - anchor);
                }
            }
        }

        dragging.setPosition(snappedX, snappedY);

        float fx = dragging.resolveX(snappedX);
        float fy = dragging.resolveY(snappedY);

        guides.begin();
        collectGuides(dragging, fx, fy, w, h, vTargets, hTargets, screenW, screenH);
    }

    private void collectGuides(HudElement dragging, float fx, float fy, float w, float h,
                               List<Float> vTargets, List<Float> hTargets, int screenW, int screenH) {
        float[] xAnchors = {fx, fx + w / 2f, fx + w};
        for (float target : vTargets) {
            for (float anchor : xAnchors) {
                if (Math.abs(anchor - target) <= 1.0f) {
                    float[] span = verticalSpan(dragging, target, fy, fy + h);
                    guides.addVertical(target, span[0], span[1]);
                    break;
                }
            }
        }

        float[] yAnchors = {fy, fy + h / 2f, fy + h};
        for (float target : hTargets) {
            for (float anchor : yAnchors) {
                if (Math.abs(anchor - target) <= 1.0f) {
                    float[] span = horizontalSpan(dragging, target, fx, fx + w);
                    guides.addHorizontal(target, span[0], span[1]);
                    break;
                }
            }
        }
    }

    private List<Float> verticalTargets(HudElement dragging, int screenW) {
        List<Float> targets = new ArrayList<>();
        targets.add(0f);
        targets.add(screenW / 2f);
        targets.add((float) screenW);
        for (HudElement other : elements.values()) {
            if (other == dragging || !other.isRenderedThisFrame() || !other.isEnabled()) continue;
            targets.add(other.left());
            targets.add(other.centerX());
            targets.add(other.right());
        }
        return targets;
    }

    private List<Float> horizontalTargets(HudElement dragging, int screenH) {
        List<Float> targets = new ArrayList<>();
        targets.add(0f);
        targets.add(screenH / 2f);
        targets.add((float) screenH);
        for (HudElement other : elements.values()) {
            if (other == dragging || !other.isRenderedThisFrame() || !other.isEnabled()) continue;
            targets.add(other.top());
            targets.add(other.centerY());
            targets.add(other.bottom());
        }
        return targets;
    }

    private float[] verticalSpan(HudElement dragging, float line, float dragTop, float dragBottom) {
        float min = dragTop;
        float max = dragBottom;
        for (HudElement other : elements.values()) {
            if (other == dragging || !other.isRenderedThisFrame() || !other.isEnabled()) continue;
            if (near(other.left(), line) || near(other.centerX(), line) || near(other.right(), line)) {
                min = Math.min(min, other.top());
                max = Math.max(max, other.bottom());
            }
        }
        return new float[]{min - 4f, max + 4f};
    }

    private float[] horizontalSpan(HudElement dragging, float line, float dragLeft, float dragRight) {
        float min = dragLeft;
        float max = dragRight;
        for (HudElement other : elements.values()) {
            if (other == dragging || !other.isRenderedThisFrame() || !other.isEnabled()) continue;
            if (near(other.top(), line) || near(other.centerY(), line) || near(other.bottom(), line)) {
                min = Math.min(min, other.left());
                max = Math.max(max, other.right());
            }
        }
        return new float[]{min - 4f, max + 4f};
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) <= 1.0f;
    }

    public void idle() {
        guides.begin();
    }

    public void renderGuides(DrawContext context, Color color) {
        guides.render(context, color);
    }

    public static class GuideManager {
        private final Map<String, Guide> guides = new HashMap<>();

        public void begin() {
            for (Guide guide : guides.values()) {
                guide.target = 0f;
                guide.spanSetThisRound = false;
            }
        }

        public void addVertical(float x, float start, float end) {
            add(true, x, start, end);
        }

        public void addHorizontal(float y, float start, float end) {
            add(false, y, start, end);
        }

        private void add(boolean vertical, float pos, float start, float end) {
            String key = (vertical ? "V" : "H") + Math.round(pos);
            Guide guide = guides.computeIfAbsent(key, k -> new Guide(vertical, pos));
            guide.pos = pos;
            guide.target = 1f;
            guide.freshSpan(start, end);
        }

        private static final float CORE        = 0.35f;
        private static final float GLOW_REACH  = 6.0f;
        private static final float GLOW_STEP   = 0.5f;
        private static final float GLOW_PEAK   = 105f;
        private static final float CORE_PEAK   = 235f;
        private static final float SIGMA       = GLOW_REACH / 2.5f;

        public void render(DrawContext context, Color color) {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            float screenW = client.getWindow().getScaledWidth();
            float screenH = client.getWindow().getScaledHeight();

            Iterator<Map.Entry<String, Guide>> it = guides.entrySet().iterator();
            while (it.hasNext()) {
                Guide guide = it.next().getValue();
                float a = guide.alpha.get(guide.target);
                if (guide.target == 0f && a <= 0.02f) {
                    it.remove();
                    continue;
                }

                float twoSigmaSq = 2f * SIGMA * SIGMA;

                if (guide.vertical) {
                    float x = guide.pos;
                    for (float d = CORE; d < CORE + GLOW_REACH; d += GLOW_STEP) {
                        float dist = d - CORE + GLOW_STEP * 0.5f;
                        Color g = alpha(color, a * GLOW_PEAK * (float) Math.exp(-(dist * dist) / twoSigmaSq), 255);
                        Renderer2D.renderQuad(context, x + d, 0, x + d + GLOW_STEP, screenH, g);
                        Renderer2D.renderQuad(context, x - d - GLOW_STEP, 0, x - d, screenH, g);
                    }
                    Renderer2D.renderQuad(context, x - CORE, 0, x + CORE, screenH, alpha(color, a * CORE_PEAK, 255));
                } else {
                    float y = guide.pos;
                    for (float d = CORE; d < CORE + GLOW_REACH; d += GLOW_STEP) {
                        float dist = d - CORE + GLOW_STEP * 0.5f;
                        Color g = alpha(color, a * GLOW_PEAK * (float) Math.exp(-(dist * dist) / twoSigmaSq), 255);
                        Renderer2D.renderQuad(context, 0, y + d, screenW, y + d + GLOW_STEP, g);
                        Renderer2D.renderQuad(context, 0, y - d - GLOW_STEP, screenW, y - d, g);
                    }
                    Renderer2D.renderQuad(context, 0, y - CORE, screenW, y + CORE, alpha(color, a * CORE_PEAK, 255));
                }
            }
        }

        private static Color alpha(Color base, float raw, int cap) {
            int a = Math.max(0, Math.min(cap, Math.round(raw)));
            return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
        }
    }

    private static class Guide {
        private final boolean vertical;
        private float pos;
        private float start = Float.MAX_VALUE;
        private float end = -Float.MAX_VALUE;
        private float target;
        private boolean spanSetThisRound;
        private final Animation alpha = new Animation(160, Easing.Method.EASE_OUT_CUBIC);

        private Guide(boolean vertical, float pos) {
            this.vertical = vertical;
            this.pos = pos;
        }

        private void freshSpan(float s, float e) {
            if (!spanSetThisRound) {
                start = s;
                end = e;
                spanSetThisRound = true;
            } else {
                start = Math.min(start, s);
                end = Math.max(end, e);
            }
        }
    }
}
