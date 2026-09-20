package cn.blockforge.wushiwuzhong.logic;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PermissionsChangedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持剑即掌权。
 *
 * <p>只要背包、物品栏或手上有无始无终，玩家会被直接授予满级 OP：</p>
 * <ul>
 *   <li>每刻检查一次，掉了就立刻补上，并把 OP 等级写死为 4；</li>
 *   <li>{@link PermissionsChangedEvent} 里把「降级为 0」的操作整个按掉，
 *       所以后台控制台与其它管理员的 {@code /deop} 都拿不走这份权限；</li>
 *   <li>背包装满也没关系——OP 等级 4 让 {@code canUseGameMasterBlocks()} 恒为真，
 *       生存模式下也能正常放置与使用命令方块、结构方块这类游戏管理员方块。</li>
 * </ul>
 *
 * <p>权限是「借来」的：剑一旦离身（只可能发生在认主判定之前），
 * 由本模组授予的那一份会自动收回，玩家原本自带的 OP 不受影响。</p>
 */
@Mod.EventBusSubscriber(modid = WushiwuzhongMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HolderRights {

    /** 满级权限。 */
    public static final int FULL_LEVEL = 4;

    /** 由本模组授予（而不是玩家本来就有）OP 的名单，用于剑离身时收回。 */
    private static final Set<UUID> LENT = ConcurrentHashMap.newKeySet();

    private HolderRights() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        PlayerList players = server.getPlayerList();
        GameProfile profile = player.getGameProfile();

        if (SwordHolder.holdsSword(player)) {
            if (!players.isOp(profile)) {
                players.op(profile);
                LENT.add(player.getUUID());
            }
            forceFullLevel(players, player, profile);
            return;
        }
        if (LENT.remove(player.getUUID()) && players.isOp(profile)) {
            players.deop(profile);
        }
    }

    /**
     * 把 OP 条目里的等级硬顶到 4。
     *
     * <p>{@code PlayerList#op} 用的是服务端 {@code op-permission-level} 设置，
     * 如果服务器把它调低了，命令方块之流就会用不了；这里直接重建条目写死 4，
     * 补上权限包并同步给客户端。</p>
     */
    private static void forceFullLevel(PlayerList players, ServerPlayer player, GameProfile profile) {
        ServerOpList ops = players.getOps();
        ServerOpListEntry entry = ops.get(profile);
        if (entry != null && entry.getLevel() >= FULL_LEVEL) {
            return;
        }
        boolean bypass = entry != null && entry.getBypassesPlayerLimit();
        ops.remove(profile);
        ops.add(new ServerOpListEntry(profile, FULL_LEVEL, bypass));
        players.sendPlayerPermissionLevel(player);
    }

    /**
     * OP 被摘掉的那一刻拦下来。
     *
     * <p>{@code PlayerList#deop} 在最前面就会发出这个事件，取消掉它，
     * 条目原封不动，客户端那边连权限包都不会收到。控制台、其它管理员、
     * 模组的自动化流程，走 {@code deop} 这条路的全都被挡在这里。</p>
     */
    @SubscribeEvent
    public static void onPermissionChanged(PermissionsChangedEvent event) {
        if (event.getNewLevel() > 0) {
            return;
        }
        if (!SwordHolder.holdsSword(event.getEntity())) {
            return;
        }
        event.setCanceled(true);
        event.getEntity().displayClientMessage(
                Component.translatable("message.wushiwuzhong.op_sealed").withStyle(ChatFormatting.LIGHT_PURPLE),
                false);
    }
}
