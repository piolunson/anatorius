package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.events.impl.UpdateMovementEvent;
import dev.project.anatorius.mixins.accessors.LivingEntityAccessor;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.movement.FlightModule;
import dev.project.anatorius.settings.impl.*;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.Renderer3D;
import dev.project.anatorius.utils.minecraft.EntityUtils;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import lombok.Getter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttackRangeComponent;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractFireballEntity;
import net.minecraft.entity.projectile.AbstractWindChargeEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

@RegisterModule(name = "KillAura", description = "Automatically attacks entities if they meet specified requirements.", category = Module.Category.COMBAT)
public class KillAuraModule extends Module {
    public ModeSetting sorting = new ModeSetting("Sorting", "Which of the valid targets is attacked first.", "Distance", new String[]{"Distance", "Crosshair", "Health"});
    public NumberSetting fov = new NumberSetting("Fov", "Only targets within this many degrees of where you look. 0: only the entity under the crosshair.", 180.0, 0.0, 180.0);
    public NumberSetting delay = new NumberSetting("Delay", "How charged the attack has to be before it hits (1.0: full cooldown).", 1.0, 0.0, 1.0);
    public NumberSetting range = new NumberSetting("Range", "How far away a target you can see may be. Max: the weapon's vanilla reach.", 6.0, 1.0, 6.5).withMaxLabel("Vanilla");
    public NumberSetting wallsRange = new NumberSetting("WallsRange", "How far away a target behind blocks may be. Max: the weapon's vanilla reach.", 6.0, 0.0, 6.5).withMaxLabel("Vanilla");
    public BooleanSetting tpsSync = new BooleanSetting("TPSSync", "Waits longer for the cooldown while the server runs below 20 TPS.", false);
    public ModeSetting rotate = new ModeSetting("Rotate", "Normal: looks at the target once for each hit, then straight back to where you look. Hold: keeps looking at it. Both silent.", "Normal", new String[]{"None", "Normal", "Hold"});
    public BooleanSetting waitCrits = new BooleanSetting("WaitCrits", "While you jump, holds the hit until you fall so it lands as a critical.", true);
    public ModeSetting swing = new ModeSetting("Swing", "", "Mainhand", new String[]{"None", "Packet", "Mainhand", "Offhand", "Both"});
    public NumberSetting ticksExisted = new NumberSetting("TicksExisted", "Players only: ticks a player has to be in view before it's attacked (skips anticheat bots that pop in for a check).", 10, 0, 240);

    public CategorySetting weaponsCategory = new CategorySetting("Weapons", "Contains all of the settings relating to the weapon used.");
    public ModeSetting weapon = new ModeSetting("Weapon", "None: hits with anything. Require: only while holding a weapon. Swap: switches to the best weapon in the hotbar.", new CategorySetting.Visibility(weaponsCategory), "Swap", new String[]{"None", "Require", "Swap"});
    public ModeSetting swap = new ModeSetting("Swap", "Normal: stays on the weapon. SwapBack: back to the old slot once there's no target. Silent: only the server sees the weapon, for the hit.", new ModeSetting.Visibility(weapon, "Swap"), "Normal", new String[]{"Normal", "SwapBack", "Silent"});
    public BooleanSetting mace = new BooleanSetting("Mace", "Hits with a mace from the hotbar (silently) while falling, for smash attacks.", new CategorySetting.Visibility(weaponsCategory), true);

