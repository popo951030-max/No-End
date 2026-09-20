package cn.blockforge.wushiwuzhong.network;

import cn.blockforge.wushiwuzhong.client.PurgeRenderClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：把一批实体的渲染逐级剥掉。
 *
 * <p>右键清除的第一步是「让目标在画面上先消失」，分两级推送：</p>
 * <ol>
 *   <li>{@link #STAGE_TEXTURE}：贴图/模型不再绘制；</li>
 *   <li>{@link #STAGE_COLLISION}：连碰撞箱那一层也一并剥掉，画面上彻底不存在。</li>
 * </ol>
 */
public final class PurgeVisualPacket {

    public static final int STAGE_TEXTURE = 1;
    public static final int STAGE_COLLISION = 2;

    private final int[] ids;
    private final int stage;

    public PurgeVisualPacket(int[] ids, int stage) {
        this.ids = ids;
        this.stage = stage;
    }

    public static void encode(PurgeVisualPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarIntArray(message.ids);
        buffer.writeVarInt(message.stage);
    }

    public static PurgeVisualPacket decode(FriendlyByteBuf buffer) {
        return new PurgeVisualPacket(buffer.readVarIntArray(), buffer.readVarInt());
    }

    public static void handle(PurgeVisualPacket message, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> PurgeRenderClient.strip(message.ids, message.stage));
        context.get().setPacketHandled(true);
    }
}
