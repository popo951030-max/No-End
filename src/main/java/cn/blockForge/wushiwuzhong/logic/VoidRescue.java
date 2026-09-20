package cn.blockforge.wushiwuzhong.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 虚空救援。
 *
 * <p>背包里带着无始无终却掉进虚空时，按下面的优先级找落脚点：</p>
 * <ol>
 *   <li>玩家已设置的床 / 已激活的重生锚；</li>
 *   <li>所在维度的传送门——终界走返回传送门，下界找最近的地狱门；</li>
 *   <li>主世界出生点，出生点被恶意清掉就在别的区块重新生成一个，
 *       并写回世界出生点。</li>
 * </ol>
 * <p>落脚点脚下没有实体方块时，就地铺一层 5×5 黑曜石平台。</p>
 */
public final class VoidRescue {

    private static final int PLATFORM_RADIUS = 2;
    private static final int PORTAL_SEARCH_RADIUS = 48;
    private static final int PORTAL_SEARCH_STEP = 4;

    private VoidRescue() {
    }

    public static void watch(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (player.getY() < level.getMinBuildHeight() - 2.0D) {
            rescue(player);
        }
    }

    private static void rescue(ServerPlayer player) {
        MinecraftServer server = player.server;

        // 1. 玩家自己设置的重生点（床 / 已激活重生锚）
        if (rescueToRespawnPoint(player, server)) {
            return;
        }

        // 2. 所在维度的传送门
        ServerLevel current = player.serverLevel();
        if (current.dimension() == Level.END) {
            if (deliver(player, current, new BlockPos(0, 64, 0))) {
                return;
            }
        } else if (current.dimension() == Level.NETHER) {
            BlockPos portal = findPortal(current, player.blockPosition());
            if (portal != null && deliver(player, current, portal.above())) {
                return;
            }
        }

        // 3. 主世界出生点
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        if (!hasFloor(overworld, spawn)) {
            spawn = regenerateSpawn(overworld);
        }
        deliver(player, overworld, spawn);
    }

    private static boolean rescueToRespawnPoint(ServerPlayer player, MinecraftServer server) {
        BlockPos pos = player.getRespawnPosition();
        ResourceKey<Level> dimension = player.getRespawnDimension();
        if (pos == null || dimension == null) {
            return false;
        }
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return false;
        }
        level.getChunkAt(pos);
        if (!isRespawnBlock(level, pos)) {
            return false;
        }
        return deliver(player, level, pos.above());
    }

    /** 重生位置上还站得住吗：床还在，或者重生锚还充着能。 */
    private static boolean isRespawnBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BedBlock) {
            return true;
        }
        if (state.is(Blocks.RESPAWN_ANCHOR)) {
            return state.getValue(RespawnAnchorBlock.CHARGE) > 0;
        }
        return false;
    }

    /** 把玩家送到目标点；脚下没地方站就现铺一块黑曜石。 */
    private static boolean deliver(ServerPlayer player, ServerLevel level, BlockPos feet) {
        int y = feet.getY();
        if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 2) {
            y = Math.max(level.getMinBuildHeight() + 2, 64);
            feet = new BlockPos(feet.getX(), y, feet.getZ());
        }
        level.getChunkAt(feet);
        if (!hasFloor(level, feet)) {
            buildPlatform(level, feet);
        }
        player.teleportTo(level, feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        player.resetFallDistance();
        level.playSound(null, feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.displayClientMessage(Component.translatable("message.wushiwuzhong.void_rescue"), false);
        return true;
    }

    /** 脚下要有实体方块，脚部与头部两格要空，且不能泡在岩浆里。 */
    private static boolean hasFloor(Level level, BlockPos feet) {
        BlockPos below = feet.below();
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            return false;
        }
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
            return false;
        }
        if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
            return false;
        }
        return !level.getFluidState(feet).is(FluidTags.LAVA);
    }

    private static void buildPlatform(ServerLevel level, BlockPos feet) {
        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                level.setBlockAndUpdate(feet.offset(dx, -1, dz), Blocks.OBSIDIAN.defaultBlockState());
                for (int dy = 0; dy < 3; dy++) {
                    level.setBlockAndUpdate(feet.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** 下界里以玩家为中心、由近及远地找一个地狱门方块。 */
    private static BlockPos findPortal(ServerLevel level, BlockPos origin) {
        for (int radius = 0; radius <= PORTAL_SEARCH_RADIUS; radius += PORTAL_SEARCH_STEP) {
            for (int dx = -radius; dx <= radius; dx += PORTAL_SEARCH_STEP) {
                for (int dz = -radius; dz <= radius; dz += PORTAL_SEARCH_STEP) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y += PORTAL_SEARCH_STEP) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!level.isLoaded(pos)) {
                            continue;
                        }
                        if (level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 原出生点废了：在附近别的区块里重新挑一块地，并写回世界出生点。 */
    private static BlockPos regenerateSpawn(ServerLevel overworld) {
        RandomSource random = overworld.getRandom();
        BlockPos origin = overworld.getSharedSpawnPos();
        for (int attempt = 0; attempt < 64; attempt++) {
            int chunkX = (origin.getX() >> 4) + random.nextInt(33) - 16;
            int chunkZ = (origin.getZ() >> 4) + random.nextInt(33) - 16;
            BlockPos column = new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8);
            overworld.getChunkAt(column);
            BlockPos surface = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (hasFloor(overworld, surface)) {
                overworld.setDefaultSpawnPos(surface, 0.0F);
                return surface;
            }
        }
        BlockPos fallback = new BlockPos(origin.getX(),
                Math.max(overworld.getMinBuildHeight() + 2, 64), origin.getZ());
        overworld.getChunkAt(fallback);
        buildPlatform(overworld, fallback);
        overworld.setDefaultSpawnPos(fallback, 0.0F);
        return fallback;
    }
}