    public CategorySetting entitiesCategory = new CategorySetting("Entities", "Contains all of the settings relating to which entities should be attacked.");
    public BooleanSetting players = new BooleanSetting("Players", "Automatically attacks player entities.", new CategorySetting.Visibility(entitiesCategory), true);
    public BooleanSetting friends = new BooleanSetting("Friends", "Automatically attacks player entities that you have friended.", new CategorySetting.Visibility(entitiesCategory), false);
    public BooleanSetting invisibles = new BooleanSetting("Invisibles", "Attacks invisible players too.", new CategorySetting.Visibility(entitiesCategory), true);
    public BooleanSetting ignoreNakeds = new BooleanSetting("IgnoreNakeds", "Skips players without any armor.", new CategorySetting.Visibility(entitiesCategory), false);
    public BooleanSetting animals = new BooleanSetting("Animals", "Automatically attacks animal entities.", new CategorySetting.Visibility(entitiesCategory), false);
    public BooleanSetting villagers = new BooleanSetting("Villagers", "Automatically attacks villagers and wandering traders.", new CategorySetting.Visibility(entitiesCategory), false);
    public BooleanSetting hostiles = new BooleanSetting("Hostiles", "Automatically attacks hostile entities.", new CategorySetting.Visibility(entitiesCategory), true);
    public BooleanSetting neutrals = new BooleanSetting("Neutrals", "Attacks neutral mobs (zombified piglins, endermen, piglins) even when they aren't angry at you. Off: only once they are.", new CategorySetting.Visibility(entitiesCategory), false);
    public BooleanSetting ambient = new BooleanSetting("Ambient", "Automatically attacks ambient entities.", new CategorySetting.Visibility(entitiesCategory), false);
    public BooleanSetting crystals = new BooleanSetting("Crystals", "Automatically attacks end crystals.", new CategorySetting.Visibility(entitiesCategory), false);
    public BooleanSetting projectiles = new BooleanSetting("Projectiles", "Hits fireballs, wind charges and shulker bullets away.", new CategorySetting.Visibility(entitiesCategory), false);
    public BooleanSetting vehicles = new BooleanSetting("Vehicles", "Automatically attacks boats and minecarts.", new CategorySetting.Visibility(entitiesCategory), false);
    public BooleanSetting ignoreNamed = new BooleanSetting("IgnoreNamed", "Skips mobs with a custom name.", new CategorySetting.Visibility(entitiesCategory), false);

    public CategorySetting renderCategory = new CategorySetting("Render", "Contains all of the settings relating to target rendering.");
    public ModeSetting mode = new ModeSetting("Mode", "The rendering that will be applied to the target entity.", new CategorySetting.Visibility(renderCategory), "Both", new String[]{"None", "Fill", "Outline", "Both"});
    public ColorSetting fillColor = new ColorSetting("FillColor", "The color that will be used for the fill rendering.", new ModeSetting.Visibility(mode, "Fill", "Both"), ColorUtils.getDefaultFillColor());
    public ColorSetting outlineColor = new ColorSetting("OutlineColor", "The color that will be used for the outline rendering.", new ModeSetting.Visibility(mode, "Outline", "Both"), ColorUtils.getDefaultOutlineColor());

    @Getter public Entity target;

    private boolean attacking = false;
    private int restoreSlot = -1;
    private int groundedTicks = 0;

    private static final int HIT_PRIORITY = 93;

    private record Aim(Vec3d point, double distance) {}

    @Override
    public void onEnable() {
        groundedTicks = 0;
        restoreSlot = -1;
    }

    @Override
    public void onDisable() {
        target = null;
        attacking = false;
        if (mc.player != null) restoreWeaponSlot();
        restoreSlot = -1;
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        attacking = false;
        if (mc.player == null || mc.world == null || mc.player.isSpectator() || !mc.player.isAlive() || AutoMaceModule.busy()) {
            target = null;
            return;
        }
        if (weapon.getValue().equalsIgnoreCase("Require") && !isAllowedWeapon(mc.player.getMainHandStack())) {
            target = null;
            return;
        }

        Vec3d eye = mc.player.getEyePos();
        target = selectTarget(eye);
        if (target == null) {
            restoreWeaponSlot();
            return;
        }
        attacking = true;
        if (rotate.getValue().equalsIgnoreCase("None")) return;

        int slot = attackSlot(target);
        boolean hitComing = attackStrength(slot, 1.5f) >= attackDelay(slot, target)
                && !(waitCrits.getValue() && critWaitComing(target));
        if (rotate.getValue().equalsIgnoreCase("Normal") && !hitComing) return;

        Vec3d nextEye = eye.add(mc.player.getVelocity());
        float[] rotations = rotationsFrom(nextEye, closestPoint(target.getBoundingBox(), nextEye));
        int priority = hitComing ? HIT_PRIORITY : Anatorius.ROTATION_MANAGER.getModulePriority(this);
        Anatorius.ROTATION_MANAGER.rotate(rotations, this, priority);
    }

