package dev.project.anatorius.utils.playermodels;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PlayerModelRenderer {
    public static final Identifier WHITE = Identifier.of("anatorius", "textures/models/white.png");
    public static int submits, armSubmits;

    private PlayerModelRenderer() {
    }

    private static RenderLayer layer(PlayerModelMesh.Batch batch) {
        if (batch.texture == null) return RenderLayers.entitySolid(WHITE);
        return batch.translucent ? RenderLayers.entityTranslucent(batch.texture) : RenderLayers.entityCutoutNoCull(batch.texture);
    }

    public static void submit(PlayerModelMesh mesh, PlayerEntityModel model, MatrixStack matrices, OrderedRenderCommandQueue queue,
                              int light, int overlay, int tint) {
        ModelPart rootPart = model.getRootPart();
        Matrix4f root = matrixAt(rootPart, rootPart.originX / 16f, rootPart.originY / 16f, rootPart.originZ / 16f);
        ModelPart[] vanilla = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        Matrix4f[] pose = new Matrix4f[PlayerModelMesh.PARTS.length];
        for (int i = 0; i < pose.length; i++) {
            ModelPart part = vanilla[i];
            if (!part.visible || part.hidden) continue;
            PlayerModelMesh.Part ours = mesh.parts[i];
            ModelTransform d = part.getDefaultTransform();
            pose[i] = new Matrix4f(root).mul(matrixAt(part,
                    ours.pivotX + (part.originX - d.x()) / 16f,
                    ours.pivotY + (part.originY - d.y()) / 16f,
                    ours.pivotZ + (part.originZ - d.z()) / 16f));
        }
        submits++;
        for (int i = 0; i < pose.length; i++) {
            if (pose[i] == null) continue;
            Matrix4f partPose = pose[i];
            for (PlayerModelMesh.Batch batch : mesh.parts[i].batches) {
                queue.submitCustom(matrices, layer(batch), (entry, consumer) -> emit(batch, entry, partPose, consumer, light, overlay, tint));
            }
        }
        BackItem back = PlayerModels.backFor(mesh);
        if (back != null && pose[PlayerModelMesh.BODY] != null) submitBack(back, mesh.parts[PlayerModelMesh.BODY], pose[PlayerModelMesh.BODY], matrices, queue, light, tint);
    }

    private static final float BACK_HEIGHT = 1.45f, BACK_DISTANCE = 0.45f, BACK_SIZE = 2.5f;
    public static int backSubmits;

    private static void submitBack(BackItem back, PlayerModelMesh.Part body, Matrix4f bodyPose, MatrixStack matrices,
                                   OrderedRenderCommandQueue queue, int light, int tint) {
        dev.project.anatorius.modules.impl.visuals.Items3DModule module = dev.project.anatorius.modules.impl.visuals.Items3DModule.active();
        if (module == null) return;
        float shown = PlayerModels.backAmount(module);
        if (shown <= 0) return;
        float e = 1 - (1 - shown) * (1 - shown) * (1 - shown);
        float size = BACK_SIZE * module.backSize.getValue().floatValue() / 100f * e;
        Matrix4f base = new Matrix4f(bodyPose)
                .translate(-body.pivotX, 1.5f - BACK_HEIGHT - body.pivotY, BACK_DISTANCE - body.pivotZ)
                .rotateZ((1 - e) * (float) Math.PI / 2f)
                .scale(size);
        float seconds = (System.nanoTime() / 1_000_000L % 3_600_000L) / 1000f;
        backSubmits++;
        for (BackItem.Piece piece : back.pieces) {
            Matrix4f m = piece.speed == 0 ? base : new Matrix4f(base).translate(piece.centerX, piece.centerY, 0)
                    .rotateZ(piece.speed * seconds).translate(-piece.centerX, -piece.centerY, 0);
            for (PlayerModelMesh.Batch batch : piece.batches) {
                queue.submitCustom(matrices, layer(batch), (entry, consumer) -> emit(batch, entry, m, consumer, light,
                        net.minecraft.client.render.OverlayTexture.DEFAULT_UV, tint));
            }
        }
    }

    public static void submitArm(PlayerModelMesh mesh, boolean right, ModelPart arm, MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        PlayerModelMesh.Part ours = mesh.parts[right ? PlayerModelMesh.RIGHT_ARM : PlayerModelMesh.LEFT_ARM];
        float side = right ? -1 : 1;
        float shift = side / 16f - ours.handX();
        Matrix4f m = matrixAt(arm, arm.originX / 16f + shift, arm.originY / 16f, arm.originZ / 16f);
        armSubmits++;
        for (PlayerModelMesh.Batch batch : ours.batches) {
            queue.submitCustom(matrices, layer(batch), (entry, consumer) -> emit(batch, entry, m, consumer, light,
                    net.minecraft.client.render.OverlayTexture.DEFAULT_UV, -1));
        }
    }

    public static void moveArmsForItems(PlayerModelMesh mesh, PlayerEntityModel model) {
        moveArm(model.rightArm, mesh.parts[PlayerModelMesh.RIGHT_ARM], -1);
        moveArm(model.leftArm, mesh.parts[PlayerModelMesh.LEFT_ARM], 1);
    }

    private static void moveArm(ModelPart arm, PlayerModelMesh.Part ours, float side) {
        ModelTransform d = arm.getDefaultTransform();
        arm.originX = (ours.pivotX + ours.handX()) * 16f - side + (arm.originX - d.x());
        arm.originY = ours.pivotY * 16f + (arm.originY - d.y());
        arm.originZ = ours.pivotZ * 16f + (arm.originZ - d.z());
    }

    private static Matrix4f matrixAt(ModelPart part, float x, float y, float z) {
        Matrix4f m = new Matrix4f().translation(x, y, z);
        if (part.pitch != 0 || part.yaw != 0 || part.roll != 0) m.rotate(new Quaternionf().rotationZYX(part.roll, part.yaw, part.pitch));
        if (part.xScale != 1 || part.yScale != 1 || part.zScale != 1) m.scale(part.xScale, part.yScale, part.zScale);
        return m;
    }

    private static void emit(PlayerModelMesh.Batch batch, MatrixStack.Entry entry, Matrix4f pose, VertexConsumer consumer,
                             int light, int overlay, int tint) {
        Matrix4f m = new Matrix4f(entry.getPositionMatrix()).mul(pose);
        Matrix3f n = new Matrix3f(entry.getNormalMatrix()).mul(new Matrix3f(pose).normal());
        int tr = tint >> 16 & 0xFF, tg = tint >> 8 & 0xFF, tb = tint & 0xFF, ta = tint >>> 24;
        boolean tinted = tint != -1;
        Vector3f p = new Vector3f(), nv = new Vector3f();
        float[] pos = batch.positions, nrm = batch.normals, uv = batch.uvs;
        int[] col = batch.colors;
        for (int i = 0, j = 0; i < batch.vertexCount; i++, j += 3) {
            m.transformPosition(pos[j], pos[j + 1], pos[j + 2], p);
            n.transform(nrm[j], nrm[j + 1], nrm[j + 2], nv);
            if (nv.lengthSquared() > 0) nv.normalize();
            int c = col[i];
            if (tinted) {
                c = ((c >>> 24) * ta / 255) << 24 | ((c >> 16 & 0xFF) * tr / 255) << 16 | ((c >> 8 & 0xFF) * tg / 255) << 8 | (c & 0xFF) * tb / 255;
            }
            float u = uv != null ? uv[i * 2] : 0.5f, v = uv != null ? uv[i * 2 + 1] : 0.5f;
            consumer.vertex(p.x, p.y, p.z, c, u, v, overlay, light, nv.x, nv.y, nv.z);
        }
    }
}
