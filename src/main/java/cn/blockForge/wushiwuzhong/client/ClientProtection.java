package cn.blockforge.wushiwuzhong.client;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import cn.blockforge.wushiwuzhong.logic.SwordHolder;
import cn.blockforge.wushiwuzhong.network.ClearBlacklistPacket;
import cn.blockforge.wushiwuzhong.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端侧的死亡界面 / 红屏屏蔽与 Shift + T 处理。
 *
 * <p>只有背包里带着无始无终时才生效，所以不影响其他玩家。</p>
 */
@Mod.EventBusSubscriber(modid = WushiwuzhongMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientProtection {

    private ClientProtection() {
    }

    /** 死亡界面永不出现——除非是无始无终特意放行的模组事件死亡。 */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof DeathScreen && clientHoldsSword() && clientAlive()) {
            event.setCanceled(true);
            return;
        }
        // Shift + T 是「清空生物黑名单」；原版的 T 会顺手开聊天，这里按掉。
        if (event.getNewScreen() instanceof ChatScreen && Screen.hasShiftDown() && clientHoldsSword()) {
            event.setCanceled(true);
        }
    }

    /** 低血量红屏（VIGNETTE 叠加层）一并隐藏。 */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (clientHoldsSword() && event.getOverlay().id().equals(VanillaGuiOverlay.VIGNETTE.id())) {
            event.setCanceled(true);
        }
    }

    /** Shift + T：把清空黑名单的请求发给服务端。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        boolean clicked = false;
        while (ClientKeys.CLEAR_BLACKLIST.consumeClick()) {
            clicked = true;
        }
        if (!clicked || !Screen.hasShiftDown() || !clientHoldsSword()) {
            return;
        }
        NetworkHandler.CHANNEL.sendToServer(new ClearBlacklistPacket());
    }

    private static boolean clientHoldsSword() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && SwordHolder.holdsSword(player);
    }

    /** 真被放行死亡时血量会归零，此时不能挡住死亡界面，否则玩家卡在死亡状态出不来。 */
    private static boolean clientAlive() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getHealth() > 0.0F;
    }
}
