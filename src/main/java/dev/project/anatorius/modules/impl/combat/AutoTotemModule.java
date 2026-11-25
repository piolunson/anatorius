package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PlayerPopEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.player.SpeedMineModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.DamageUtils;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@RegisterModule(name = "AutoTotem", description = "Automatically puts a specified item in your offhand slot.", category = Module.Category.COMBAT)
public class AutoTotemModule extends Module {
    public ModeSetting item = new ModeSetting("Item", "The item that will be placed in your offhand slot when safety conditions are met.", "Totem", new String[]{"Totem", "Crystal", "Gapple"});
    public NumberSetting health = new NumberSetting("Health", "The health at which a totem will be prioritized. At or below it the offhand gapple (UseGapple) is never swapped in, so the totem stays.", 16, 0, 36);
    public BooleanSetting elytraCheck = new BooleanSetting("ElytraCheck", "Prioritizes a totem whenever you're wearing an elytra.", true);
    public NumberSetting fallDistance = new NumberSetting("FallDistance", "The fall distance at which the module will prioritize a totem.", 20.0f, 0.0f, 80.0f);
    public BooleanSetting useGapple = new BooleanSetting("UseGapple", "Switches to a golden apple in your offhand when holding right click with a sword, axe or pickaxe.", true);
    public BooleanSetting lethalOverride = new BooleanSetting("LethalOverride", "Right-click gappling overrides the other totem reasons (fall, elytra, FeetTrap, AntiMace). Never low health: below Health the totem always stays.", new BooleanSetting.Visibility(useGapple, true), false);
    public BooleanSetting tickAbort = new BooleanSetting("TickAbort", "Enable the interval between switching item which is determine by player ping", true);
    public BooleanSetting smartMine = new BooleanSetting("SmartMine", "Switches to a crystal whenever you start mining and a totem when you aren't mining.", new ModeSetting.Visibility(item, "Crystal"), false);
    public BooleanSetting antiMace = new BooleanSetting("AntiMace", "Switches to a totem if a player near you is trying to smash attack you with a mace.", false);
    public NumberSetting maceRange = new NumberSetting("MaceRange", "The distance at which an enemy has to be in with a mace in order to swap to a totem.", new BooleanSetting.Visibility(antiMace, true), 12.0f, 0.0f, 24.0f);
    public BooleanSetting feetTrapCheck = new BooleanSetting("FeetTrapCheck", "Prioritizes a totem when FeetTrap has unfilled positions.", true);

    private int totemCount = 0;

    private int cooldownTicks = 0;

    private Item lastSwappedItem = null;

    private long totemHoldUntil = 0L;
    private static final long TOTEM_HOLD_MS = 400L;

    private long lethalHoldUntil = 0L;
    private static final long LETHAL_HOLD_MS = 750L;
    private static final double LETHAL_RANGE_SQ = 144.0;

    @SubscribeEvent
    public void onPlayerPop(PlayerPopEvent event) {
        if (event.getPlayer() == mc.player
                && !AutoCrystalModule.suiciding()) {
            mc.execute(this::handleSelfPop);
        }
    }

