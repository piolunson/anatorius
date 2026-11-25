package dev.project.anatorius.utils.items3d;

import dev.project.anatorius.mixins.accessors.ItemRenderStateAccessor;
import dev.project.anatorius.mixins.accessors.LayerRenderStateAccessor;
import dev.project.anatorius.modules.impl.visuals.Items3DModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.EndCrystalEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Items3DRenderer implements IMinecraft {
    public static final Items3DRenderer INSTANCE = new Items3DRenderer();

    private static final Identifier CRYSTAL_TEXTURE = Identifier.ofVanilla("textures/entity/end_crystal/end_crystal.png");
    private static final int CACHE_LIMIT = 96;
    public static final Object CRYSTAL_MODEL_KEY = "anatorius:crystal3d";

    private static final Set<Item> SPECIALS = Set.of(Items.TOTEM_OF_UNDYING, Items.ENDER_PEARL, Items.ENDER_EYE,
            Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.NETHER_STAR, Items.BLAZE_ROD, Items.WIND_CHARGE,
            Items.FIREWORK_ROCKET, Items.EXPERIENCE_BOTTLE, Items.COMPASS, Items.CLOCK, Items.MAP, Items.BUCKET,
            Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.TORCH, Items.SOUL_TORCH, Items.LANTERN, Items.SOUL_LANTERN);
    private static final Set<Item> TOOLS = Set.of(Items.MACE, Items.BOW, Items.CROSSBOW, Items.FISHING_ROD,
            Items.FLINT_AND_STEEL, Items.SHEARS);

    private final ItemRenderState state = new ItemRenderState();
    private final Map<Sprite, VoxelItemMesh> cache = new HashMap<>();
    private int cacheParams;
    private long drawn3D, vanilla, failures;
    private boolean warned;

    private EndCrystalEntityModel crystalModel;
    private LoadedEntityModels crystalModelSource;

    private static final Map<Item, String> SWORD_TIERS = Map.of(Items.WOODEN_SWORD, "wooden", Items.STONE_SWORD, "stone",
            Items.IRON_SWORD, "iron", Items.GOLDEN_SWORD, "golden", Items.DIAMOND_SWORD, "diamond", Items.NETHERITE_SWORD, "netherite");
    private static final Identifier SWORD_MODEL = Identifier.of("anatorius", "items3d/models/sword.json");
    private HandmadeModel swordModel;
    private boolean swordModelLoaded;
    private final Map<Item, Boolean> packOverride = new HashMap<>();
    private LoadedEntityModels resourcesSeen;
    private long handmadeDrawn;
    private long crystalViews;

    private Items3DRenderer() { }

    public void clear() {
        cache.clear();
        crystalModel = null;
        crystalModelSource = null;
        swordModel = null;
        swordModelLoaded = false;
        packOverride.clear();
        resourcesSeen = null;
    }

    public int cachedMeshes() {
        return cache.size();
    }

    public String stats() {
        return "3d=" + drawn3D + " (handmade " + handmadeDrawn + (swordModel != null ? " " + swordModel.extremes() : "") + ") crystalViews=" + crystalViews + " vanilla=" + vanilla + " failed=" + failures + " cached=" + cache.size();
    }

    public boolean render(Items3DModule m, LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                          MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        boolean done = renderInner(m, entity, stack, context, matrices, queue, light);
        if (done) drawn3D++; else vanilla++;
        return done;
    }

    private boolean renderInner(Items3DModule m, LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        boolean crystal = stack.isOf(Items.END_CRYSTAL);
        if (crystal ? !m.crystal3D.getValue() : !eligible(m, stack)) return false;

        try {
            mc.getItemModelManager().updateForLivingEntity(state, stack, context, entity);
            ItemRenderStateAccessor sa = (ItemRenderStateAccessor) state;
            if (sa.getLayerCount() != 1) return false;
            ItemRenderState.LayerRenderState layer = sa.getLayers()[0];
            List<BakedQuad> quads = layer.getQuads();
            if (quads.isEmpty()) return false;

            Sprite sprite = quads.getFirst().sprite();
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            boolean flat = !tinted(((LayerRenderStateAccessor) layer).getTints());
            for (BakedQuad q : quads) {
                if (q.sprite() != sprite) flat = false;
                for (Vector3fc p : new Vector3fc[]{q.position0(), q.position1(), q.position2(), q.position3()}) {
                    minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
                    minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
                    minZ = Math.min(minZ, p.z()); maxZ = Math.max(maxZ, p.z());
                }
            }
            float width = maxX - minX, height = maxY - minY;
            if (width <= 0f || height <= 0f) return false;
            if (maxZ - minZ > width * 0.13f) flat = false;

            Transformation transform = ((LayerRenderStateAccessor) layer).getTransform();
            if (crystal) {
                if (!flat && m.priority.getValue().equals("ResourcePack")) return false;
                renderCrystal(m, transform, context, matrices, queue, light, (minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f, false);
                return true;
            }
            if (!flat) return false;
            boolean glint = ((LayerRenderStateAccessor) layer).getGlint() != ItemRenderState.Glint.NONE;

            String tier = Items3DModule.HANDMADE ? SWORD_TIERS.get(stack.getItem()) : null;
            HandmadeModel handmadeModel = tier != null && !packWins(m, stack.getItem()) ? swordModel() : null;
            if (handmadeModel != null) {
                matrices.push();
                transform.apply(context.isLeftHand(), matrices.peek());
                scaleAroundCentre(m, matrices, minX, maxX, minY, maxY, minZ, maxZ);
                matrices.translate(minX, minY, (minZ + maxZ) / 2f);
                matrices.scale(width / 16f, height / 16f, width / 16f);
                matrices.translate(0f, 0f, -8f);
                Identifier texture = Identifier.of("anatorius", "textures/items3d/sword_" + tier + ".png");
                queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(texture), (entry, consumer) -> handmadeModel.emit(entry, consumer, light, -1));
                if (glint) queue.submitCustom(matrices, RenderLayers.glint(), (entry, consumer) -> handmadeModel.emit(entry, consumer, light, -1));
                matrices.pop();
                handmadeDrawn++;
                return true;
            }

            VoxelItemMesh mesh = mesh(m, sprite);
            if (mesh == null) return false;

            matrices.push();
            transform.apply(context.isLeftHand(), matrices.peek());
            scaleAroundCentre(m, matrices, minX, maxX, minY, maxY, minZ, maxZ);
            matrices.translate(minX, minY, (minZ + maxZ) / 2f);
            matrices.scale(width, height, width);
            RenderLayer solid = RenderLayers.entityCutoutNoCull(sprite.getAtlasId());
            queue.submitCustom(matrices, solid, (entry, consumer) -> mesh.emit(entry, consumer, light, -1));
            if (glint) {
                queue.submitCustom(matrices, RenderLayers.glint(), (entry, consumer) -> mesh.emit(entry, consumer, light, -1));
            }
            matrices.pop();
            return true;
        } catch (RuntimeException e) {
            failures++;
            if (!warned) {
                warned = true;
                dev.project.anatorius.Anatorius.LOGGER.warn("[Anatorius] Items3D fell back to vanilla for {}: {}", stack.getItem(), e.toString());
            }
            return false;
        }
    }

    private static void scaleAroundCentre(Items3DModule m, MatrixStack matrices, float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
        float scale = m.modelScale.getValue().floatValue() / 100f;
        if (scale == 1f) return;
        float cx = (minX + maxX) / 2f, cy = (minY + maxY) / 2f, cz = (minZ + maxZ) / 2f;
        matrices.translate(cx, cy, cz);
        matrices.scale(scale, scale, scale);
        matrices.translate(-cx, -cy, -cz);
    }

    private void checkReload() {
        LoadedEntityModels now = mc.getLoadedEntityModels();
        if (now == resourcesSeen) return;
        resourcesSeen = now;
        swordModel = null;
        swordModelLoaded = false;
        packOverride.clear();
    }

    private HandmadeModel swordModel() {
        checkReload();
        if (!swordModelLoaded) {
            swordModelLoaded = true;
            swordModel = HandmadeModel.load(mc.getResourceManager(), SWORD_MODEL);
            if (swordModel == null) dev.project.anatorius.Anatorius.LOGGER.warn("[Anatorius] Items3D: hand-made sword model missing or malformed, using texture 3D");
        }
        return swordModel;
    }

    private boolean packWins(Items3DModule m, Item item) {
        if (!m.priority.getValue().equals("ResourcePack")) return false;
        checkReload();
        return packOverride.computeIfAbsent(item, i -> {
            Identifier id = net.minecraft.registry.Registries.ITEM.getId(i);
            Identifier texture = Identifier.of(id.getNamespace(), "textures/item/" + id.getPath() + ".png");
            return mc.getResourceManager().getResource(texture).map(r -> !"vanilla".equals(r.getPackId())).orElse(false);
        });
    }

    private static boolean tinted(int[] tints) {
        if (tints == null) return false;
        for (int t : tints) if (t != -1) return true;
        return false;
    }

    private static boolean eligible(Items3DModule m, ItemStack stack) {
        Item item = stack.getItem();
        if (m.weapons.getValue() && (stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.PICKAXES)
                || stack.isIn(ItemTags.SHOVELS) || stack.isIn(ItemTags.HOES) || TOOLS.contains(item))) return true;
        return m.specials.getValue() && SPECIALS.contains(item);
    }

    private VoxelItemMesh mesh(Items3DModule m, Sprite sprite) {
        int steps = ItemMotion.INSTANCE.degraded() ? 0 : m.quality();
        float thickness = Items3DModule.THICKNESS;
        int params = steps * 1000 + Math.round(thickness * 100f);
        if (params != cacheParams) { cache.clear(); cacheParams = params; }
        if (cache.containsKey(sprite)) return cache.get(sprite);
        if (cache.size() >= CACHE_LIMIT) cache.clear();
        VoxelItemMesh mesh = VoxelItemMesh.build(sprite, steps, thickness);
        cache.put(sprite, mesh);
        return mesh;
    }

    public boolean crystalEligible(Items3DModule m, ItemRenderState state) {
        float[] b = crystalLayout(state);
        return b != null && (b[3] > 0f || !m.priority.getValue().equals("ResourcePack"));
    }

    public boolean renderCrystalState(Items3DModule m, ItemRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        try {
            float[] b = crystalLayout(state);
            if (b == null) return false;
            ItemRenderStateAccessor sa = (ItemRenderStateAccessor) state;
            ItemMotion.INSTANCE.advanceClock(m);
            renderCrystal(m, ((LayerRenderStateAccessor) sa.getLayers()[0]).getTransform(), sa.getDisplayContext(), matrices, queue, light,
                    b[0], b[1], b[2], true);
            crystalViews++;
            return true;
        } catch (Exception e) {
            failures++;
            if (!warned) {
                warned = true;
                dev.project.anatorius.Anatorius.LOGGER.warn("[Anatorius] Items3D: 3D crystal fell back to the flat item: {}", e.toString());
            }
            return false;
        }
    }

    private static float[] crystalLayout(ItemRenderState state) {
        ItemRenderStateAccessor sa = (ItemRenderStateAccessor) state;
        if (sa.getLayerCount() != 1) return null;
        ItemRenderState.LayerRenderState layer = sa.getLayers()[0];
        List<BakedQuad> quads = layer.getQuads();
        if (quads.isEmpty()) return null;
        Sprite sprite = quads.getFirst().sprite();
        boolean flat = !tinted(((LayerRenderStateAccessor) layer).getTints());
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (BakedQuad q : quads) {
            if (q.sprite() != sprite) flat = false;
            for (Vector3fc p : new Vector3fc[]{q.position0(), q.position1(), q.position2(), q.position3()}) {
                minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
                minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
                minZ = Math.min(minZ, p.z()); maxZ = Math.max(maxZ, p.z());
            }
        }
        float width = maxX - minX;
        if (width <= 0f || maxY - minY <= 0f) return null;
        if (maxZ - minZ > width * 0.13f) flat = false;
        return new float[]{(minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f, flat ? 1f : 0f};
    }

    private void renderCrystal(Items3DModule m, Transformation transform, ItemDisplayContext context, MatrixStack matrices,
                               OrderedRenderCommandQueue queue, int light, float cx, float cy, float cz, boolean otherView) {
        LoadedEntityModels models = mc.getLoadedEntityModels();
        if (crystalModel == null || crystalModelSource != models) {
            crystalModel = new EndCrystalEntityModel(models.getModelPart(EntityModelLayers.END_CRYSTAL));
            crystalModelSource = models;
        }
        ItemMotion motion = ItemMotion.INSTANCE;
        EndCrystalEntityRenderState st = new EndCrystalEntityRenderState();
        st.baseVisible = false;
        st.age = Items3DModule.CORE_ROTATION ? motion.crystalTicks() : 0f;
        st.light = light;
        boolean gui = context == ItemDisplayContext.GUI;

        matrices.push();
        transform.apply(context.isLeftHand(), matrices.peek());
        matrices.translate(cx, cy, cz);
        Vector3fc r = transform.rotation();
        float ry = context.isLeftHand() ? -r.y() : r.y(), rz = context.isLeftHand() ? -r.z() : r.z();
        matrices.multiply(new Quaternionf().rotationXYZ(r.x() * MathHelper.RADIANS_PER_DEGREE, ry * MathHelper.RADIANS_PER_DEGREE,
                rz * MathHelper.RADIANS_PER_DEGREE).conjugate());
        float size;
        if (!otherView) {
            matrices.translate(context.isLeftHand() ? 0.2f : -0.2f, 0.24f, 0f);
            size = 0.36f;
        } else if (gui) {
            matrices.multiply(new Quaternionf().rotationX(20f * MathHelper.RADIANS_PER_DEGREE));
            size = 1.14f;
        } else if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            matrices.translate(0f, 0.16f, 0f);
            size = 1.3f;
        } else {
            size = 1.0f;
        }
        size *= m.modelScale.getValue().floatValue() / 100f;
        matrices.scale(size, size, size);
        if (Items3DModule.FLOATING && !gui && context != ItemDisplayContext.GROUND) {
            float amp = 0.10f * Items3DModule.FLOAT_STRENGTH;
            matrices.translate(0f, MathHelper.sin(motion.floatTime() * 2.2f) * amp, 0f);
        }
        crystalModel.setAngles(st);
        matrices.translate(0f, -crystalModel.outerGlass.originY / 16f, 0f);
        queue.submitModel(crystalModel, st, matrices, RenderLayers.entityCutoutNoCull(CRYSTAL_TEXTURE), light, OverlayTexture.DEFAULT_UV, 0, null);
        if (Items3DModule.GLOW && !motion.degraded()) {
            int i = Math.round(255f * Items3DModule.GLOW_INTENSITY);
            if (gui) i = Math.max(i, 191);
            if (i > 0) {
                queue.submitModel(crystalModel, st, matrices, RenderLayers.eyes(CRYSTAL_TEXTURE), LightmapTextureManager.MAX_LIGHT_COORDINATE,
                        OverlayTexture.DEFAULT_UV, 0xFF000000 | (i << 16) | (i << 8) | i, null, 0, null);
            }
        }
        matrices.pop();
    }
}
