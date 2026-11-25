package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.Renderer3D;
import dev.project.anatorius.utils.minecraft.DamageUtils;
import dev.project.anatorius.utils.minecraft.EntityUtils;
import dev.project.anatorius.utils.minecraft.HoleUtils;
import dev.project.anatorius.utils.minecraft.PositionUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RegisterModule(name = "CityESP", description = "Highlights enemies' minable surround blocks and burrows.", category = Module.Category.VISUALS)
public class CityESPModule extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Maximum distance to the players that are checked.", 12, 4, 30);
    public final BooleanSetting friends = new BooleanSetting("Friends", "Also show friends' weak spots.", false);
    public final BooleanSetting surroundedOnly = new BooleanSetting("SurroundedOnly", "Only players whose feet are fully surrounded.", true);
    public final BooleanSetting city = new BooleanSetting("City", "Highlight the minable surround blocks.", true);
    public final BooleanSetting burrow = new BooleanSetting("Burrow", "Highlight the block a player is burrowed in.", true);
    public final BooleanSetting damage = new BooleanSetting("Damage", "Show the crystal damage a mined city block would allow.", true);
    public final ModeSetting mode = new ModeSetting("Mode", "The rendering applied to the boxes.", "Both", new String[]{"Fill", "Outline", "Both"});
    public final ColorSetting cityColor = new ColorSetting("CityColor", "Minable surround block.", ColorUtils.palette(ColorUtils.THEME_CAUTION, 55));
    public final ColorSetting crystalColor = new ColorSetting("CrystalColor", "Minable block that leaves room for a crystal.", ColorUtils.palette(ColorUtils.THEME_DANGER, 75));
    public final ColorSetting burrowColor = new ColorSetting("BurrowColor", "Block a player is burrowed in.", ColorUtils.palette(ColorUtils.THEME_ORCHID, 70));

    private enum Kind { CITY, CRYSTAL, BURROW }

    private record Mark(BlockPos pos, Kind kind, float damage) { }

    private volatile List<Mark> marks = List.of();

    @Override
    public void onDisable() {
        marks = List.of();
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (getNull()) return;
        List<Mark> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        double max = range.getValue().doubleValue();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isDead() || player.isSpectator()) continue;
            if (mc.player.squaredDistanceTo(player) > max * max) continue;
            if (EntityUtils.isBot(player)) continue;
            if (!friends.getValue() && Anatorius.FRIEND_MANAGER.contains(player.getName().getString())) continue;

            if (burrow.getValue()) {
                for (BlockPos inside : HoleUtils.getInsidePositions(player)) {
                    BlockState state = mc.world.getBlockState(inside);
                    if (!state.isReplaceable() && !state.getCollisionShape(mc.world, inside).isEmpty() && seen.add(inside)) {
                        found.add(new Mark(inside.toImmutable(), Kind.BURROW, 0f));
                    }
                }
            }

            if (city.getValue()) collectCity(player, found, seen);
        }
        marks = found;
    }

    private void collectCity(PlayerEntity player, List<Mark> found, Set<BlockPos> seen) {
        int feetY = PositionUtils.getFlooredPosition(player).getY();
        List<BlockPos> surround = new ArrayList<>();
        for (BlockPos pos : HoleUtils.getFeetPositions(player, true, false, true)) {
            if (pos.getY() != feetY) continue;
            BlockState state = mc.world.getBlockState(pos);
            if (state.isReplaceable()) {
                if (surroundedOnly.getValue()) return;
                continue;
            }
            surround.add(pos);
        }

        for (BlockPos pos : surround) {
            BlockState state = mc.world.getBlockState(pos);
            if (state.getHardness(mc.world, pos) < 0) continue;
            if (!seen.add(pos)) continue;

            BlockState base = mc.world.getBlockState(pos.down());
            boolean crystal = base.isOf(Blocks.OBSIDIAN) || base.isOf(Blocks.BEDROCK);
            float dmg = 0f;
            if (crystal && damage.getValue()) {
                dmg = DamageUtils.getCrystalDamage(player, player.getBoundingBox(), pos.down(), pos, false);
            }
            found.add(new Mark(pos.toImmutable(), crystal ? Kind.CRYSTAL : Kind.CITY, dmg));
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        List<Mark> list = marks;
        if (getNull() || list.isEmpty()) return;
        boolean fill = !mode.getValue().equals("Outline");
        boolean outline = !mode.getValue().equals("Fill");

        for (Mark mark : list) {
            Color color = switch (mark.kind) {
                case CITY -> cityColor.getColor();
                case CRYSTAL -> crystalColor.getColor();
                case BURROW -> burrowColor.getColor();
            };
            Box box = new Box(mark.pos);
            if (fill) Renderer3D.renderBox(event.getMatrices(), box, color);
            if (outline) Renderer3D.renderBoxOutline(event.getMatrices(), box, ColorUtils.getColor(color, Math.min(255, color.getAlpha() * 3)));
            if (mark.kind == Kind.CRYSTAL && damage.getValue() && mark.damage > 0f && Renderer3D.isFrustumVisible(box)) {
                Renderer3D.renderScaledText(event.getMatrices(), String.format(Locale.ROOT, "%.1f", mark.damage),
                        mark.pos.getX() + 0.5, mark.pos.getY() + 0.6, mark.pos.getZ() + 0.5, 25, true, Color.WHITE);
            }
        }
    }

    public int[] counts() {
        int[] c = new int[3];
        for (Mark mark : marks) c[mark.kind.ordinal()]++;
        return c;
    }

    @Override
    public String getMetaData() {
        return String.valueOf(marks.size());
    }
}
