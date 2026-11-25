package dev.project.anatorius.events.impl;

import dev.project.anatorius.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

@Getter
@Setter
@AllArgsConstructor
public class BlockCollisionEvent extends Event {
    private BlockState state;
    private BlockPos pos;
}
