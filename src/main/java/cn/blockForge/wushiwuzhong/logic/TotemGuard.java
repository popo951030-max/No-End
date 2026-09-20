package cn.blockforge.wushiwuzhong.logic;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import cn.blockforge.wushiwuzhong.registry.ModItems;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 无始无终造成的死亡，不死图腾救不回来。
 *
 * <p>两条路都会走这里：</p>
 * <ul>
 *   <li>右键虚空清除——目标身上带着 {@link VoidPurge#PURGE_TAG}；</li>
 *   <li>持剑玩家近战补刀——伤害来源是拿着无始无终的玩家。</li>
 * </ul>
 * <p>{@link VoidPurge} 里还会额外把图腾从目标手上摘掉，双保险。</p>
 */
@Mod.EventBusSubscriber(modid = WushiwuzhongMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TotemGuard {

    private TotemGuard() {
    }

    @SubscribeEvent
    public static void onUseTotem(LivingUseTotemEvent event) {
        if (event.getEntity().getTags().contains(VoidPurge.PURGE_TAG)) {
            event.setCanceled(true);
            return;
        }
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof Player player
                && SwordHolder.holdsSword(player)
                && player.getMainHandItem().is(ModItems.WUSHIWUZHONG.get())) {
            event.setCanceled(true);
        }
    }
}
