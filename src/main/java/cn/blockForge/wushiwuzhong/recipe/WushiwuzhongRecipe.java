package cn.blockforge.wushiwuzhong.recipe;

import cn.blockforge.wushiwuzhong.registry.ModItems;
import cn.blockforge.wushiwuzhong.registry.ModRecipes;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 无始无终的合成配方：龙蛋必须压在工作台正中央，其余八样材料
 * 围着中心任意摆放，位置不限、顺序不限，只要每样各放一个就行。
 *
 * <p>原版的 shaped / shapeless 都表达不了「中心锁死 + 外圈全排列」，
 * 所以自建了一个 {@link RecipeType#CRAFTING} 的配方类型，
 * 让工作台照常识别、照常消耗材料。</p>
 */
public class WushiwuzhongRecipe extends CustomRecipe {

    /** 龙蛋之外的八样材料，外圈各一个。 */
    public static final List<Item> RING_MATERIALS = List.of(
            Items.NETHERITE_SWORD,
            Items.NETHERITE_PICKAXE,
            Items.END_CRYSTAL,
            Items.TOTEM_OF_UNDYING,
            Items.HEART_OF_THE_SEA,
            Items.NETHER_STAR,
            Items.DRAGON_BREATH,
            Items.WRITABLE_BOOK);

    public WushiwuzhongRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        if (container.getContainerSize() != 9) {
            return false;
        }
        ItemStack core = container.getItem(4);
        if (!core.is(Items.DRAGON_EGG) || core.getCount() != 1) {
            return false;
        }
        List<Item> remaining = new ArrayList<>(RING_MATERIALS);
        for (int slot = 0; slot < 9; slot++) {
            if (slot == 4) {
                continue;
            }
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || stack.getCount() != 1) {
                return false;
            }
            if (!remaining.remove(stack.getItem())) {
                return false;
            }
        }
        return remaining.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        return new ItemStack(ModItems.WUSHIWUZHONG.get());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return new ItemStack(ModItems.WUSHIWUZHONG.get());
    }

    /**
     * 让配方书把这条自建配方当成「普通配方」展示。
     *
     * <p>{@link CustomRecipe} 默认 {@code isSpecial() == true}，配方书会直接跳过它；
     * 想在拿到下界之星后于配方书里看到并一键摆料，就必须显式返回 false，
     * 同时给出 {@link #getIngredients()} 供配方书画幽灵物品。</p>
     */
    @Override
    public boolean isSpecial() {
        return false;
    }

    /**
     * 配方书用的九宫格摆法：正中央龙蛋，外圈八样材料各一。
     *
     * <p>只影响配方书里显示的幽灵摆位与「一键摆放」，实际判定仍是
     * {@link #matches} 里的「中心锁死 + 外圈任意排列」。</p>
     */
    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.withSize(9, Ingredient.EMPTY);
        ingredients.set(4, Ingredient.of(Items.DRAGON_EGG));
        ingredients.set(0, Ingredient.of(RING_MATERIALS.get(0)));
        ingredients.set(1, Ingredient.of(RING_MATERIALS.get(1)));
        ingredients.set(2, Ingredient.of(RING_MATERIALS.get(2)));
        ingredients.set(3, Ingredient.of(RING_MATERIALS.get(3)));
        ingredients.set(5, Ingredient.of(RING_MATERIALS.get(4)));
        ingredients.set(6, Ingredient.of(RING_MATERIALS.get(5)));
        ingredients.set(7, Ingredient.of(RING_MATERIALS.get(6)));
        ingredients.set(8, Ingredient.of(RING_MATERIALS.get(7)));
        return ingredients;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        return NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.WUSHIWUZHONG.get();
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeType.CRAFTING;
    }

    /** 无参数的序列化器：配方内容写死在代码里。 */
    public static class Serializer implements RecipeSerializer<WushiwuzhongRecipe> {

        @Override
        public WushiwuzhongRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new WushiwuzhongRecipe(id, CraftingBookCategory.MISC);
        }

        @Override
        public WushiwuzhongRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new WushiwuzhongRecipe(id, CraftingBookCategory.MISC);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, WushiwuzhongRecipe recipe) {
            // 没有需要同步的数据。
        }
    }
}
