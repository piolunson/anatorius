package dev.project.anatorius.utils.playermodels;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.visuals.Items3DModule;
import dev.project.anatorius.utils.system.AssetVault;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.client.MinecraftClient;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerModels {
    public record Entry(String id, String name, String file, boolean local, String back) {
    }

    private static final String DIR = "assets/anatorius/models/player/";

    private static Entry bundled(String id, String name) {
        return new Entry(id, name, DIR + id + ".glb", false, null);
    }

    public static final String LOCAL_DIR = Anatorius.MOD_NAME + "/Models";

    public static final List<Entry> ALL = List.of(
            new Entry("terrorist", "Terrorist", DIR + "terrorist.amdl", false, null),
            new Entry("counter_terrorist", "Counter-Terrorist", DIR + "counter_terrorist.amdl", false, null),
            new Entry("kurumi_school", "Kurumi Tokisaki", DIR + "kurumi_school.glb", false, "assets/anatorius/models/back/zafkiel.glb"),
            bundled("nezuko", "Nezuko Kamado"),
            bundled("esdeath", "Esdeath"),
            bundled("rias", "Rias Gremory"),
            bundled("rias_underwear", "Rias Gremory (Swimsuit)"),
            bundled("naruto_sage", "Naruto (Sage Mode)"),
            bundled("naruto_fanart", "Naruto (Fan Art)"),
            bundled("sasuke_uchiha", "Sasuke Uchiha"),
            bundled("sasuke_shippuden", "Sasuke (Shippuden)"),
            bundled("sasuke_fanart", "Sasuke (Fan Art)"),
            bundled("madara", "Madara Uchiha"),
            bundled("sans", "Sans"),
            bundled("frisk", "Frisk")
    );

    private static volatile Entry[] local = new Entry[0];
    private static long localScanned;

    public static List<Entry> all() {
        scanLocal(false);
        List<Entry> out = new ArrayList<>(local.length + ALL.size());
        java.util.Collections.addAll(out, local);
        out.addAll(ALL);
        return out;
    }

    public static synchronized void scanLocal(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - localScanned < 3000) return;
        localScanned = now;
        java.io.File[] files = new java.io.File(LOCAL_DIR).listFiles((d, n) -> {
            String l = n.toLowerCase(java.util.Locale.ROOT);
            return l.endsWith(".amdl") || l.endsWith(".glb");
        });
        if (files == null) {
            new java.io.File(LOCAL_DIR).mkdirs();
            local = new Entry[0];
            return;
        }
        java.util.Arrays.sort(files);
        List<Entry> found = new ArrayList<>();
        for (java.io.File f : files) {
            String base = f.getName().substring(0, f.getName().lastIndexOf('.'));
            String id = "local:" + f.getName().toLowerCase(java.util.Locale.ROOT);
            Entry old = null;
            for (Entry e : local) if (e.id.equals(id)) old = e;
            found.add(old != null ? old : new Entry(id, pretty(base), f.getPath(), true, null));
        }
        local = found.toArray(new Entry[0]);
    }

    private static String pretty(String base) {
        StringBuilder sb = new StringBuilder();
        for (String w : base.replace('-', '_').split("_")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static final java.util.Map<String, PlayerModelMesh> MESHES = new java.util.HashMap<>();
    private static final java.util.Set<String> FAILED = new java.util.HashSet<>();

    private PlayerModels() {
    }

    public static Entry byId(String id) {
        for (Entry e : ALL) if (e.id.equals(id)) return e;
        if (id.startsWith("local:")) {
            scanLocal(true);
            for (Entry e : local) if (e.id.equals(id)) return e;
        }
        return null;
    }

    public static PlayerModelMesh forPlayer(PlayerLikeEntity player) {
        Items3DModule module = Items3DModule.active();
        if (module == null || module.playerModels.getWhitelist().isEmpty()) return null;
        boolean self = player == MinecraftClient.getInstance().player;
        if (self ? !module.targetSelf.getValue() : !module.targetOthers.getValue()) return null;
        List<Entry> selected = selected(module);
        if (selected.isEmpty()) return null;
        Entry entry = self ? selected.getFirst() : selected.get(Math.floorMod(spread(player.getUuid()), selected.size()));
        return mesh(entry);
    }

    public static PlayerModelMesh forSelf() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? null : forPlayer(mc.player);
    }

    private static List<Entry> selected(Items3DModule module) {
        List<Entry> out = new ArrayList<>(ALL.size());
        for (Entry e : all()) if (module.playerModels.isWhitelistContains(e.id)) out.add(e);
        return out;
    }

    private static int spread(UUID uuid) {
        long h = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        return (int) (h ^ (h >>> 32));
    }

    private static final java.util.Map<String, java.util.concurrent.CompletableFuture<GlbLoader.Result>> LOADING = new java.util.HashMap<>();
    private static final java.util.List<net.minecraft.util.Identifier> TEXTURES = new java.util.ArrayList<>();

    public static synchronized PlayerModelMesh mesh(Entry entry) {
        PlayerModelMesh mesh = MESHES.get(entry.id);
        if (mesh != null || FAILED.contains(entry.id)) return mesh;
        if (entry.file.toLowerCase(java.util.Locale.ROOT).endsWith(".glb")) return glb(entry);
        try (InputStream in = entry.local ? java.nio.file.Files.newInputStream(java.nio.file.Path.of(entry.file)) : AssetVault.open(entry.file)) {
            if (in == null) throw new java.io.FileNotFoundException(entry.file);
            mesh = PlayerModelMesh.read(new java.io.BufferedInputStream(in));
            MESHES.put(entry.id, mesh);
            meshLoaded(entry, mesh);
        } catch (Exception e) {
            FAILED.add(entry.id);
            Anatorius.LOGGER.warn("[CustomModel] could not load player model {}: {}", entry.id, e.toString());
        }
        return mesh;
    }

    static GlbLoader.Result parse(Entry entry) throws java.io.IOException {
        return entry.local ? GlbLoader.load(java.nio.file.Path.of(entry.file)) : GlbLoader.load(bundledBytes(entry.file), null);
    }

    private static PlayerModelMesh glb(Entry entry) {
        var future = LOADING.get(entry.id);
        if (future == null) {
            LOADING.put(entry.id, java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return parse(entry);
                } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(e);
                }
            }, net.minecraft.util.Util.getMainWorkerExecutor()));
            return null;
        }
        if (!future.isDone()) return null;
        LOADING.remove(entry.id);
        GlbLoader.Result result;
        try {
            result = future.join();
        } catch (Exception e) {
            FAILED.add(entry.id);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            Anatorius.LOGGER.warn("[CustomModel] could not load player model {}: {}", entry.id, cause.toString());
            if (Anatorius.CHAT_MANAGER != null) Anatorius.CHAT_MANAGER.message("CustomModel: " + entry.name + " could not be loaded (" + cause.getMessage() + ")");
            return null;
        }
        net.minecraft.util.Identifier[] ids = upload(entry.id, entry.name, result.textures());
        int k = 0;
        for (PlayerModelMesh.Part part : result.mesh().parts) {
            for (PlayerModelMesh.Batch batch : part.batches) {
                int tex = result.batchTexture()[k++];
                if (tex >= 0) batch.texture = ids[tex];
            }
        }
        MESHES.put(entry.id, result.mesh());
        meshLoaded(entry, result.mesh());
        Anatorius.LOGGER.info("[CustomModel] loaded {} ({} quads, {} textures)", entry.id, result.mesh().quads, ids.length);
        return result.mesh();
    }

    private static net.minecraft.util.Identifier[] upload(String key, String name, List<GlbLoader.Texture> list) {
        net.minecraft.client.texture.TextureManager textures = MinecraftClient.getInstance().getTextureManager();
        net.minecraft.util.Identifier[] ids = new net.minecraft.util.Identifier[list.size()];
        String folder = "playermodel/" + Integer.toHexString(key.hashCode()) + "/";
        for (int i = 0; i < ids.length; i++) {
            GlbLoader.Texture t = list.get(i);
            net.minecraft.client.texture.NativeImage image = new net.minecraft.client.texture.NativeImage(t.width(), t.height(), false);
            for (int y = 0; y < t.height(); y++) for (int x = 0; x < t.width(); x++) image.setColorArgb(x, y, t.argb()[y * t.width() + x]);
            ids[i] = net.minecraft.util.Identifier.of("anatorius", folder + i);
            String label = name + " #" + i;
            textures.registerTexture(ids[i], new net.minecraft.client.texture.NativeImageBackedTexture(() -> label, image));
            TEXTURES.add(ids[i]);
        }
        return ids;
    }

    public static final String BACK_DIR = LOCAL_DIR + "/Back";
    private static final java.util.Map<PlayerModelMesh, String> BACK_OF = new java.util.IdentityHashMap<>();
    private static final java.util.Map<String, BackItem> BACKS = new java.util.HashMap<>();
    private static final java.util.Map<String, java.util.concurrent.CompletableFuture<GlbLoader.PropResult>> BACK_LOADING = new java.util.HashMap<>();
    private static final java.util.Set<String> BACK_FAILED = new java.util.HashSet<>();
    private static String backFile;
    private static long backScanned;
    private static float backShown = -1;
    private static long backStepped;

    private static void meshLoaded(Entry entry, PlayerModelMesh mesh) {
        if (entry.local) BACK_OF.put(mesh, "");
        else if (entry.back != null) BACK_OF.put(mesh, entry.back);
    }

    public static synchronized String backFile() {
        long now = System.currentTimeMillis();
        if (now - backScanned < 3000) return backFile;
        backScanned = now;
        java.io.File[] files = new java.io.File(BACK_DIR).listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".glb"));
        if (files == null) new java.io.File(BACK_DIR).mkdirs();
        String found = null;
        if (files != null && files.length > 0) {
            java.util.Arrays.sort(files);
            found = files[0].getPath();
        }
        if (backFile != null && !backFile.equals(found)) {
            BACKS.remove(backFile);
            BACK_LOADING.remove(backFile);
            BACK_FAILED.remove(backFile);
        }
        backFile = found;
        return backFile;
    }

    public static synchronized String backName() {
        PlayerModelMesh self = forSelf();
        String source = self != null ? BACK_OF.get(self) : null;
        String f = source == null || source.isEmpty() ? backFile() : source;
        if (f == null) return "Back item";
        String n = f.substring(Math.max(f.lastIndexOf('/'), f.lastIndexOf(java.io.File.separatorChar)) + 1);
        return pretty(n.substring(0, n.lastIndexOf('.')));
    }

    public static synchronized BackItem backFor(PlayerModelMesh mesh) {
        Items3DModule module = Items3DModule.active();
        String source = BACK_OF.get(mesh);
        if (module == null || source == null || backAmount(module) <= 0) return null;
        boolean local = source.isEmpty();
        String file = local ? backFile() : source;
        if (file == null || BACK_FAILED.contains(file)) return null;
        BackItem back = BACKS.get(file);
        if (back != null) return back;
        var loading = BACK_LOADING.get(file);
        if (loading == null) {
            BACK_LOADING.put(file, java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return local ? GlbLoader.loadProp(java.nio.file.Path.of(file)) : GlbLoader.loadProp(bundledBytes(file), null);
                } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(e);
                }
            }, net.minecraft.util.Util.getMainWorkerExecutor()));
            return null;
        }
        if (!loading.isDone()) return null;
        BACK_LOADING.remove(file);
        GlbLoader.PropResult result;
        try {
            result = loading.join();
        } catch (Exception e) {
            BACK_FAILED.add(file);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            Anatorius.LOGGER.warn("[CustomModel] could not load back item {}: {}", file, cause.toString());
            if (Anatorius.CHAT_MANAGER != null) Anatorius.CHAT_MANAGER.message("CustomModel: back item could not be loaded (" + cause.getMessage() + ")");
            return null;
        }
        net.minecraft.util.Identifier[] ids = upload("back:" + file, "back item", result.textures());
        int k = 0;
        for (BackItem.Piece piece : result.item().pieces) {
            for (PlayerModelMesh.Batch batch : piece.batches) {
                int tex = result.batchTexture()[k++];
                if (tex >= 0) batch.texture = ids[tex];
            }
        }
        back = result.item();
        BACKS.put(file, back);
        Anatorius.LOGGER.info("[CustomModel] loaded back item {} ({} quads, {} pieces)", file, back.quads, back.pieces.length);
        return back;
    }

    static byte[] bundledBytes(String path) throws java.io.IOException {
        try (InputStream in = AssetVault.open(path)) {
            if (in == null) throw new java.io.FileNotFoundException(path);
            return in.readAllBytes();
        }
    }

    public static synchronized float backAmount(Items3DModule module) {
        float target = module.backShow.getValue() ? 1 : 0;
        long now = System.nanoTime();
        if (backShown < 0) backShown = target;
        else if (target > backShown) backShown = Math.min(target, backShown + (now - backStepped) / 450e6f);
        else backShown = Math.max(target, backShown - (now - backStepped) / 300e6f);
        backStepped = now;
        return backShown;
    }

    public static synchronized void clear() {
        net.minecraft.client.texture.TextureManager tm = MinecraftClient.getInstance().getTextureManager();
        for (net.minecraft.util.Identifier id : TEXTURES) tm.destroyTexture(id);
        TEXTURES.clear();
        BACK_OF.clear();
        BACKS.clear();
        BACK_LOADING.clear();
        BACK_FAILED.clear();
        backFile = null;
        backScanned = 0;
        backShown = -1;
        LOADING.clear();
        MESHES.clear();
        FAILED.clear();
    }
}
