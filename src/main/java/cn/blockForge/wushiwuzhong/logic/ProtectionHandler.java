package cn.blockforge.wushiwuzhong.logic;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 持有者不死 + 死亡事件取消 + 数据回溯。
 *
 * <p>背包里有剑时：</p>
 * <ul>
 *   <li>取消 {@link LivingAttackEvent}、{@link LivingHurtEvent}，任何来源都削不掉血；
 *       指令里靠 {@code hurt()} 造成伤害的（含 {@code /kill}、模组的伤害指令）同样被拦；</li>
 *   <li>取消 {@link LivingDeathEvent}，死亡判定根本不成立；</li>
 *   <li>每 tick 把血量顶回满、把 deathTime / hurtTime / 缺氧值一起复位，
 *       顺带把受伤红屏也一并压掉；</li>
 *   <li>如果别的模组绕过伤害系统直接把玩家实体 {@code setRemoved} 掉，
 *       服务器刻巡检时会把移除标记清掉并把玩家重新挂回世界，血量与状态一并复原。</li>
 * </ul>
 *
 * <p>唯一的例外是 {@link CommandGuard} 判定「玩家正被其它模组的事件推着走」
 * 时放行的 {@code /kill}：那时本处理器会让路，死亡正常发生。</p>
 *
 * <p>死亡界面的屏蔽在客户端侧（见 {@code client.ClientProtection}）。</p>
 */
@Mod.EventBusSubscriber(modid = WushiwuzhongMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProtectionHandler {

    private static final float GUARANTEED_HEALTH = 20.0F;

    /** {@code Entity#unsetRemoved()}，用来把外部塞进来的移除标记抹掉。 */
    private static final Method UNSET_REMOVED = resolveUnsetRemoved();

    private ProtectionHandler() {
    }

    private static Method resolveUnsetRemoved() {
        try {
            Method method = ObfuscationReflectionHelper.findMethod(Entity.class, "unsetRemoved");
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player
                && SwordHolder.holdsSword(player)
                && !CommandGuard.isDeathAllowed(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player
                && SwordHolder.holdsSword(player)
                && !CommandGuard.isDeathAllowed(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player
                && SwordHolder.holdsSword(player)
                && !CommandGuard.isDeathAllowed(player)) {
            event.setCanceled(true);
            player.setHealth(GUARANTEED_HEALTH);
            player.deathTime = 0;
            player.hurtTime = 0;
        }
    }

    /**
     * 无始无终的每一次攻击都清空敌对生物的受击冷却（无敌帧）。
     *
     * <p>{@link LivingAttackEvent} 是 {@code hurt()} 最前面发出的，此时把
     * {@code invulnerableTime} 归零，后面那道
     * 「{@code invulnerableTime > 10 就忽略本次伤害}」的判定就永远拦不住这一击；
     * 每次攻击都会重新归零，于是连续攻击也不再被无敌帧吃掉。</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSwordStrike(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker)) {
            return;
        }
        if (!SwordHolder.holdsSword(attacker)) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target instanceof Enemy) {
            target.invulnerableTime = 0;
        }
    }

    /** 攻击、爆炸、生物行为推来的击退一律取消。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof Player player && SwordHolder.holdsSword(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * 爆炸的冲击走的是 {@code setDeltaMovement}，绕过了
     * {@link LivingKnockBackEvent}，所以直接把持有者从爆炸影响名单里摘掉，
     * 伤害与击退就都落不到他身上。
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        event.getAffectedEntities().removeIf(entity ->
                entity instanceof Player player && SwordHolder.holdsSword(player));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (!SwordHolder.holdsSword(player) || CommandGuard.isDeathAllowed(player)) {
            return;
        }
        // 放行的死亡已经发生：别再把人从死亡状态里拽回来。
        if (player.isDeadOrDying()) {
            return;
        }
        if (player.getHealth() < GUARANTEED_HEALTH) {
            player.setHealth(GUARANTEED_HEALTH);
        }
        if (player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(player.getMaxAirSupply());
        }
        player.deathTime = 0;
        player.hurtTime = 0;
        player.hurtDuration = 0;
    }

    /**
     * 防「Remove 型攻击」。
     *
     * <p>有些模组不走伤害系统，而是直接给玩家实体打一个移除标记
     * （{@code setRemoved(RemovalReason)}），玩家就从世界里消失了。
     * 这里在服务器刻上巡检所有在线的持剑者：一旦发现被移除标记过，
     * 就把标记抹掉、把玩家重新挂回所在维度，再把血量与受伤状态复原。</p>
     *
     * <p>只处理「非正常死亡」的情况：真的走完了放行死亡流程的玩家不会被拽回来。
     * 底层调用全部包在 try/catch 里，任何一步不成立就放弃，绝不让服务器因为
     * 一次恢复失败而崩掉。</p>
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            if (!player.isRemoved() || player.isDeadOrDying() || CommandGuard.isDeathAllowed(player)) {
                continue;
            }
            if (!SwordHolder.holdsSword(player)) {
                continue;
            }
            restoreRemoved(player);
        }
    }

    private static void restoreRemoved(ServerPlayer player) {
        if (UNSET_REMOVED == null) {
            return;
        }
        try {
            UNSET_REMOVED.invoke(player);
            ServerLevel level = player.serverLevel();
            if (level.getEntity(player.getUUID()) == null) {
                level.addFreshEntity(player);
            }
            player.setHealth(Math.min(GUARANTEED_HEALTH, player.getMaxHealth()));
            player.setAirSupply(player.getMaxAirSupply());
            player.deathTime = 0;
            player.hurtTime = 0;
            player.hurtDuration = 0;
        } catch (Throwable ignored) {
            // 恢复失败就算了，至少不要让服务端因此崩溃。
        }
    }
}
