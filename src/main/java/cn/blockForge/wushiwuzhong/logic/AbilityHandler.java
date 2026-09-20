package cn.blockforge.wushiwuzhong.logic;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 持有者的常驻增益。
 *
 * <ul>
 *   <li>像创造模式一样飞行（把 {@code mayfly} 打开并同步给客户端）；</li>
 *   <li>移动速度 +50%，做成属性修饰符而不是药水效果；</li>
 *   <li>免疫一切负面药水效果，包括指令直接给予的，
 *       只留「不祥之兆」这一种好让袭击流程还能跑；</li>
 *   <li>饱食度锁在满格、消耗值清零，怎么跑跳挖矿都不会掉；</li>
 *   <li>常驻夜视，直接作用在玩家身上：效果实例设为不可见，
 *       既没有药水粒子，也不会在 HUD 上挂药水图标；时长给到无穷大，
 *       不是那种几百刻续一次的循环；</li>
 *   <li>攻击速度拉到极高，攻击冷却条永远满格，剑下没有冷却。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = WushiwuzhongMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AbilityHandler {

    private static final UUID SPEED_ID = UUID.fromString("6b0f3b64-1f52-4a8e-9f7a-3d1c2b4e5f60");
    private static final AttributeModifier SPEED_BOOST =
            new AttributeModifier(SPEED_ID, "wushiwuzhong_speed", 0.5D, AttributeModifier.Operation.MULTIPLY_TOTAL);

    /** 攻击速度修饰符：把冷却时长压到几乎为零。 */
    private static final UUID HASTE_ID = UUID.fromString("2f9d4c17-7b3a-4d55-9c81-5e0a6d2f4b73");
    private static final AttributeModifier NO_COOLDOWN =
            new AttributeModifier(HASTE_ID, "wushiwuzhong_no_cooldown", 400.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);

    /** 饱食度与饱和度的满值。 */
    private static final int FULL_FOOD = 20;
    private static final float FULL_SATURATION = 5.0F;

    /**
     * 夜视的持续时间：直接给到无穷大。
     *
     * <p>用 {@link Integer#MAX_VALUE} 减去一点余量，避免效果结算时自增溢出。
     * 效果只在「身上没有」时补一次，不做事无巨细的循环续时。</p>
     */
    private static final int INFINITE_DURATION = Integer.MAX_VALUE - 16;

    private AbilityHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!SwordHolder.holdsSword(player)) {
            return;
        }
        grantFlight(player);
        grantSpeed(player);
        grantNoCooldown(player);
        purgeHarmfulEffects(player);
        keepFed(player);
        grantNightVision(player);
        VoidRescue.watch(player);
    }

    /**
     * 饱食度不再下降。
     *
     * <p>{@code FoodData.tick} 只在「消耗值 {@code exhaustionLevel} > 4」时才扣饱食度，
     * 这里每刻把消耗值清零、把饱食度与饱和度顶回满格，饥饿条就永远停在满状态。
     * 数值改动会由 {@code ServerPlayer} 自己同步给客户端，不用手动发包。</p>
     */
    private static void keepFed(ServerPlayer player) {
        FoodData food = player.getFoodData();
        if (food.getFoodLevel() != FULL_FOOD) {
            food.setFoodLevel(FULL_FOOD);
        }
        if (food.getSaturationLevel() != FULL_SATURATION) {
            food.setSaturation(FULL_SATURATION);
        }
        if (food.getExhaustionLevel() != 0.0F) {
            food.setExhaustion(0.0F);
        }
    }

    /**
     * 无限时长的无粒子夜视。
     *
     * <p>{@code visible=false} 让原版的药水粒子根本不生成，{@code showIcon=false}
     * 则连 HUD 上的药水图标都不挂。时长直接写成 {@link #INFINITE_DURATION}，
     * 一次给足，不再每几百刻续一次。玩家自己喝出来的那瓶可见夜视会先被换成
     * 这一份隐藏版。</p>
     */
    private static void grantNightVision(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (current != null && current.isVisible()) {
            player.removeEffect(MobEffects.NIGHT_VISION);
            current = null;
        }
        if (current == null || current.getDuration() < INFINITE_DURATION / 2) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.NIGHT_VISION, INFINITE_DURATION, 0, false, false, false));
        }
    }

    /** 指令给药、药水、光灵箭……全都从源头拦下。 */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player) || !SwordHolder.holdsSword(player)) {
            return;
        }
        MobEffect effect = event.getEffectInstance().getEffect();
        if (isAllowed(effect)) {
            return;
        }
        event.setResult(Event.Result.DENY);
    }

    private static boolean isAllowed(MobEffect effect) {
        // 不祥之兆放行，否则袭击事件没法正常触发。
        return effect == MobEffects.BAD_OMEN || effect.isBeneficial();
    }

    private static void grantFlight(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        boolean changed = false;
        if (!abilities.mayfly) {
            abilities.mayfly = true;
            changed = true;
        }
        if (abilities.getFlyingSpeed() < 0.05F) {
            abilities.setFlyingSpeed(0.05F);
            changed = true;
        }
        if (changed) {
            player.onUpdateAbilities();
        }
    }

    private static void grantSpeed(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || speed.getModifier(SPEED_ID) != null) {
            return;
        }
        speed.addTransientModifier(SPEED_BOOST);
        // 属性变化不会自动同步，手动推一份给客户端，免得走起来一卡一卡。
        if (player.connection != null) {
            player.connection.send(new ClientboundUpdateAttributesPacket(
                    player.getId(), player.getAttributes().getSyncableAttributes()));
        }
    }

    /**
     * 攻击冷却归零。
     *
     * <p>原版的攻击冷却时长是 {@code (1 / 攻击速度) * 20} 刻，攻击速度越高冷却越短。
     * 这里给玩家叠一份乘算的攻击速度修饰符，冷却被压到 0.05 刻以内，
     * 攻击条永远满格，连着点也不会被冷却吃掉。</p>
     */
    private static void grantNoCooldown(ServerPlayer player) {
        AttributeInstance haste = player.getAttribute(Attributes.ATTACK_SPEED);
        if (haste == null || haste.getModifier(HASTE_ID) != null) {
            return;
        }
        haste.addTransientModifier(NO_COOLDOWN);
        if (player.connection != null) {
            player.connection.send(new ClientboundUpdateAttributesPacket(
                    player.getId(), player.getAttributes().getSyncableAttributes()));
        }
    }

    private static void purgeHarmfulEffects(ServerPlayer player) {
        List<MobEffectInstance> active = new ArrayList<>(player.getActiveEffects());
        for (MobEffectInstance instance : active) {
            MobEffect effect = instance.getEffect();
            if (!isAllowed(effect)) {
                player.removeEffect(effect);
            }
        }
    }
}
