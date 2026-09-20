package cn.blockforge.wushiwuzhong.client;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Shift + T：清空无始无终的生物黑名单。
 *
 * <p>快捷键绑在 T 上并要求按住 Shift。因为原版的 T 同时也是「打开聊天」，
 * 所以按下时还要把聊天界面挡掉一次（见 {@link ClientProtection}），
 * 否则会一边清名单一边弹出输入框。按键的实际处理在
 * {@link ClientProtection#onClientTick}。</p>
 */
@Mod.EventBusSubscriber(modid = WushiwuzhongMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientKeys {

    public static final KeyMapping CLEAR_BLACKLIST = new KeyMapping(
            "key.wushiwuzhong.clear_blacklist",
            KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_T,
            "key.categories.wushiwuzhong");

    private ClientKeys() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(CLEAR_BLACKLIST);
    }
}
