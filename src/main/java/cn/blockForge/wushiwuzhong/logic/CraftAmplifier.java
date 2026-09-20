package cn.blockforge.wushiwuzhong.logic;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 拿无始无终去合成任何东西，产物直接翻到 64 个。
 *
 * <p>思路是「顺着原版走」而不是事后补：每个玩家刻扫一遍当前打开的菜单，
 * 只要合成格里有这把剑、结果格里是别的东西，就把结果格的数量顶到 64。
 * 之后玩家怎么拿——点一下、Shift 快速移动、按数字键换手——都是原版路径，
 * 拿到手的就是 64 个，也不会跟原版的合成统计打架。</p>
 *
 * <p>材料照常消耗；剑本身不进产物清单，会被 {@code SwordKeeper} 的看门逻辑
 * 在下一刻从合成格里捞回来。非堆叠产物（工具、盔甲之类）仍按物品自身的
 * 堆叠上限给，免得出现「一格 64 把剑」这种会把背包玩坏的东西。</p>
 */
@Mod.EventBusSubscriber(modid = WushiwuzhongMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CraftAmplifier {

    /** 合成产物的目标数量。 */
    public static final int OUTPUT = 64;

    private CraftAmplifier() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player instanceof ServerPlayer player) {
            amplify(player);
        }
    }

    /** 兜底：万一结果格没来得及被顶上去，就在取走的那一刻把数量改掉。 */
    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!gridHasSword(event.getInventory())) {
            return;
        }
        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty() || SwordHolder.isSword(crafted)) {
            return;
        }
        int wanted = wantedCount(crafted);
        if (crafted.getCount() < wanted) {
            crafted.setCount(wanted);
        }
    }

    private static void amplify(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            return;
        }
        Inventory own = player.getInventory();
        ResultSlot result = null;
        boolean swordInGrid = false;
        for (Slot slot : menu.slots) {
            // 玩家自己的背包格不算合成材料。
            if (slot.container == own) {
                continue;
            }
            if (slot instanceof ResultSlot) {
                result = (ResultSlot) slot;
            } else if (SwordHolder.isSword(slot.getItem())) {
                swordInGrid = true;
            }
        }
        if (!swordInGrid || result == null) {
            return;
        }
        ItemStack out = result.getItem();
        if (out.isEmpty() || SwordHolder.isSword(out)) {
            return;
        }
        int wanted = wantedCount(out);
        if (out.getCount() < wanted) {
            out.setCount(wanted);
        }
    }

    private static boolean gridHasSword(net.minecraft.world.Container grid) {
        for (int i = 0; i < grid.getContainerSize(); i++) {
            if (SwordHolder.isSword(grid.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    private static int wantedCount(ItemStack stack) {
        return Math.min(OUTPUT, Math.max(1, stack.getMaxStackSize()));
    }
}
