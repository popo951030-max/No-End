package cn.blockforge.wushiwuzhong.network;

import cn.blockforge.wushiwuzhong.logic.EntityBlacklist;
import cn.blockforge.wushiwuzhong.logic.SwordHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：清空无始无终的生物黑名单。
 *
 * <p>服务端会再核一次「手里到底有没有这把剑」，免得被伪造的包清掉名单。</p>
 */
public final class ClearBlacklistPacket {

    public ClearBlacklistPacket() {
    }

    public static void encode(ClearBlacklistPacket message, FriendlyByteBuf buffer) {
        // 没有载荷。
    }

    public static ClearBlacklistPacket decode(FriendlyByteBuf buffer) {
        return new ClearBlacklistPacket();
    }

    public static void handle(ClearBlacklistPacket message, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player == null || !SwordHolder.holdsSword(player)) {
                return;
            }
            int removed = EntityBlacklist.data(player.serverLevel()).size();
            EntityBlacklist.clear(player.serverLevel());
            player.displayClientMessage(Component.translatable(
                            "message.wushiwuzhong.blacklist_cleared", removed)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        });
        context.get().setPacketHandled(true);
    }
}
