package dev.project.anatorius.mixins;

import dev.project.anatorius.utils.mixins.IPlayerModelState;
import dev.project.anatorius.utils.playermodels.PlayerModelMesh;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateMixin implements IPlayerModelState {
    @Unique private PlayerModelMesh anatorius$playerModel;

    @Override
    public PlayerModelMesh anatorius$getPlayerModel() {
        return anatorius$playerModel;
    }

    @Override
    public void anatorius$setPlayerModel(PlayerModelMesh mesh) {
        anatorius$playerModel = mesh;
    }
}
