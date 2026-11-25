package dev.project.anatorius.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import dev.project.anatorius.events.Event;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;

@Getter @Setter @AllArgsConstructor
public class RenderHandEvent extends Event {
    private OrderedRenderCommandQueue queue;

    public static class Post extends Event {}
}