    private void handleSelfPop() {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            mc.player.getMainHandStack().decrement(1);
        } else if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            mc.player.getOffHandStack().decrement(1);
        }

        cooldownTicks   = 0;
        lastSwappedItem = null;
        totemHoldUntil  = System.currentTimeMillis() + TOTEM_HOLD_MS;
        processTotem();
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (lethalCheckActive() && hasLethalCrystal()) {
            lethalHoldUntil = System.currentTimeMillis() + LETHAL_HOLD_MS;
        }

        if (cooldownTicks > 0 && tickAbort.getValue()) {
            if (!needsTotem()) {
                cooldownTicks--;
                return;
            } else {
                cooldownTicks = 0;
            }
        }

        processTotem();
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (event.getPacket() instanceof HealthUpdateS2CPacket packet) {
            if (packet.getHealth() + mc.player.getAbsorptionAmount() <= health.getValue().floatValue()) {
                cooldownTicks = 0;
                processTotem();
            }
        } else if (event.getPacket() instanceof EntityStatusS2CPacket packet) {
            if (packet.getEntity(mc.world) == mc.player) {
                cooldownTicks = 0;
                processTotem();
            }
        } else if (event.getPacket() instanceof EntitySpawnS2CPacket packet && packet.getEntityType() == EntityType.END_CRYSTAL) {
            Vec3d crystalPos = new Vec3d(packet.getX(), packet.getY(), packet.getZ());
            double dist = mc.player.squaredDistanceTo(crystalPos);
            if (dist > LETHAL_RANGE_SQ) return;
            mc.execute(() -> {
                if (mc.player == null || mc.world == null) return;
                boolean lethal = lethalCheckActive() && isCrystalLethal(crystalPos);
                if (lethal) lethalHoldUntil = System.currentTimeMillis() + LETHAL_HOLD_MS;
                if (lethal || dist <= 36.0) {
                    cooldownTicks = 0;
                    processTotem();
                }
            });
        }
    }

    private boolean lethalCheckActive() {
        return !mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)
                && !AutoCrystalModule.suicideOffhandOverride();
    }

    private boolean isCrystalLethal(Vec3d crystalPos) {
        if (mc.player.squaredDistanceTo(crystalPos) > LETHAL_RANGE_SQ) return false;
        float damage = DamageUtils.getDamage(mc.player, mc.player.getBoundingBox(), crystalPos, 6.0f, null, false);
        return damage >= mc.player.getHealth() + mc.player.getAbsorptionAmount();
    }

    private boolean hasLethalCrystal() {
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity && isCrystalLethal(entity.getEntityPos())) return true;
        }
        return false;
    }

    private boolean lethalCrystalHeld() {
        return System.currentTimeMillis() < lethalHoldUntil && lethalCheckActive();
    }

    private void processTotem() {
        if (!mc.isOnThread()) {
            mc.execute(this::processTotem);
            return;
        }
        if (mc.player == null || mc.world == null) return;

        if (!(mc.currentScreen instanceof InventoryScreen)
                && mc.currentScreen instanceof HandledScreen<?>) return;

        Item wantedItem = getItem();
        if (wantedItem == null) return;

        if (mc.player.getOffHandStack().getItem() == wantedItem) {
            lastSwappedItem = wantedItem;
            return;
        }

        if (wantedItem == lastSwappedItem && !needsTotem()) return;

        int slot = findSlot(wantedItem);
        if (slot == -1) return;

        if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            InventoryUtils.swap("Swap", slot, 40);
        } else {
            InventoryUtils.swap("Pickup", slot, 45);
        }
        lastSwappedItem = wantedItem;

        cooldownTicks = needsTotem()
                ? 1
                : Math.max(1, 1 + Anatorius.SERVER_MANAGER.getPingDelay());
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;
        totemCount = mc.player.getInventory().count(Items.TOTEM_OF_UNDYING);

        if (mc.player.getOffHandStack().getItem() != lastSwappedItem) {
            lastSwappedItem = null;
        }
    }

    private int findSlot(Item wantedItem) {
        if (wantedItem == Items.TOTEM_OF_UNDYING
                && AutoCrystalModule.suicideOffhandOverride()) {
            if (mc.player.getOffHandStack().isEmpty()) return -1;
            return findFirstEmpty(InventoryUtils.HOTBAR_START, InventoryUtils.INVENTORY_END);
        }

        int slot = findFirstItem(wantedItem, InventoryUtils.INVENTORY_START, InventoryUtils.INVENTORY_END);

        if (slot == -1) slot = findFirstItem(wantedItem, InventoryUtils.HOTBAR_START, InventoryUtils.HOTBAR_END);

        if (slot == -1 && wantedItem == Items.TOTEM_OF_UNDYING) {
            slot = findFirstEmpty(InventoryUtils.HOTBAR_START, InventoryUtils.INVENTORY_END);
        }

        return slot;
    }

    private int findFirstItem(net.minecraft.item.Item item, int start, int end) {
        for (int i = start; i <= end; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private int findFirstEmpty(int start, int end) {
        for (int i = start; i <= end; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private Item getItem() {
        boolean crit = needsTotem();

        if (useGapple.getValue()
                && mc.options.useKey.isPressed()
                && !lowHealth()
                && !lethalCrystalHeld()
                && (lethalOverride.getValue() || !crit)
                && (mc.player.getMainHandStack().isIn(ItemTags.SWORDS)
                || mc.player.getMainHandStack().getItem() instanceof AxeItem
                || mc.player.getMainHandStack().isIn(ItemTags.PICKAXES))
                && hasItem(Items.ENCHANTED_GOLDEN_APPLE)) {
            return Items.ENCHANTED_GOLDEN_APPLE;
        }

        long now = System.currentTimeMillis();
        if (crit) totemHoldUntil = now + TOTEM_HOLD_MS;

        if (crit || now < totemHoldUntil) {
            return hasItem(Items.TOTEM_OF_UNDYING) ? Items.TOTEM_OF_UNDYING : null;
        }

        if (item.getValue().equalsIgnoreCase("Crystal") && smartMine.getValue()) {
            SpeedMineModule module = Anatorius.MODULE_MANAGER.getModule(SpeedMineModule.class);
            boolean mining = (module.getPrimary() != null && module.getPrimary().isMining())
                    || (module.getSecondary() != null && module.getSecondary().isMining());
            if (!mining) {
                return hasItem(Items.TOTEM_OF_UNDYING) ? Items.TOTEM_OF_UNDYING : null;
            }
        }

        return switch (item.getValue()) {
            case "Crystal" -> hasItem(Items.END_CRYSTAL)
                    ? Items.END_CRYSTAL
                    : (hasItem(Items.TOTEM_OF_UNDYING) ? Items.TOTEM_OF_UNDYING : null);
            case "Gapple" -> hasItem(Items.ENCHANTED_GOLDEN_APPLE)
                    ? Items.ENCHANTED_GOLDEN_APPLE
                    : (hasItem(Items.TOTEM_OF_UNDYING) ? Items.TOTEM_OF_UNDYING : null);
            default -> hasItem(Items.TOTEM_OF_UNDYING) ? Items.TOTEM_OF_UNDYING : null;
        };
    }

    private boolean needsTotem() {
        if (AutoCrystalModule.suicideOffhandOverride()) {
            return false;
        }

        if (lowHealth()) return true;

        if (lethalCrystalHeld()) return true;

        if (mc.player.fallDistance > fallDistance.getValue().floatValue()) return true;

        if (elytraCheck.getValue()
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) return true;

        if (feetTrapCheck.getValue()) {
            FeetTrapModule feetTrap = Anatorius.MODULE_MANAGER.getModule(FeetTrapModule.class);
            if (feetTrap != null && feetTrap.isToggled() && feetTrap.hasUnfilledPositions()) return true;
        }

        if (antiMace.getValue()) {
            return mc.world.getPlayers().stream().anyMatch(entity ->
                    entity != mc.player
                            && !Anatorius.FRIEND_MANAGER.contains(entity.getName().getString())
                            && mc.player.squaredDistanceTo(entity) <= MathHelper.square(maceRange.getValue().floatValue())
                            && entity.fallDistance >= 1.5f
                            && entity.getMainHandStack().getItem().equals(Items.MACE));
        }

        return false;
    }

    private boolean lowHealth() {
        return mc.player.getHealth() + mc.player.getAbsorptionAmount() <= health.getValue().floatValue();
    }

    private boolean hasItem(Item item) {
        return InventoryUtils.find(item) != -1 || mc.player.getOffHandStack().getItem() == item;
    }

    @Override
    public String getMetaData() {
        return String.valueOf(totemCount);
    }
}
