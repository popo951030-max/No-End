package cn.blockforge.wushiwuzhong.registry;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组自己的创造模式物品栏。
 *
 * <p>无始无终不再塞进原版的「战斗」栏，而是独占一个「无始无终」页，
 * 方便一键取用，也让原版栏位保持干净。</p>
 */
public final class ModTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WushiwuzhongMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> WUSHIWUZHONG =
            TABS.register("wushiwuzhong", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wushiwuzhong"))
                    .icon(() -> new ItemStack(ModItems.WUSHIWUZHONG.get()))
                    .withBackgroundLocation(ResourceLocation.fromNamespaceAndPath(
                            "minecraft", "textures/gui/container/creative_inventory/tab_items.png"))
                    .displayItems((parameters, output) -> output.accept(ModItems.WUSHIWUZHONG.get()))
                    .build());

    private ModTabs() {
    }
}
