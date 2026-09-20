package cn.blockforge.wushiwuzhong.logic;

import cn.blockforge.wushiwuzhong.network.NetworkHandler;
import cn.blockforge.wushiwuzhong.network.PurgeVisualPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 「虚空清除」：右键长按 1 tick 触发，把已加载区块里的敌对生物一抹干净。
 *
 * <p>对每个目标执行的兜底链，顺序固定：</p>
 * <ol>
 *   <li>摘掉不死图腾；</li>
 *   <li>剥贴图渲染（客户端通知，第一级）；</li>
 *   <li>剥碰撞箱渲染（客户端通知，第二级）；</li>
 *   <li>清空无敌帧后灌入 {@link Float#MAX_VALUE} 虚空伤害；</li>
 *   <li>仍然活着就走一次 {@code /kill}；</li>
 *   <li>仍然活着就把血量按到 0（连最大生命基值一起按）；</li>
 *   <li>强制移出世界；</li>
 *   <li>再从实体列表里摘掉；</li>
 *   <li>最后才拉起死亡计时 {@code deathTime}。</li>
 * </ol>
 *
 * <p>清场之前先点一遍所有敌对生物的实体 ID：既用于上面的渲染剥离通知，
 * 也用于把被打死的种类记进 {@link EntityBlacklist}，事后还把场上散落的
 * 掉落物与经验球一并吸进施法者的背包。</p>
 */
public final class VoidPurge {

    /** 临时打在目标身上的标签，供 /kill 的实体选择器精确定位，也给不死图腾拦截认人。 */
    public static final String PURGE_TAG = "wswz_void_target";

    /** 攻击兜底链用的标签（与右键清除分开，便于各自精确点名）。 */
    public static final String STRIKE_TAG = "wswz_strike_target";

    private VoidPurge() {
    }

    /**
     * 右键清除。
     *
     * @return 本次被清除的敌对生物数量
     */
    public static int unleash(ServerLevel level, ServerPlayer caster) {
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity == caster || entity.isRemoved()) {
                continue;
            }
            if (!(entity instanceof LivingEntity living) || !isHostile(living)) {
                continue;
            }
            if (!living.isAlive()) {
                continue;
            }
            targets.add(living);
        }

        if (targets.isEmpty()) {
            playFeedback(level, caster, 0);
            return 0;
        }

        // 侦测实体 ID —— 渲染剥离与黑名单都靠它。
        int[] ids = new int[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            ids[i] = targets.get(i).getId();
        }
        stripRendering(level, ids);

        DamageSource voidSource = level.damageSources().genericKill();

        // 先记下场上已有的经验球，事后再收，就只收这一轮新掉出来的。
        List<ExperienceOrb> preexistingOrbs = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ExperienceOrb orb) {
                preexistingOrbs.add(orb);
            }
        }

        List<ItemStack> loot = new ArrayList<>();
        int purged = 0;
        for (LivingEntity target : targets) {
            // 把掉落物截流下来，不让它掉在地上，事后再直接进背包。
            List<ItemEntity> captured = new ArrayList<>();
            target.captureDrops(captured);
            target.addTag(PURGE_TAG);
            try {
                if (annihilate(level, target, voidSource, PURGE_TAG, false)) {
                    purged++;
                }
                if (isHostile(target)) {
                    EntityBlacklist.ban(level, target);
                }
            } finally {
                target.removeTag(PURGE_TAG);
                target.captureDrops(null);
            }
            for (ItemEntity item : captured) {
                if (!item.getItem().isEmpty()) {
                    loot.add(item.getItem().copy());
                }
            }
        }

        // 掉落物与这一轮新生的经验直接吸进背包。
        vacuum(level, caster, loot, preexistingOrbs);
        playFeedback(level, caster, purged);
        return purged;
    }

    /**
     * 完整的击杀兜底链，右键清除与左键攻击共用。
     *
     * @param tag            临时打在目标身上的标签，供 {@code /kill} 点名
     * @param includePlayers 是否连玩家一起处理（右键清除只打敌对生物，左键攻击允许点玩家）
     * @return 是否真的处理了
     */
    public static boolean annihilate(ServerLevel level, LivingEntity target, DamageSource source,
                                     String tag, boolean includePlayers) {
        if (!includePlayers && target instanceof Player) {
            return false;
        }

        // 1. 先摘掉两只手里的不死图腾
        stripTotems(target);

        // 2. 清掉受击冷却，灌入 Float.MAX_VALUE 虚空伤害
        target.invulnerableTime = 0;
        target.hurt(source, Float.MAX_VALUE);

        // 3. 伤害没吃住，走一次 /kill
        if (target.isAlive() && !target.isRemoved()) {
            killByCommand(level, target, tag, includePlayers);
        }

        // 4. 指令也没拿下的（免疫类），把血量按到 0
        if (target.isAlive() && !target.isRemoved()) {
            zeroHealth(target);
        }

        // 5. 确认死亡状态：还在世上就强制移除
        if (!target.isRemoved()) {
            target.remove(Entity.RemovalReason.KILLED);
        }

        // 6. 再从实体列表里摘掉
        removeFromEntityList(level, target);

        // 7. 最后拉起死亡计时
        target.deathTime = Integer.MAX_VALUE;
        return true;
    }

    /** 这个目标算不算「敌对生物」：原版所有怪物 + 三大 Boss。 */
    public static boolean isHostile(LivingEntity entity) {
        return entity instanceof Enemy;
    }

    // ------------------------------------------------------------------
    // 兜底链的各个零件
    // ------------------------------------------------------------------

    /** 贴图与碰撞箱分两级推给客户端剥掉。 */
    private static void stripRendering(ServerLevel level, int[] ids) {
        NetworkHandler.sendToDimension(level,
                new PurgeVisualPacket(ids, PurgeVisualPacket.STAGE_TEXTURE));
        NetworkHandler.sendToDimension(level,
                new PurgeVisualPacket(ids, PurgeVisualPacket.STAGE_COLLISION));
    }

    private static void killByCommand(ServerLevel level, LivingEntity target, String tag, boolean includePlayers) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        CommandSourceStack source = server.createCommandSourceStack()
                .withLevel(level)
                .withSuppressedOutput()
                .withPermission(4);
        String selector = "kill @e[tag=" + tag + (includePlayers ? "]" : ",type=!player]");
        server.getCommands().performPrefixedCommand(source, selector);
    }

    /**
     * 把血量按到 0。
     *
     * <p>先动最大生命的「基值」，再写实际血量：即便某个模组把 {@code getHealth}
     * 包了一层，原版属性表算出来的上限也是 0，血量只能停在 0。玩家目标不动属性，
     * 免得把对方的角色面板改坏。</p>
     */
    private static void zeroHealth(LivingEntity target) {
        AttributeInstance maxHealth = target.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && !(target instanceof Player)) {
            maxHealth.setBaseValue(0.0D);
        }
        target.setHealth(0.0F);
    }

    /** 从世界的实体列表里摘掉：{@code setRemoved} 只打标记，这一步才真正从实体管理器移除。 */
    public static void removeFromEntityList(ServerLevel level, Entity entity) {
        try {
            level.getChunkSource().removeEntity(entity);
        } catch (RuntimeException ignored) {
            // 已经被别的路径摘走过一次，忽略即可。
        }
    }

    /** 把不死图腾从目标手里摘掉，让原版的图腾判定根本找不到它。 */
    private static void stripTotems(LivingEntity target) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = target.getItemInHand(hand);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                target.setItemInHand(hand, ItemStack.EMPTY);
            }
        }
    }

    // ------------------------------------------------------------------
    // 掉落物与经验
    // ------------------------------------------------------------------

    /**
     * 把这场清除产生的掉落物与经验直接送进施法者背包。
     *
     * <p>掉落物在目标死亡前就被 {@code captureDrops} 截流下来了，不会掉在地上，
     * 这里只是把它们挨个塞进背包；经验球则是按「清除前就在场上的」名单做差集，
     * 只收这一轮新爆出来的，不会顺手牵走别人掉的球。</p>
     *
     * @param loot            截流下来的掉落物
     * @param preexistingOrbs 动手之前就已经在场上飘着的经验球
     */
    private static void vacuum(ServerLevel level, ServerPlayer caster,
                               List<ItemStack> loot, List<ExperienceOrb> preexistingOrbs) {
        int experience = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ExperienceOrb orb && !containsIdentity(preexistingOrbs, orb)) {
                experience += orb.getValue();
                orb.discard();
            }
        }
        if (experience > 0) {
            caster.giveExperiencePoints(experience);
        }
        for (ItemStack stack : loot) {
            give(caster, stack);
        }
    }

    /** 按对象本身（而不是 equals）找，避免不同经验球之间互相误判。 */
    private static boolean containsIdentity(List<ExperienceOrb> orbs, ExperienceOrb candidate) {
        for (ExperienceOrb orb : orbs) {
            if (orb == candidate) {
                return true;
            }
        }
        return false;
    }

    /** 进背包，塞不下就丢在脚边（这时候已经不经过 Q 键那条拦截链）。 */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ------------------------------------------------------------------
    // 反馈
    // ------------------------------------------------------------------

    /** 低沉斩击 + 静默消解，外加一圈向外扩散的虚空微粒。 */
    private static void playFeedback(ServerLevel level, ServerPlayer caster, int purged) {
        double x = caster.getX();
        double y = caster.getY();
        double z = caster.getZ();

        level.playSound(null, x, y, z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, 0.55F);
        level.playSound(null, x, y, z, SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.9F, 0.5F);
        level.playSound(null, x, y, z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.4F, 1.15F);

        for (int i = 0; i < 72; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = 1.5D + level.getRandom().nextDouble() * 7.0D;
            level.sendParticles(ParticleTypes.PORTAL,
                    x + Math.cos(angle) * radius,
                    y + level.getRandom().nextDouble() * 2.5D - 0.5D,
                    z + Math.sin(angle) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        if (purged > 0) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 1.0D, z, 24, 0.6D, 1.0D, 0.6D, 0.02D);
        }
    }
}
