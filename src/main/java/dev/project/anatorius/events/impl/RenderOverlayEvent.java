package dev.project.anatorius.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import dev.project.anatorius.events.Event;
import net.minecraft.client.gui.DrawContext;

@Getter @AllArgsConstructor
public class RenderOverlayEvent extends Event {
    private final DrawContext context;
    private final float tickDelta;
}
