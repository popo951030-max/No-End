package cn.blockforge.wushiwuzhong.item;

import cn.blockforge.wushiwuzhong.client.SwordHandRender;
import cn.blockforge.wushiwuzhong.logic.SwordHolder;
import cn.blockforge.wushiwuzhong.logic.VoidGradient;
import cn.blockforge.wushiwuzhong.logic.VoidPurge;
import cn.blockforge.wushiwuzhong.registry.ModTier;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.List;
import java.util.function.Consumer;

/**
 * 无始无终之剑。
 *
 * <ul>
 *   <li>耐久被彻底封死：{@code canBeDepleted} / {@code isDamageable} /
 *       {@code getDamage} / {@code setDamage} 全部改写，常规逻辑与指令都动不了它。</li>
 *   <li>背包里带着它，玩家身旁就会飘起紫色虚空微粒。</li>
 *   <li>长按右键 1 tick 即触发全图虚空清除。</li>
 *   <li>右键按住时，第一人称摆出 1.8 的举剑格挡架势
 *       （见 {@code client.SwordHandRender}）。</li>
 *   <li>{@code onDroppedByPlayer} 恒为 false：丢不出去。</li>
 * </ul>
 */
public class WushiwuzhongSwordItem extends SwordItem {

    /** 长按右键多少 tick 后触发虚空清除。1 tick 即触发。 */
    public static final int CHARGE_TICKS = 1;

    /**
     * 格挡架势的持续时长。
     *
     * <p>原版用「使用中」这个状态来决定手臂姿势与格挡观感，所以只要还在
     * 按着右键，就给一个足够长的时长，让 1.8 那样举剑格挡的架势一直保持。</p>
     */
    public static final int BLOCK_DURATION = 72000;

    public WushiwuzhongSwordItem() {
        super(ModTier.WUSHIWUZHONG, 6, -1.6F,
                new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    /**
     * 第一人称格挡姿势的客户端扩展。
     *
     * <p>{@code Item} 只在客户端且非数据生成时回调这里，所以这段代码不会在
     * 专用服务器上跑；扩展本身也只在客户端被取用。</p>
     */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SwordHandRender.handExtension());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // ------------------------------------------------------------------
    // 名字与攻击属性
    // ------------------------------------------------------------------

    /**
     * 名字本身也走灰白渐变。
     *
     * <p>原版悬浮框的第一行就是 {@code ItemStack#getHoverName()} 的结果，
     * 子组件的颜色优先级高于外层稀有度颜色，所以这里逐字染色就能盖住原版的紫名。</p>
     */
    @Override
    public Component getName(ItemStack stack) {
        return VoidGradient.ofTranslatable(this.getDescriptionId(stack));
    }

    /**
     * 不给剑挂任何原版属性修饰符。
     *
     * <p>原版的「+7 攻击伤害 / 1.6 攻击速度」两行是由属性表推出来的，名字写死在
     * {@code Attributes} 上，改不了文案；索性把属性表清空，改用
     * {@link #appendHoverText} 自己写「+nonentity伤害 / No End攻击速度」两行。
     * 真正的伤害与攻速由 {@code logic.AttackHandler}、{@code logic.AbilityHandler}
     * 在代码里给足，不依赖这张表。</p>
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return ImmutableMultimap.of();
    }

    // ------------------------------------------------------------------
    // 耐久封死
    // ------------------------------------------------------------------

    /** 剑永不耗损：根本不认为它「有耐久」。 */
    @Override
    public boolean canBeDepleted() {
        return false;
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    /** 无论 NBT 里写了什么，读出来永远是 0。 */
    @Override
    public int getDamage(ItemStack stack) {
        return 0;
    }

    /** 写入耐久直接丢弃，连指令都改不动。 */
    @Override
    public void setDamage(ItemStack stack, int damage) {
        // 无始无终不存在耐久。
    }

    @Override
    public boolean isDamaged(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return false;
    }

    /** 丢不出去。 */
    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        return false;
    }

    // ------------------------------------------------------------------
    // 右键行为
    // ------------------------------------------------------------------

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || !SwordHolder.holdsSword(player)) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        level.playSound(player, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.7F, 0.45F);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return BLOCK_DURATION;
    }

    /**
     * 1.8 的剑格挡：右手把剑架到身前。
     *
     * <p>{@link UseAnim#BLOCK} 决定了「使用中」的手臂姿势（第三人称由玩家模型摆出
     * 横臂格挡），第一人称的具体位移与旋转则交给 {@code client.SwordHandRender}，
     * 用 1.8 原版那套数值把剑抬到身前，观感与老版本的举剑格挡一致。</p>
     */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    /**
     * 蓄力 / 格挡过程。
     *
     * <p>第 {@link #CHARGE_TICKS} tick 触发一次虚空清除，之后只要还按着右键，
     * 就保持格挡架势；客户端同时把虚空微粒往剑身收拢。</p>
     */
    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingUseDuration) {
        int elapsed = BLOCK_DURATION - remainingUseDuration;
        if (!level.isClientSide) {
            if (elapsed == CHARGE_TICKS && living instanceof ServerPlayer player) {
                VoidPurge.unleash((ServerLevel) level, player);
            }
            return;
        }
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = 1.2D - Math.min(0.8D, elapsed / (double) BLOCK_DURATION * 0.8D);
        level.addParticle(ParticleTypes.SCULK_SOUL,
                living.getX() + Math.cos(angle) * radius,
                living.getY() + 0.9D + random.nextDouble() * 0.9D,
                living.getZ() + Math.sin(angle) * radius,
                -Math.cos(angle) * 0.08D, 0.05D, -Math.sin(angle) * 0.08D);
    }

    /** 蓄力已在 {@link #onUseTick} 里完成；这里只把剑原样还回去。 */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        return stack;
    }

    /** 只要剑还在背包里，持有者周身就有紫色虚空微粒，同时把耐久抹回 0。 */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        SwordHolder.sealDamage(stack);
        if (!level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        RandomSource random = level.getRandom();
        if (random.nextFloat() > 0.35F) {
            return;
        }
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = 0.65D;
        level.addParticle(ParticleTypes.PORTAL,
                player.getX() + Math.cos(angle) * radius,
                player.getY() + random.nextDouble() * 1.9D,
                player.getZ() + Math.sin(angle) * radius,
                (random.nextDouble() - 0.5D) * 0.04D, 0.02D, (random.nextDouble() - 0.5D) * 0.04D);
    }

    /**
     * 悬浮说明。
     *
     * <p>第一行名字由 {@link #getName} 负责，这里补上介绍词、攻击面板与使用说明，
     * 每一行都逐字染成「灰 → 白 → 灰」的渐变色。悬浮框每帧重建一次，于是颜色
     * 自己就一直在循环流动，不需要任何定时器。</p>
     */
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        int[] cursor = {0};
        for (int line = 1; line <= 5; line++) {
            tooltip.add(VoidGradient.ofTranslatable("tooltip.wushiwuzhong.intro" + line, cursor[0]));
            cursor[0] += 12;
        }
        tooltip.add(VoidGradient.ofTranslatable("tooltip.wushiwuzhong.attack_damage", cursor[0]));
        tooltip.add(VoidGradient.ofTranslatable("tooltip.wushiwuzhong.attack_speed", cursor[0] + 8));
        for (String key : new String[]{"break", "purge", "strike", "immortal", "bind", "bless", "recipe", "slot"}) {
            tooltip.add(VoidGradient.ofTranslatable("tooltip.wushiwuzhong." + key, cursor[0]));
        }
    }
}
