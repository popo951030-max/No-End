package cn.blockforge.wushiwuzhong.network;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 模组自用的极简网络通道。
 *
 * <p>只有两条消息：</p>
 * <ul>
 *   <li>{@link ClearBlacklistPacket}：客户端 → 服务端，Shift + T 清空生物黑名单；</li>
 *   <li>{@link PurgeVisualPacket}：服务端 → 客户端，右键清除时通知客户端把目标
 *       的贴图渲染与碰撞箱渲染逐级剥离。</li>
 * </ul>
 */
public final class NetworkHandler {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(WushiwuzhongMod.MOD_ID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private NetworkHandler() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(ClearBlacklistPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClearBlacklistPacket::encode)
                .decoder(ClearBlacklistPacket::decode)
                .consumerMainThread(ClearBlacklistPacket::handle)
                .add();
        CHANNEL.messageBuilder(PurgeVisualPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PurgeVisualPacket::encode)
                .decoder(PurgeVisualPacket::decode)
                .consumerMainThread(PurgeVisualPacket::handle)
                .add();
    }

    /** 把这个维度里所有玩家都通知一遍。 */
    public static void sendToDimension(ServerLevel level, PurgeVisualPacket message) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), message);
    }
}
