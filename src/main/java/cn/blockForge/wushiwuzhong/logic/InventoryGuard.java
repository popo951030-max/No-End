package cn.blockforge.wushiwuzhong.logic;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 持剑者的背包与物品栏对外上锁。
 *
 * <p>原版没有「打开别人的背包」这条路径，但各种模组的 /invsee、右键开包、
 * 便捷背包界面都会把别人的 {@code Inventory} 直接塞进一个菜单里。这里从两个方向堵：</p>
 * <ol>
 *   <li>右键交互：别人对着持剑者按右键（部分模组借这条路开包）一律取消；</li>
 *   <li>每个服务器刻巡检一遍：谁打开的菜单里出现了持剑者的背包或末影箱，
 *       当场把菜单关掉并提示。不管是哪个模组、用什么方式开起来的，都拦得住。</li>
 * </ol>
 * <p>持剑者自己开自己的东西不受影响。</p>
 */
@Mod.EventBusSubscriber(modid = WushiwuzhongMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InventoryGuard {

    private InventoryGuard() {
    }

    /**
     * 菜单里有没有直接摆着这位玩家的背包 / 末影箱。
     *
     * <p>只看格子背后真正的容器引用，所以常规箱子、熔炉、村民交易都不会被误伤。</p>
     */
    private static boolean exposes(AbstractContainerMenu menu, Player owner) {
        Inventory inventory = owner.getInventory();
        PlayerEnderChestContainer enderChest = owner.getEnderChestInventory();
        for (Slot slot : menu.slots) {
            if (slot.container == inventory || slot.container == enderChest) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.size() < 2) {
            return;
        }
        List<ServerPlayer> holders = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (SwordHolder.holdsSword(player)) {
                holders.add(player);
            }
        }
        if (holders.isEmpty()) {
            return;
        }
        for (ServerPlayer opener : players) {
            AbstractContainerMenu menu = opener.containerMenu;
            if (menu == null || menu == opener.inventoryMenu) {
                continue;
            }
            for (ServerPlayer holder : holders) {
                if (holder == opener || !exposes(menu, holder)) {
                    continue;
                }
                opener.closeContainer();
                warn(opener, holder);
                break;
            }
        }
    }

    /** 别人对着持剑者按右键——不少模组正是借这一下打开对方的背包。 */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        guard(event, event.getTarget());
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        guard(event, event.getTarget());
    }

    private static void guard(PlayerInteractEvent event, Entity target) {
        if (!(target instanceof ServerPlayer holder) || !SwordHolder.holdsSword(holder)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer interactor) || interactor == holder) {
            return;
        }
        event.setCanceled(true);
        warn(interactor, holder);
    }

    private static void warn(ServerPlayer opener, Player holder) {
        opener.displayClientMessage(Component.translatable(
                "message.wushiwuzhong.inventory_locked", holder.getDisplayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }
}
