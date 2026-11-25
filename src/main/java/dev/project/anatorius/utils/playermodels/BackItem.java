package dev.project.anatorius.utils.playermodels;

public final class BackItem {
    public static final class Piece {
        public final float centerX, centerY, speed;
        public final PlayerModelMesh.Batch[] batches;

        public Piece(float centerX, float centerY, float speed, PlayerModelMesh.Batch[] batches) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.speed = speed;
            this.batches = batches;
        }
    }

    public final Piece[] pieces;
    public final int quads;

    public BackItem(Piece[] pieces) {
        this.pieces = pieces;
        int vertices = 0;
        for (Piece p : pieces) for (PlayerModelMesh.Batch b : p.batches) vertices += b.vertexCount;
        this.quads = vertices / 4;
    }
}