    @SubscribeEvent
    public void onUpdateMovement$POST(UpdateMovementEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.player.isSpectator() || !mc.player.isAlive()) return;
        groundedTicks = mc.player.isOnGround() ? groundedTicks + 1 : 0;
        if (target != null && !AutoMaceModule.busy()) attackTarget(target);
        if (rotate.getValue().equalsIgnoreCase("Normal")) Anatorius.ROTATION_MANAGER.cancel(this);
    }

    private void attackTarget(Entity entity) {
        if (!entity.isAlive()) return;

        int originalSlot = mc.player.getInventory().getSelectedSlot();
        int slot = originalSlot;
        boolean silent = false;

        int maceSlot = maceSlot();
        if (maceSlot != -1) {
            slot = maceSlot;
            silent = !weapon.getValue().equalsIgnoreCase("Swap") || swap.getValue().equalsIgnoreCase("Silent");
        } else if (weapon.getValue().equalsIgnoreCase("Swap")) {
            int weaponSlot = findWeaponSlot();
            boolean wanted = !(entity instanceof EndCrystalEntity)
                    || (mc.player.hasStatusEffect(StatusEffects.WEAKNESS) && !mc.player.hasStatusEffect(StatusEffects.STRENGTH));
            if (weaponSlot != -1 && wanted) {
                slot = weaponSlot;
                silent = swap.getValue().equalsIgnoreCase("Silent");
            }
        }

        ItemStack stack = mc.player.getInventory().getStack(slot);
        Aim aim = evaluate(entity, mc.player.getEyePos(), stack);
        if (aim == null) return;
        double reach = hardReach(stack);
        if (!withinServerReach(entity, reach)) return;
        if (!canAttack(entity, slot, attackStrength(slot, 0.5f))) return;

        if (!rotate.getValue().equalsIgnoreCase("None")) faceTarget(entity, aim.point(), reach);

        boolean swapped = slot != originalSlot;
        if (swapped) {
            InventoryUtils.switchSlot(silent ? "Silent" : "Normal", slot, originalSlot);
            if (!silent && swap.getValue().equalsIgnoreCase("SwapBack") && restoreSlot == -1) restoreSlot = originalSlot;
        }
        try {
            strike(entity);
        } finally {
            if (swapped && silent) InventoryUtils.switchBack("Silent", slot, originalSlot);
        }
    }

    private void faceTarget(Entity entity, Vec3d point, double reach) {
        float[] serverLook = {Anatorius.ROTATION_MANAGER.getServerYaw(), Anatorius.ROTATION_MANAGER.getServerPitch()};
        if (lookingAt(entity, serverLook, reach)) return;
        float[] rotations = rotationsFrom(mc.player.getEyePos(), point);
        Anatorius.ROTATION_MANAGER.packetRotate(rotations[0], rotations[1]);
    }

    private void strike(Entity entity) {
        boolean gliding = mc.player.isGliding();
        boolean crystal = entity instanceof EndCrystalEntity;
        boolean sprinting = mc.player.isSprinting() && !gliding && !crystal;
        boolean stop = sprinting || (Anatorius.POSITION_MANAGER.isServerSprinting() && !gliding && !crystal);
        if (stop) mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        if (sprinting) mc.player.setSprinting(false);

        mc.interactionManager.attackEntity(mc.player, entity);

        switch (swing.getValue()) {
            case "Packet" -> mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            case "Mainhand" -> mc.player.swingHand(Hand.MAIN_HAND);
            case "Offhand" -> mc.player.swingHand(Hand.OFF_HAND);
            case "Both" -> {
                mc.player.swingHand(Hand.MAIN_HAND);
                mc.player.swingHand(Hand.OFF_HAND);
            }
        }

        if (sprinting) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            mc.player.setSprinting(true);
        }
    }

    private float attackStrength(int slot, float partial) {
        if (tpsSync.getValue()) partial -= 20.0f - Anatorius.SERVER_MANAGER.getTickRate();
        int ticks = ((LivingEntityAccessor) mc.player).getTicksSinceLastAttack();
        float period;
        if (slot == mc.player.getInventory().getSelectedSlot()) {
            period = mc.player.getAttackCooldownProgressPerTick();
        } else {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            double base = mc.player.getAttributeBaseValue(EntityAttributes.ATTACK_SPEED);
            double speed = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT)
                    .applyOperations(EntityAttributes.ATTACK_SPEED, base, EquipmentSlot.MAINHAND);
            period = speed <= 0.0 ? 20.0f : (float) (20.0 / speed);
        }
        return MathHelper.clamp((ticks + partial) / period, 0.0f, 1.0f);
    }

    private float attackDelay(int slot, Entity entity) {
        if (mace.getValue() && smashing() && mc.player.getInventory().getStack(slot).isOf(Items.MACE)) {
            return slot == mc.player.getInventory().getSelectedSlot() ? 0.1f : 0.25f;
        }
        return delay.getValue().floatValue();
    }

    private boolean canAttack(Entity entity, int slot, float strength) {
        if (entity instanceof EndCrystalEntity) return true;
        if (waitCrits.getValue() && shouldWaitForCritical(entity)) return false;

        ItemStack stack = mc.player.getInventory().getStack(slot);
        float minimumCharge = stack.getOrDefault(DataComponentTypes.MINIMUM_ATTACK_CHARGE, 0.0f);
        if (minimumCharge > 0.0f && strength < minimumCharge) return false;

        if (!(entity instanceof PlayerEntity) && entity instanceof LivingEntity living
                && living.getHealth() + living.getAbsorptionAmount() <= 1.0f && living.getMaxHealth() <= 4.0f) {
            return strength >= 0.2f;
        }
        return strength >= attackDelay(slot, entity);
    }

    private boolean shouldWaitForCritical(Entity entity) {
        if (!critsApply(entity)) return false;
        boolean onGround = mc.player.isOnGround();
        if (!onGround && mc.player.fallDistance <= 0.0) return true;
        return onGround && groundedTicks <= 1;
    }

    private boolean critWaitComing(Entity entity) {
        if (!critsApply(entity)) return false;
        if (mc.player.isOnGround()) {
            return mc.options.jumpKey.isPressed() || groundedTicks == 0;
        }
        return mc.player.fallDistance <= 0.0 && mc.player.getVelocity().y > 0.0;
    }

    private boolean critsApply(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        FlightModule flight = Anatorius.MODULE_MANAGER.getModule(FlightModule.class);
        if ((flight != null && flight.isToggled()) || mc.player.getAbilities().flying || mc.player.isGliding()
                || mc.player.isClimbing() || mc.player.isTouchingWater() || mc.player.isInLava()
                || mc.player.hasStatusEffect(StatusEffects.LEVITATION) || mc.player.hasVehicle() || EntityUtils.isInWeb(mc.player)) {
            return false;
        }
        CriticalsModule criticals = Anatorius.MODULE_MANAGER.getModule(CriticalsModule.class);
        return criticals == null || !criticals.isToggled() || !criticals.mode.getValue().equalsIgnoreCase("Grim");
    }

    private int attackSlot(Entity entity) {
        int maceSlot = maceSlot();
        if (maceSlot != -1) return maceSlot;
        if (weapon.getValue().equalsIgnoreCase("Swap") && !(entity instanceof EndCrystalEntity)) {
            int weaponSlot = findWeaponSlot();
            if (weaponSlot != -1) return weaponSlot;
        }
        return mc.player.getInventory().getSelectedSlot();
    }

    private static boolean fakeFlying() {
        dev.project.anatorius.modules.impl.movement.FakeFlyModule fakeFly = Anatorius.MODULE_MANAGER.getModule(dev.project.anatorius.modules.impl.movement.FakeFlyModule.class);
        return fakeFly != null && fakeFly.isToggled();
    }

    private boolean smashing() {
        return mc.player.fallDistance > 1.5 && !mc.player.isGliding();
    }

    public boolean maceComing() {
        return isToggled() && target != null && mace.getValue() && mc.player != null
                && InventoryUtils.find(Items.MACE, InventoryUtils.HOTBAR_START, InventoryUtils.HOTBAR_END) != -1;
    }

    private int maceSlot() {
        if (!mace.getValue() || !(smashing() || fakeFlying())) return -1;
        return InventoryUtils.find(Items.MACE, InventoryUtils.HOTBAR_START, InventoryUtils.HOTBAR_END);
    }

    private int findWeaponSlot() {
        int slot = InventoryUtils.findBestSword(InventoryUtils.HOTBAR_START, InventoryUtils.HOTBAR_END);
        if (slot == -1) slot = InventoryUtils.find(stack -> stack.isIn(ItemTags.AXES), InventoryUtils.HOTBAR_START, InventoryUtils.HOTBAR_END);
        if (slot == -1) slot = InventoryUtils.find(stack -> stack.isIn(ItemTags.SPEARS), InventoryUtils.HOTBAR_START, InventoryUtils.HOTBAR_END);
        return slot;
    }

    private boolean isAllowedWeapon(ItemStack stack) {
        if (stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.SPEARS)
                || stack.isOf(Items.TRIDENT) || stack.isOf(Items.MACE)) {
            return true;
        }
        if (!stack.isIn(ItemTags.PICKAXES)) return false;
        return InventoryUtils.find(s -> s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES), InventoryUtils.HOTBAR_START, InventoryUtils.HOTBAR_END) == -1;
    }

    private void restoreWeaponSlot() {
        if (restoreSlot == -1) return;
        if (weapon.getValue().equalsIgnoreCase("Swap") && swap.getValue().equalsIgnoreCase("SwapBack")) {
            int current = mc.player.getInventory().getSelectedSlot();
            if (current != restoreSlot) InventoryUtils.switchSlot("Normal", restoreSlot, current);
        }
        restoreSlot = -1;
    }

    private Entity selectTarget(Vec3d eye) {
        double reach = Math.max(range.getValue().doubleValue(), wallsRange.getValue().doubleValue()) + 1.0;
        ItemStack stack = mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot());
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity entity : mc.world.getOtherEntities(mc.player, new Box(eye, eye).expand(reach), e -> true)) {
            if (!withinFov(entity)) continue;
            Aim aim = evaluate(entity, eye, stack);
            if (aim == null) continue;
            double score = score(entity, aim);
            if (best == null || score < bestScore) {
                best = entity;
                bestScore = score;
            }
        }
        return best;
    }

    private double score(Entity entity, Aim aim) {
        return switch (sorting.getValue()) {
            case "Crosshair" -> Math.abs(MathHelper.wrapDegrees(rotationsFrom(mc.player.getEyePos(), aim.point())[0] - mc.player.getYaw()));
            case "Health" -> entity instanceof LivingEntity living ? living.getHealth() + living.getAbsorptionAmount() : Double.MAX_VALUE;
            default -> aim.distance();
        };
    }

    private boolean withinFov(Entity entity) {
        double fovValue = fov.getValue().doubleValue();
        if (fovValue >= 180.0) return true;
        if (fovValue <= 0.0) return entity == mc.targetedEntity;
        float[] rotations = rotationsFrom(mc.player.getEyePos(), closestPoint(entity.getBoundingBox(), mc.player.getEyePos()));
        return Math.abs(MathHelper.wrapDegrees(rotations[0] - mc.player.getYaw())) <= fovValue
                && Math.abs(rotations[1] - mc.player.getPitch()) <= fovValue;
    }

    private boolean isValidTarget(Entity entity, Vec3d eye) {
        return evaluate(entity, eye, mc.player.getMainHandStack()) != null;
    }

    private Aim evaluate(Entity entity, Vec3d eye, ItemStack weapon) {
        if (entity == null || entity == mc.player || !entity.isAlive() || entity.isSpectator() || !entity.isAttackable()) return null;
        if (entity instanceof LivingEntity living && (living.getHealth() <= 0.0f || living.deathTime > 0)) return null;
        if (entity == mc.player.getVehicle()) return null;
        if (!isValidEntity(entity)) return null;
        if (entity instanceof PlayerEntity player) {
            if (player.age < ticksExisted.getValue().intValue()) return null;
            if (!friends.getValue() && Anatorius.FRIEND_MANAGER.contains(player.getName().getString())) return null;
            if (player.isInvisible() && !invisibles.getValue()) return null;
            if (ignoreNakeds.getValue() && !hasArmor(player)) return null;
        } else if (ignoreNamed.getValue() && entity.hasCustomName()) {
            return null;
        }
        if (!mc.world.getWorldBorder().contains(entity.getBlockPos())) return null;

        Box box = entity.getBoundingBox();
        Vec3d point = closestPoint(box, eye);
        double distance = eye.distanceTo(point);
        double visibleLimit = limitFor(range, weapon);
        double wallLimit = limitFor(wallsRange, weapon);
        if (distance > Math.max(visibleLimit, wallLimit)) return null;
        boolean visible = Math.abs(visibleLimit - wallLimit) <= 0.01 || canSee(box, eye);
        if (distance > (visible ? visibleLimit : wallLimit)) return null;
        return new Aim(distance < 0.05 ? box.getCenter() : point, distance);
    }

    private boolean isValidEntity(Entity entity) {
        if (entity instanceof PlayerEntity) return players.getValue();
        boolean neutral = entity instanceof EndermanEntity || entity instanceof ZombifiedPiglinEntity || entity instanceof PiglinEntity;
        if (neutral) {
            if (neutrals.getValue()) return true;
            boolean angry = entity instanceof EndermanEntity enderman ? enderman.isAngry() : ((MobEntity) entity).isAttacking();
            return hostiles.getValue() && angry;
        }
        if (entity instanceof MerchantEntity) return villagers.getValue();
        if (entity instanceof EndCrystalEntity) return crystals.getValue();
        if (entity instanceof ShulkerBulletEntity || entity instanceof AbstractFireballEntity || entity instanceof AbstractWindChargeEntity) return projectiles.getValue();
        if (entity instanceof VehicleEntity) return vehicles.getValue();

        SpawnGroup group = entity.getType().getSpawnGroup();
        if (group == SpawnGroup.MONSTER) return hostiles.getValue();
        if (group == SpawnGroup.CREATURE || group == SpawnGroup.WATER_CREATURE || group == SpawnGroup.WATER_AMBIENT
                || group == SpawnGroup.UNDERGROUND_WATER_CREATURE || group == SpawnGroup.AXOLOTLS) {
            return animals.getValue();
        }
        if (group == SpawnGroup.AMBIENT) return ambient.getValue();
        return false;
    }

    private static boolean hasArmor(PlayerEntity player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!player.getEquippedStack(slot).isEmpty()) return true;
        }
        return false;
    }

    private double limitFor(NumberSetting setting, ItemStack weapon) {
        double hard = hardReach(weapon);
        if (setting.isAtMaxLabel()) return Math.min(vanillaReach(weapon), hard);
        return Math.min(setting.getValue().doubleValue(), hard);
    }

    private double vanillaReach(ItemStack weapon) {
        AttackRangeComponent attackRange = weapon.getOrDefault(DataComponentTypes.ATTACK_RANGE, AttackRangeComponent.defaultForEntity(mc.player));
        return attackRange.getEffectiveMaxRange(mc.player);
    }

    private double hardReach(ItemStack weapon) {
        AttackRangeComponent attackRange = weapon.getOrDefault(DataComponentTypes.ATTACK_RANGE, AttackRangeComponent.defaultForEntity(mc.player));
        return attackRange.getEffectiveMaxRange(mc.player) + attackRange.hitboxMargin() + 3.0 - 0.15;
    }

    private boolean withinServerReach(Entity entity, double hard) {
        Box box = entity.getBoundingBox();
        Vec3d eye = mc.player.getEyePos();
        Vec3d standing = new Vec3d(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(EntityPose.STANDING), mc.player.getZ());
        return Math.sqrt(box.squaredMagnitude(eye)) <= hard && Math.sqrt(box.squaredMagnitude(standing)) <= hard;
    }

    private boolean canSee(Box box, Vec3d eye) {
        Vec3d center = box.getCenter();
        for (double fraction = 0.0; fraction <= 1.0; fraction += 0.5) {
            double y = box.minY + (box.maxY - box.minY) * fraction;
            Vec3d[] corners = {
                    new Vec3d(box.minX, y, box.minZ), new Vec3d(box.maxX, y, box.minZ),
                    new Vec3d(box.minX, y, box.maxZ), new Vec3d(box.maxX, y, box.maxZ)
            };
            java.util.Arrays.sort(corners, java.util.Comparator.comparingDouble(eye::squaredDistanceTo));
            for (int i = 0; i < corners.length - 1; i++) {
                Vec3d point = corners[i].add(center.subtract(corners[i]).normalize().multiply(0.03));
                if (mc.world.raycast(new RaycastContext(eye, point, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Vec3d closestPoint(Box box, Vec3d from) {
        return new Vec3d(MathHelper.clamp(from.x, box.minX, box.maxX), MathHelper.clamp(from.y, box.minY, box.maxY), MathHelper.clamp(from.z, box.minZ, box.maxZ));
    }

    private static float[] rotationsFrom(Vec3d eyePos, Vec3d vec) {
        double deltaX = vec.x - eyePos.x;
        double deltaY = vec.y - eyePos.y;
        double deltaZ = vec.z - eyePos.z;
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        return new float[]{
                (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0),
                (float) MathHelper.clamp(MathHelper.wrapDegrees(-Math.toDegrees(Math.atan2(deltaY, distance))), -90.0, 90.0)
        };
    }

    private boolean lookingAt(Entity entity, float[] rotations, double reach) {
        Vec3d eye = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        if (box.contains(eye)) return true;
        float yaw = rotations[0] * MathHelper.RADIANS_PER_DEGREE, pitch = rotations[1] * MathHelper.RADIANS_PER_DEGREE;
        Vec3d direction = new Vec3d(-MathHelper.sin(yaw) * MathHelper.cos(pitch), -MathHelper.sin(pitch), MathHelper.cos(yaw) * MathHelper.cos(pitch));
        return box.raycast(eye, eye.add(direction.multiply(reach))).isPresent();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (target == null || !attacking) return;
        if (mode.getValue().equalsIgnoreCase("None")) return;

        Vec3d vec3d = EntityUtils.getRenderPos(target, event.getTickDelta());
        Box box = new Box(vec3d.x - target.getBoundingBox().getLengthX() / 2, vec3d.y, vec3d.z - target.getBoundingBox().getLengthZ() / 2, vec3d.x + target.getBoundingBox().getLengthX() / 2, vec3d.y + target.getBoundingBox().getLengthY(), vec3d.z + target.getBoundingBox().getLengthZ() / 2);

        if (mode.getValue().equalsIgnoreCase("Fill") || mode.getValue().equalsIgnoreCase("Both")) Renderer3D.renderBox(event.getMatrices(), box, fillColor.getColor());
        if (mode.getValue().equalsIgnoreCase("Outline") || mode.getValue().equalsIgnoreCase("Both")) Renderer3D.renderBoxOutline(event.getMatrices(), box, outlineColor.getColor());
    }

    @Override
    public String getMetaData() {
        return target == null ? "None" : target.getName().getString();
    }
}
