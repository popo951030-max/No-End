package cn.blockforge.wushiwuzhong.logic;

import cn.blockforge.wushiwuzhong.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 「持有者」判定：只要无始无终在背包（含快捷栏、护甲槽、副手）里就算持有，
 * 不需要真的拿在手上。
 */
public final class SwordHolder {

    private SwordHolder() {
    }

    /** 这一格是不是无始无终。 */
    public static boolean isSword(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.WUSHIWUZHONG.get());
    }

    /**
     * 剑是否「在身」。
     *
     * <p>除了背包里的格子，还算上玩家自己背包界面里鼠标正拖着的那一格，
     * 以及合成格、护甲格。这样玩家在背包里随意拖拽时，剑不会被保护逻辑
     * 误判成「弄丢了」而抢回去。</p>
     */
    public static boolean holdsSword(Player player) {
        InventoryMenu menu = player.inventoryMenu;
        if (isSword(menu.getCarried())) {
            return true;
        }
        for (Slot slot : menu.slots) {
            if (isSword(slot.getItem())) {
                return true;
            }
        }
        return false;
    }

    /** 返回背包里找到的第一把无始无终，找不到返回 {@link ItemStack#EMPTY}。 */
    public static ItemStack findSword(Player player) {
        Inventory inventory = player.getInventory();
        int size = inventory.getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isSword(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 把耐久值强行抹回 0。
     *
     * <p>剑本身已经 {@code canBeDepleted() == false}，正常途径掉不了耐久；
     * 这一手专门对付指令、NBT 编辑这类绕过常规逻辑、直接往 {@code Damage}
     * 标签里写数的做法。</p>
     */
    public static void sealDamage(ItemStack stack) {
        if (!isSword(stack)) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Damage") && tag.getInt("Damage") != 0) {
            tag.putInt("Damage", 0);
        }
    }
}
