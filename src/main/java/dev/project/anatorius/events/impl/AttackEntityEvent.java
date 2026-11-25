package dev.project.anatorius.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import dev.project.anatorius.events.Event;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

@Getter @AllArgsConstructor
public class AttackEntityEvent extends Event {
    private final PlayerEntity player;
    private final Entity target;

    @Getter @AllArgsConstructor
    public static class Post extends Event {
        private final PlayerEntity player;
        private final Entity target;
    }
}
