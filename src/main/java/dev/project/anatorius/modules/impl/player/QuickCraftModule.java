package dev.project.anatorius.modules.impl.player;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.settings.impl.WhitelistSetting;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.RecipeFinder;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.context.ContextParameterMap;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

@RegisterModule(name = "QuickCraft", description = "Crafts the items you pick from your inventory, through the recipe book.", category = Module.Category.PLAYER)
public class QuickCraftModule extends Module {
    public WhitelistSetting items = new WhitelistSetting("Items", "What to craft. Empty: nothing.", WhitelistSetting.Type.ITEMS);
    public NumberSetting delay = new NumberSetting("Delay", "Milliseconds between steps (fill the grid, take the result).", 100, 0, 1000);
    public BooleanSetting wholeStack = new BooleanSetting("WholeStack", "Fills the grid for as many crafts as your ingredients allow at once.", true);
    public ModeSetting output = new ModeSetting("Output", "Keep: the result goes into your inventory. Drop: it is thrown on the ground.", "Keep", new String[]{"Keep", "Drop"});
    public NumberSetting stopAt = new NumberSetting("StopAt", "Stops crafting an item once you carry this many of it (0: never stop).", 0, 0, 2304);
    public BooleanSetting inventoryGrid = new BooleanSetting("InventoryGrid", "Crafts 2x2 recipes in your inventory grid, no table needed.", true);

    public CategorySetting tableCategory = new CategorySetting("Table", "Crafting tables for 3x3 recipes.");
    public BooleanSetting openTable = new BooleanSetting("OpenTable", "Opens a crafting table near you when a 3x3 recipe is ready to make.", false);
    public NumberSetting tableRange = new NumberSetting("TableRange", "How far away a table may be, in blocks.", new BooleanSetting.Visibility(openTable, true), 4.0, 1.0, 6.0);
    public BooleanSetting closeWhenDone = new BooleanSetting("CloseWhenDone", "Closes a table it opened once there's nothing left to craft.", new BooleanSetting.Visibility(openTable, true), true);

    private final Timer timer = new Timer();
    private final Timer openTimer = new Timer();
    private boolean openedTable;

    @Override
    public void onEnable() {
        openedTable = false;
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (items.getWhitelist().isEmpty()) return;

        ScreenHandler handler = mc.player.currentScreenHandler;
        boolean table = handler instanceof CraftingScreenHandler;
        boolean inventory = handler == mc.player.playerScreenHandler
                && (mc.currentScreen == null || mc.currentScreen instanceof InventoryScreen);
        if (!table && !inventory) return;
        if (!timer.hasTimeElapsed(delay.getValue().longValue())) return;

        ItemStack result = handler.getSlot(0).getStack();
        if (!result.isEmpty() && items.isWhitelistContains(result.getItem())) {
            boolean drop = output.getValue().equalsIgnoreCase("Drop");
            mc.interactionManager.clickSlot(handler.syncId, 0, drop ? 1 : 0, drop ? SlotActionType.THROW : SlotActionType.QUICK_MOVE, mc.player);
            timer.reset();
            return;
        }

        RecipeDisplayEntry recipe = inventory && !inventoryGrid.getValue() ? null : findRecipe(table ? 3 : 2);
        if (recipe != null) {
            mc.interactionManager.clickRecipe(handler.syncId, recipe.id(), wholeStack.getValue());
            timer.reset();
            return;
        }

        if (table) {
            if (openedTable && closeWhenDone.getValue()) {
                mc.player.closeHandledScreen();
                openedTable = false;
            }
        } else if (openTable.getValue() && mc.currentScreen == null && findRecipe(3) != null) {
            openNearbyTable();
        }
    }

    private RecipeDisplayEntry findRecipe(int grid) {
        RecipeFinder finder = new RecipeFinder();
        mc.player.getInventory().populateRecipeFinder(finder);
        ContextParameterMap context = SlotDisplayContexts.createParameters(mc.world);

        for (RecipeResultCollection collection : mc.player.getRecipeBook().getOrderedResults()) {
            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                RecipeDisplay display = entry.display();
                if (!fits(display, grid)) continue;
                ItemStack made = display.result().getFirst(context);
                if (made.isEmpty() || !items.isWhitelistContains(made.getItem())) continue;
                int limit = stopAt.getValue().intValue();
                if (limit > 0 && carried(made.getItem()) >= limit) continue;
                if (entry.isCraftable(finder)) return entry;
            }
        }
        return null;
    }

    private static boolean fits(RecipeDisplay display, int grid) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) return shaped.width() <= grid && shaped.height() <= grid;
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) return shapeless.ingredients().size() <= grid * grid;
        return false;
    }

    private int carried(Item item) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(item)) count += stack.getCount();
        }
        return count;
    }

    private void openNearbyTable() {
        if (!openTimer.hasTimeElapsed(1000L)) return;
        openTimer.reset();

        double range = tableRange.getValue().doubleValue();
        int radius = (int) Math.ceil(range);
        Vec3d eye = mc.player.getEyePos();
        BlockPos origin = mc.player.getBlockPos(), best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (!mc.world.getBlockState(pos).isOf(Blocks.CRAFTING_TABLE)) continue;
                    double distance = eye.squaredDistanceTo(Vec3d.ofCenter(pos));
                    if (distance <= range * range && distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }
        if (best == null) return;

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(best), Direction.UP, best, false));
        openedTable = true;
    }
}
