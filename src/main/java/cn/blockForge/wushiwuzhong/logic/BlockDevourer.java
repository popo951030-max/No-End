package cn.blockforge.wushiwuzhong.logic;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 全方块秒破 + 自动收纳。
 *
 * <p>背包里带着无始无终时，左键点中任意方块立刻破坏，不需要挖掘进度，
 * 也不管方块原本硬度多少（基岩、命令方块、屏障照破）。</p>
 *
 * <p>掉落规则保持原版：正常掉落直接进背包；没有战利品表的方块
 * （基岩、命令方块、刷怪笼等）则把方块本体变成物品塞进背包。</p>
 */
@Mod.EventBusSubscriber(modid = WushiwuzhongMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlockDevourer {

    private BlockDevourer() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getSide() != LogicalSide.SERVER) {
            return;
        }
        Player player = event.getEntity();
        if (!SwordHolder.holdsSword(player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        // 只要剑在身，就别让原版的那套挖掘逻辑插手
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCanceled(true);

        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }

        BlockPos pos = event.getPos();
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        harvest(level, player, pos, state);
    }

    private static void harvest(ServerLevel level, Player player, BlockPos pos, BlockState state) {
        ItemStack tool = player.getMainHandItem();
        BlockEntity blockEntity = level.getBlockEntity(pos);

        // 先按原版规则算掉落，再动方块
        List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, player, tool);
        List<ItemStack> collected = new ArrayList<>(drops.size() + 1);
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                collected.add(drop);
            }
        }
        if (collected.isEmpty()) {
            // 基岩、命令方块、刷怪笼这类没有战利品表的方块：直接给本体
            Item item = state.getBlock().asItem();
            if (item != Items.AIR) {
                collected.add(new ItemStack(item));
            }
        }

        // 箱子、木桶、熔炉这类容器：removeBlock 不会吐出内容物，先手动收走，
        // 否则里面的东西会随方块一起消失。潜影盒例外 —— 它的内容跟物品 NBT 走。
        if (blockEntity instanceof Container container
                && !(blockEntity instanceof ShulkerBoxBlockEntity)) {
            int size = container.getContainerSize();
            for (int slot = 0; slot < size; slot++) {
                ItemStack content = container.getItem(slot);
                if (!content.isEmpty()) {
                    collected.add(content.copy());
                }
            }
            container.clearContent();
        }

        level.removeBlock(pos, false);

        for (ItemStack stack : collected) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }

        // 原版的破坏音效 + 碎块粒子
        level.levelEvent(null, 2001, pos, Block.getId(state));
    }
}
