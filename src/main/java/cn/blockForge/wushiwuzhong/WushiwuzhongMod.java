package cn.blockforge.wushiwuzhong;

import cn.blockforge.wushiwuzhong.network.NetworkHandler;
import cn.blockforge.wushiwuzhong.registry.ModItems;
import cn.blockforge.wushiwuzhong.registry.ModRecipes;
import cn.blockforge.wushiwuzhong.registry.ModTabs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 无始无终 —— 主入口。
 *
 * <p>模组只注册一件物品：无始无终之剑。它的全部行为分散在
 * {@code item}（剑本身）、{@code logic}（破坏、清除、处决、保护、指令拦截、
 * 绑定、增益、黑名单、虚空救援）、{@code network}（Shift + T 与渲染剥离）
 * 与 {@code client}（死亡界面、红屏屏蔽、格挡姿势、渲染剥离）几个包里。</p>
 */
@Mod(WushiwuzhongMod.MOD_ID)
public final class WushiwuzhongMod {

    public static final String MOD_ID = "wushiwuzhong";

    public WushiwuzhongMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        // 独占一个模组创造栏，不再加入原版的「战斗」栏。
        ModTabs.TABS.register(modBus);
        NetworkHandler.register();
    }
}

