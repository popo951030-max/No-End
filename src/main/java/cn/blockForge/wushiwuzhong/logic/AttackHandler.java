package cn.blockforge.wushiwuzhong.logic;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无始无终的左键攻击。
 *
 * <p>手持（或背包里有）这把剑时，左键点到任何活物——敌对生物、被动生物、
 * 别的玩家都算——都不会再走原版那套结算，而是进入这条固定流程：</p>
 * <ol>
 *   <li>侦测目标的实体 ID，把它挂进「处决名单」；</li>
 *   <li>每一刻把它的受击无敌帧清零；</li>
 *   <li>灌入 {@link Float#MAX_VALUE} 虚空伤害；</li>
 *   <li>没死就走一次 {@code /kill}；</li>
 *   <li>还在就每一刻把血量按到 0（连最大生命基值一起）；</li>
 *   <li>确认死亡后把 {@code deathTime} 设成 {@link Integer#MAX_VALUE}；</li>
 *   <li>敌对生物的种类同时进 {@link EntityBlacklist}，此后不许再生成。</li>
 * </ol>
 *
 * <p>没有攻击冷却：{@code AbilityHandler} 已经把攻击速度修饰符拉到极高，
 * 攻击条永远满格。</p>
 */
@Mod.EventBusSubscriber(modid = WushiwuzhongMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AttackHandler {

    /** 目标最多被追着处理多少刻，防止残留条目永久占着内存。 */
    private static final int MAX_CHASE_TICKS = 200;

    /** 处决名单：实体 UUID → 剩余追查刻数。 */
    private static final Map<UUID, Integer> CONDEMNED = new ConcurrentHashMap<>();

    private AttackHandler() {
    }

    /** 左键攻击：先接过来，改写成一整套虚空处决。 */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player attacker = event.getEntity();
        if (!SwordHolder.holdsSword(attacker)) {
            return;
        }
        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity living) || living == attacker) {
            return;
        }
        // 原版结算整个取消，改由下面这条链负责。
        event.setCanceled(true);
        if (!(living.level() instanceof ServerLevel level)) {
            return;
        }
        CONDEMNED.put(living.getUUID(), MAX_CHASE_TICKS);
        execute(level, living);
    }

    /** 每一刻把处决名单里的目标再按一遍，无敌帧根本立不住。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CONDEMNED.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Integer>> iterator = CONDEMNED.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            LivingEntity target = locate(server, entry.getKey());
            int left = entry.getValue() - 1;
            if (target == null || left <= 0) {
                iterator.remove();
                continue;
            }
            if (target.isRemoved()) {
                target.deathTime = Integer.MAX_VALUE;
                iterator.remove();
                continue;
            }
            entry.setValue(left);
            if (target.level() instanceof ServerLevel level) {
                execute(level, target);
            }
            if (target.isRemoved() || target.isDeadOrDying()) {
                target.deathTime = Integer.MAX_VALUE;
                iterator.remove();
            }
        }
    }

    /** 一整套处决链，右键清除那边也在用同一份逻辑。 */
    private static void execute(ServerLevel level, LivingEntity target) {
        DamageSource source = level.damageSources().genericKill();
        target.addTag(VoidPurge.STRIKE_TAG);
        try {
            VoidPurge.annihilate(level, target, source, VoidPurge.STRIKE_TAG, true);
            if (VoidPurge.isHostile(target)) {
                EntityBlacklist.ban(level, target);
            }
        } finally {
            target.removeTag(VoidPurge.STRIKE_TAG);
        }
    }

    /** 按 UUID 在全部维度里找回目标。 */
    private static LivingEntity locate(MinecraftServer server, UUID id) {
        List<ServerLevel> levels = new ArrayList<>();
        server.getAllLevels().forEach(levels::add);
        for (ServerLevel level : levels) {
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }
}
