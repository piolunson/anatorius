package dev.project.anatorius.utils.playermodels;

import net.minecraft.util.Identifier;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class PlayerModelMesh {
    public static final String[] PARTS = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
    public static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;

    public static final class Batch {
        public Identifier texture;
        public final boolean translucent;
        public final float[] positions;
        public final float[] normals;
        public final float[] uvs;
        public final int[] colors;
        public final int vertexCount;

        public Batch(Identifier texture, boolean translucent, float[] positions, float[] normals, float[] uvs, int[] colors) {
            this.texture = texture;
            this.translucent = translucent;
            this.positions = positions;
            this.normals = normals;
            this.uvs = uvs;
            this.colors = colors;
            this.vertexCount = colors.length;
        }
    }

    public static final class Part {
        public final float pivotX, pivotY, pivotZ;
        public final Batch[] batches;

        public Part(float px, float py, float pz, Batch[] batches) {
            this.pivotX = px;
            this.pivotY = py;
            this.pivotZ = pz;
            this.batches = batches;
        }

        public float handX() {
            float maxY = -Float.MAX_VALUE;
            for (Batch b : batches) for (int i = 0; i < b.vertexCount; i++) maxY = Math.max(maxY, b.positions[i * 3 + 1]);
            if (maxY == -Float.MAX_VALUE) return 0;
            float cut = maxY * 0.85f, min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            for (Batch b : batches) for (int i = 0; i < b.vertexCount; i++) {
                if (b.positions[i * 3 + 1] < cut) continue;
                min = Math.min(min, b.positions[i * 3]);
                max = Math.max(max, b.positions[i * 3]);
            }
            return min == Float.MAX_VALUE ? 0 : (min + max) / 2f;
        }
    }

    public final Part[] parts = new Part[PARTS.length];
    public final int quads;

    private PlayerModelMesh(Part[] parts) {
        int vertices = 0;
        for (int i = 0; i < PARTS.length; i++) {
            this.parts[i] = parts[i] != null ? parts[i] : new Part(0, 0, 0, new Batch[0]);
            for (Batch b : this.parts[i].batches) vertices += b.vertexCount;
        }
        this.quads = vertices / 4;
    }

    public static PlayerModelMesh of(Part[] parts) {
        return new PlayerModelMesh(parts);
    }

    public static PlayerModelMesh read(InputStream stream) throws IOException {
        DataInputStream in = new DataInputStream(stream);
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (magic[0] != 'A' || magic[1] != 'M' || magic[2] != 'D' || magic[3] != 'L') throw new IOException("not an AMDL model");
        int version = in.readInt();
        if (version != 1) throw new IOException("unsupported AMDL version " + version);
        int count = in.readUnsignedByte();
        Part[] read = new Part[PARTS.length];
        for (int p = 0; p < count; p++) {
            byte[] nameBytes = new byte[in.readUnsignedShort()];
            in.readFully(nameBytes);
            String name = new String(nameBytes, java.nio.charset.StandardCharsets.US_ASCII);
            float px = in.readFloat() / 16f, py = in.readFloat() / 16f, pz = in.readFloat() / 16f;
            int n = in.readInt();
            float[] pos = new float[n * 3], nrm = new float[n * 3];
            int[] col = new int[n];
            for (int i = 0; i < n; i++) {
                pos[i * 3] = in.readFloat() / 16f;
                pos[i * 3 + 1] = in.readFloat() / 16f;
                pos[i * 3 + 2] = in.readFloat() / 16f;
                nrm[i * 3] = in.readByte() / 127f;
                nrm[i * 3 + 1] = in.readByte() / 127f;
                nrm[i * 3 + 2] = in.readByte() / 127f;
                col[i] = 0xFF000000 | in.readUnsignedByte() << 16 | in.readUnsignedByte() << 8 | in.readUnsignedByte();
            }
            int index = indexOf(name);
            if (index >= 0) read[index] = new Part(px, py, pz, new Batch[]{new Batch(null, false, pos, nrm, null, col)});
        }
        return new PlayerModelMesh(read);
    }

    private static int indexOf(String name) {
        for (int i = 0; i < PARTS.length; i++) if (PARTS[i].equals(name)) return i;
        return -1;
    }
}
