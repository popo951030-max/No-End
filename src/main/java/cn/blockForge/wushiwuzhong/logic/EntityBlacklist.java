package cn.blockforge.wushiwuzhong.logic;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 无始无终的生物黑名单。
 *
 * <p>被这把剑诛除的敌对生物，其「种类」会被记进世界存档。名单里的种类：</p>
 * <ul>
 *   <li>再也进不了世界——{@link EntityJoinLevelEvent} 直接拦下，刷怪笼、刷怪蛋、
 *       自然生成、指令召唤、区块读档全都一样；</li>
 *   <li>已经存在的，每个服务器刻会被巡检一遍并当场抹除，防止任何「重生」路径。</li>
 * </ul>
 *
 * <p>名单是世界的持久数据，放在主世界的存档里，跨维度通用。
 * 手持无始无终按 Shift + T 可以整份清空。</p>
 */
@Mod.EventBusSubscriber(modid = WushiwuzhongMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EntityBlacklist {

    private static final String DATA_NAME = "wushiwuzhong_blacklist";

    private EntityBlacklist() {
    }

    // ------------------------------------------------------------------
    // 查询与写入
    // ------------------------------------------------------------------

    public static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, DATA_NAME);
    }

    public static Data data(ServerLevel level) {
        return data(level.getServer());
    }

    /** 把一种生物整个拉黑。 */
    public static void ban(ServerLevel level, Entity entity) {
        ban(level.getServer(), entity.getType());
    }

    public static void ban(MinecraftServer server, EntityType<?> type) {
        data(server).add(idOf(type));
    }

    public static boolean isBanned(MinecraftServer server, EntityType<?> type) {
        return server != null && data(server).contains(idOf(type));
    }

    public static boolean isBanned(Entity entity) {
        return entity.level().getServer() != null && isBanned(entity.level().getServer(), entity.getType());
    }

    /** Shift + T：整份清空。 */
    public static void clear(ServerLevel level) {
        data(level).clear();
    }

    private static String idOf(EntityType<?> type) {
        ResourceLocation key = EntityType.getKey(type);
        return key == null ? "" : key.toString();
    }

    // ------------------------------------------------------------------
    // 拦截与巡检
    // ------------------------------------------------------------------

    /** 名单里的种类一律不许进入世界。 */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        MinecraftServer server = event.getLevel().getServer();
        if (server == null) {
            return;
        }
        if (data(server).contains(idOf(event.getEntity().getType()))) {
            event.setCanceled(true);
        }
    }

    /** 每个服务器刻巡检一遍已加载的生物，名单里的一律抹除。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        Data data = data(server);
        if (data.isEmpty()) {
            return;
        }
        Set<String> banned = data.snapshot();
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> doomed = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity.isRemoved() || entity instanceof net.minecraft.world.entity.player.Player) {
                    continue;
                }
                if (banned.contains(idOf(entity.getType()))) {
                    doomed.add(entity);
                }
            }
            for (Entity entity : doomed) {
                entity.setRemoved(Entity.RemovalReason.KILLED);
                level.getChunkSource().removeEntity(entity);
            }
        }
    }

    /** 世界存档里的黑名单本身。 */
    public static final class Data extends SavedData {

        private final Set<String> types = new HashSet<>();

        public static Data load(CompoundTag tag) {
            Data data = new Data();
            ListTag list = tag.getList("types", 8);
            for (int i = 0; i < list.size(); i++) {
                String value = list.getString(i);
                if (!value.isEmpty()) {
                    data.types.add(value);
                }
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (String type : types) {
                list.add(StringTag.valueOf(type));
            }
            tag.put("types", list);
            return tag;
        }

        public boolean isEmpty() {
            return types.isEmpty();
        }

        public boolean contains(String id) {
            return !id.isEmpty() && types.contains(id);
        }

        public void add(String id) {
            if (!id.isEmpty() && types.add(id)) {
                setDirty();
            }
        }

        public void clear() {
            if (!types.isEmpty()) {
                types.clear();
                setDirty();
            }
        }

        public int size() {
            return types.size();
        }

        /** 巡检时用的快照，避免遍历过程中被并发改动。 */
        public Set<String> snapshot() {
            return new HashSet<>(types);
        }
    }
}
