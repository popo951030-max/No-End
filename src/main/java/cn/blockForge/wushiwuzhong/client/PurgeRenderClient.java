package cn.blockforge.wushiwuzhong.client;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import cn.blockforge.wushiwuzhong.network.PurgeVisualPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 右键清除的客户端渲染剥离。
 *
 * <p>服务端在下杀手之前先把目标的实体 ID 推过来，这里分两级执行：</p>
 * <ol>
 *   <li>第一级：{@code RenderLivingEvent.Pre} 直接取消，生物模型与贴图不再绘制；</li>
 *   <li>第二级：再把客户端那份实体副本标记为隐身，连阴影与名字牌一起消失——
 *       原版的碰撞箱只在 F3 调试里画，实体整体不渲染就等于碰撞箱也不存在了。</li>
 * </ol>
 *
 * <p>条目带过期时间，实体 ID 被回收后不会误伤新出现的生物。</p>
 */
@Mod.EventBusSubscriber(modid = WushiwuzhongMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PurgeRenderClient {

    /** 剥离效果的存活时间（游戏刻）。 */
    private static final long LIFETIME_TICKS = 120L;

    /** 实体 ID → [过期游戏刻, 是否已剥到第二级]。 */
    private static final Map<Integer, long[]> STRIPPED = new ConcurrentHashMap<>();

    private PurgeRenderClient() {
    }

    /** 收到服务端通知时调用。 */
    public static void strip(int[] ids, int stage) {
        Level level = Minecraft.getInstance().level;
        long expire = (level == null ? 0L : level.getGameTime()) + LIFETIME_TICKS;
        for (int id : ids) {
            STRIPPED.merge(id, new long[]{expire, stage == PurgeVisualPacket.STAGE_COLLISION ? 1L : 0L},
                    (oldValue, newValue) -> new long[]{
                            Math.max(oldValue[0], newValue[0]),
                            Math.max(oldValue[1], newValue[1])});
        }
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        long[] entry = active(event.getEntity());
        if (entry == null) {
            return;
        }
        if (entry[1] >= 1L) {
            event.getEntity().setInvisible(true);
        }
        // 贴图与模型整体不画；第二级同样不再绘制，等于碰撞箱一层也没了。
        event.setCanceled(true);
    }

    /** 顺手清理过期条目。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || STRIPPED.isEmpty()) {
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            STRIPPED.clear();
            return;
        }
        long now = level.getGameTime();
        STRIPPED.entrySet().removeIf(entry -> entry.getValue()[0] < now);
    }

    private static long[] active(LivingEntity entity) {
        long[] entry = STRIPPED.get(entity.getId());
        if (entry == null) {
            return null;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null || entry[0] < level.getGameTime()) {
            STRIPPED.remove(entity.getId());
            return null;
        }
        return entry;
    }

    /** 备用入口：给将来可能的实体类型扩展留个位置。 */
    public static void stripEntity(Entity entity, int stage) {
        strip(new int[]{entity.getId()}, stage);
    }
}
