package cn.blockforge.wushiwuzhong.registry;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeTier;

/**
 * 无始无终的材质等级：比下界合金更硬、更快、更好附魔。
 *
 * <p>决定剑的基础耐久、挖掘速度、攻击伤害加成与附魔能力。</p>
 */
public final class ModTier {

    public static final Tier WUSHIWUZHONG = new ForgeTier(
            5,                                  // 挖掘等级
            4096,                               // 耐久
            12.0F,                              // 挖掘速度
            6.0F,                               // 攻击伤害加成
            30,                                 // 附魔能力
            BlockTags.NEEDS_DIAMOND_TOOL,       // 可挖需要钻石工具的方块
            () -> Ingredient.of(Items.NETHERITE_INGOT));

    private ModTier() {
    }
}
