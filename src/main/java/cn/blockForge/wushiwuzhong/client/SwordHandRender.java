package cn.blockforge.wushiwuzhong.client;

import cn.blockforge.wushiwuzhong.logic.SwordHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * 第一人称的 1.8 举剑格挡架势。
 *
 * <p>1.8 的剑格挡在第一人称里并不是「保持原姿势」，而是把剑推到身前、斜着架起来。
 * 这套位移与旋转的数值取自 15w33b —— 1.9 快照里真正删掉剑格挡之前的最后一版，
 * 原版 {@code ItemRenderer} 的 {@code EnumAction.BLOCK} 分支就是这三条旋转加一段位移，
 * 接在手臂基准位移（{@code 0.56, -0.52, -0.72}）之后。</p>
 *
 * <p>Forge 的 {@link IClientItemExtensions#applyForgeHandTransform} 正好插在
 * 「手臂基准位移」与「渲染物品」之间：返回 {@code true} 就会跳过原版 BLOCK 分支，
 * 由这里自己摆姿势，然后照常走进物品渲染，模型自身的
 * {@code firstperson_righthand} 变换照样生效——与 1.8 的叠加顺序一致。</p>
 */
public final class SwordHandRender {

    /** 手臂基准位移，与原版 {@code applyItemArmTransform} 完全一致。 */
    private static final float ARM_X = 0.56F;
    private static final float ARM_Y = -0.52F;
    private static final float ARM_Z = -0.72F;
    private static final float EQUIP_Y_SCALE = -0.6F;

    /** 1.8 格挡增量：位移 + 绕 X / Y / Z 的三次旋转。 */
    private static final float BLOCK_X = -0.14142136F;
    private static final float BLOCK_Y = 0.08F;
    private static final float BLOCK_Z = 0.14142136F;
    private static final float BLOCK_ROT_X = -102.25F;
    private static final float BLOCK_ROT_Y = 13.365F;
    private static final float BLOCK_ROT_Z = 78.05F;

    private static final IClientItemExtensions EXTENSION = new IClientItemExtensions() {
        @Override
        public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
                                               ItemStack stack, float partialTick, float equipProcess,
                                               float swingProcess) {
            if (!SwordHolder.isSword(stack) || stack.getUseAnimation() != UseAnim.BLOCK) {
                return false;
            }
            InteractionHand hand = arm == player.getMainArm()
                    ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            if (!player.isUsingItem() || player.getUsedItemHand() != hand) {
                return false;
            }
            // 左手整枝镜像，和原版一样用正负号翻转。
            int way = arm == HumanoidArm.RIGHT ? 1 : -1;
            poseStack.translate(way * ARM_X, ARM_Y + equipProcess * EQUIP_Y_SCALE, ARM_Z);
            poseStack.translate(way * BLOCK_X, BLOCK_Y, BLOCK_Z);
            poseStack.mulPose(Axis.XP.rotationDegrees(BLOCK_ROT_X));
            poseStack.mulPose(Axis.YP.rotationDegrees(way * BLOCK_ROT_Y));
            poseStack.mulPose(Axis.ZP.rotationDegrees(way * BLOCK_ROT_Z));
            return true;
        }
    };

    private SwordHandRender() {
    }

    /** 物品的客户端扩展；只在客户端被 {@code Item#initializeClient} 取走。 */
    public static IClientItemExtensions handExtension() {
        return EXTENSION;
    }
}
