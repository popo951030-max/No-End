package cn.blockforge.wushiwuzhong.registry;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import cn.blockforge.wushiwuzhong.recipe.WushiwuzhongRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 配方序列化器注册表。 */
public final class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, WushiwuzhongMod.MOD_ID);

    public static final RegistryObject<WushiwuzhongRecipe.Serializer> WUSHIWUZHONG =
            SERIALIZERS.register("wushiwuzhong", WushiwuzhongRecipe.Serializer::new);

    private ModRecipes() {
    }
}
