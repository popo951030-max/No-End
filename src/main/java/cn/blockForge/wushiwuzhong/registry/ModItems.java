package cn.blockforge.wushiwuzhong.registry;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import cn.blockforge.wushiwuzhong.item.WushiwuzhongSwordItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 物品注册表。 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, WushiwuzhongMod.MOD_ID);

    public static final RegistryObject<Item> WUSHIWUZHONG =
            ITEMS.register("wushiwuzhong", WushiwuzhongSwordItem::new);

    private ModItems() {
    }
}
