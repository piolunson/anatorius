package dev.project.anatorius.utils.mixins;

import dev.project.anatorius.utils.playermodels.PlayerModelMesh;

public interface IPlayerModelState {
    PlayerModelMesh anatorius$getPlayerModel();

    void anatorius$setPlayerModel(PlayerModelMesh mesh);
}
