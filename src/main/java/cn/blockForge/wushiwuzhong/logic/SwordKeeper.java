package cn.blockforge.wushiwuzhong.logic;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import cn.blockforge.wushiwuzhong.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 绑定：无始无终一旦属于某位玩家，就再也离不开他。
 *
 * <ul>
 *   <li>玩家身上出现过剑，就把 UUID 记进世界存档，永久认主；</li>
 *   <li>每 tick 检查：万一剑被 {@code /clear}、容器搬走、末影箱藏起、
 *       甚至整包被替换掉，立刻从容器/末影箱里取回，取不回就补一把；</li>
 *   <li>掉落被 {@code ItemTossEvent} + {@code onDroppedByPlayer} 双重拦下；</li>
 *   <li>物品实体形态被禁止生成，避免岩浆、仙人掌、TNT、凋零头颅、
 *       末影水晶等任何爆炸把它销毁；</li>
 *   <li>死亡（被放行的模组事件）后背包原样带过重生。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = WushiwuzhongMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SwordKeeper {

    private static final String DATA_NAME = "wushiwuzhong_owners";

    /** 放行死亡瞬间的背包快照，重生时倒回。 */
    private static final Map<UUID, ListTag> DEATH_SNAPSHOTS = new HashMap<>();

    private SwordKeeper() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        OwnerData data = ownerData(player.server.overworld());

        if (!data.isOwner(player.getUUID()) && SwordHolder.holdsSword(player)) {
            data.addOwner(player.getUUID());
        }
        if (!data.isOwner(player.getUUID())) {
            return;
        }

        sealInventory(player);
        if (!SwordHolder.holdsSword(player)) {
            restoreSword(player);
        }
    }

    /**
     * 掉落拦截。
     *
     * <p>两件事一起办：</p>
     * <ul>
     *   <li>无始无终本身永远丢不出去，取消后原样退回背包；</li>
     *   <li>只要剑在身，玩家按 Q（含背包界面里的 Q）丢任何东西都会被拦下，
     *       连丢出的物品实体都不会生成，自然也不会有那一下「东西飞出去」的画面。
     *       其它模组因为背包塞不下而主动往地上丢的情况不受影响。</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getEntity().getItem();
        boolean sword = SwordHolder.isSword(stack);
        if (!sword && !(SwordHolder.holdsSword(player) && fromDropKey())) {
            return;
        }
        event.setCanceled(true);
        ItemStack back = stack.copy();
        if (sword) {
            back.setCount(1);
        }
        SwordHolder.sealDamage(back);
        if (!player.getInventory().add(back) && !back.isEmpty()) {
            // 背包满了也得留住东西：先副手，再末影箱。
            if (player.getInventory().getItem(Inventory.SLOT_OFFHAND).isEmpty()) {
                player.getInventory().setItem(Inventory.SLOT_OFFHAND, back);
            } else {
                player.getEnderChestInventory().addItem(back);
            }
        }
    }

    /**
     * 这一次掉落是不是玩家自己按 Q 触发的。
     *
     * <p>服务端在这一层拿不到「哪个键」的信息，只好看一眼调用栈：
     * 玩家按 Q 一定是从 {@code handlePlayerAction} 或背包界面的
     * {@code handleContainerClick} 进来的。</p>
     */
    private static boolean fromDropKey() {
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> {
            if (!"net.minecraft.server.network.ServerGamePacketListenerImpl".equals(frame.getClassName())) {
                return false;
            }
            String method = frame.getMethodName();
            return "handlePlayerAction".equals(method) || "handleContainerClick".equals(method);
        }));
    }

    /**
     * 禁止无始无终以物品实体的形式存在于世界上。
     *
     * <p>物品实体会被岩浆、仙人掌、TNT、凋零头颅、末影水晶的爆炸销毁，
     * 也会被 {@code /kill @e[type=item]} 抹掉；索性不让它出现在地上，
     * 直接交还持有者。</p>
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }
        if (!SwordHolder.isSword(itemEntity.getItem())) {
            return;
        }
        event.setCanceled(true);
        if (itemEntity.getOwner() instanceof ServerPlayer owner) {
            ItemStack stack = itemEntity.getItem().copy();
            stack.setCount(1);
            giveBack(owner, stack);
        }
    }

    /** 展示框也挂不住：无始无终只待在主人的背包里。 */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getTarget() instanceof ItemFrame && SwordHolder.isSword(event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    /** 容器要关上了：趁菜单还在，把塞进去的剑捞回背包。 */
    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ownerData(player.server.overworld()).isOwner(player.getUUID())) {
            return;
        }
        if (SwordHolder.holdsSword(player)) {
            return;
        }
        ItemStack recovered = takeSwordFrom(event.getContainer(), player);
        if (!recovered.isEmpty()) {
            giveBack(player, recovered);
        }
    }

    /**
     * 被放行的模组事件死亡发生的瞬间，先把整包（含护甲、副手）存一份。
     *
     * <p>原版玩家的背包在 {@code die()} 里就被 {@code dropAll()} 清空了，
     * 等到重生时的 {@code PlayerEvent.Clone} 再去抄「原背包」只会拿到空包，
     * 所以必须在 {@code LivingDeathEvent} 这里抢在清空之前抓快照。</p>
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!CommandGuard.isDeathAllowed(player)) {
            return;
        }
        if (!ownerData(player.server.overworld()).isOwner(player.getUUID())) {
            return;
        }
        DEATH_SNAPSHOTS.put(player.getUUID(), player.getInventory().save(new ListTag()));
    }

    /** 重生时把快照原样倒回新玩家的背包。 */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        Player clone = event.getEntity();
        ListTag snapshot = DEATH_SNAPSHOTS.remove(clone.getUUID());
        if (snapshot != null) {
            clone.getInventory().load(snapshot);
            return;
        }
        // 兜底：万一原背包还没被清（例如 keepInventory），照样把装备带过去。
        Player original = event.getOriginal();
        if (original instanceof ServerPlayer serverOriginal
                && ownerData(serverOriginal.server.overworld()).isOwner(clone.getUUID())) {
            clone.getInventory().replaceWith(original.getInventory());
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private static OwnerData ownerData(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(OwnerData::load, OwnerData::new, DATA_NAME);
    }

    private static void sealInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int size = inventory.getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            SwordHolder.sealDamage(inventory.getItem(slot));
        }
    }

    /** 剑不见了：先从打开着的容器/合成格拿回来，再从末影箱拿，最后才补一把。 */
    private static void restoreSword(ServerPlayer player) {
        ItemStack recovered = takeFromMenu(player);
        if (recovered.isEmpty()) {
            recovered = takeFromEnderChest(player);
        }
        if (recovered.isEmpty()) {
            recovered = new ItemStack(ModItems.WUSHIWUZHONG.get());
        }
        giveBack(player, recovered);
    }

    private static void giveBack(ServerPlayer player, ItemStack stack) {
        stack.setCount(1);
        SwordHolder.sealDamage(stack);
        Inventory inventory = player.getInventory();
        if (!inventory.add(stack) && inventory.getItem(Inventory.SLOT_OFFHAND).isEmpty()) {
            inventory.setItem(Inventory.SLOT_OFFHAND, stack);
        }
    }

    /** 打开着的容器菜单、合成格、以及鼠标上「拿着」的那一格。 */
    private static ItemStack takeFromMenu(ServerPlayer player) {
        return takeSwordFrom(player.containerMenu, player);
    }

    private static ItemStack takeSwordFrom(AbstractContainerMenu menu, ServerPlayer player) {
        if (menu == null) {
            return ItemStack.EMPTY;
        }
        ItemStack carried = menu.getCarried();
        if (SwordHolder.isSword(carried)) {
            ItemStack copy = carried.copy();
            menu.setCarried(ItemStack.EMPTY);
            menu.broadcastChanges();
            return copy;
        }
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (SwordHolder.isSword(stack)) {
                ItemStack copy = stack.copy();
                slot.set(ItemStack.EMPTY);
                slot.setChanged();
                menu.broadcastChanges();
                return copy;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack takeFromEnderChest(ServerPlayer player) {
        PlayerEnderChestContainer enderChest = player.getEnderChestInventory();
        for (int slot = 0; slot < enderChest.getContainerSize(); slot++) {
            ItemStack stack = enderChest.getItem(slot);
            if (SwordHolder.isSword(stack)) {
                ItemStack copy = stack.copy();
                enderChest.setItem(slot, ItemStack.EMPTY);
                enderChest.setChanged();
                return copy;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 世界存档里的「认主」名单。 */
    public static final class OwnerData extends SavedData {

        private final Set<UUID> owners = new HashSet<>();

        public static OwnerData load(CompoundTag tag) {
            OwnerData data = new OwnerData();
            long[] raw = tag.getLongArray("owners");
            for (int i = 0; i + 1 < raw.length; i += 2) {
                data.owners.add(new UUID(raw[i], raw[i + 1]));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            long[] raw = new long[owners.size() * 2];
            int i = 0;
            for (UUID id : owners) {
                raw[i++] = id.getMostSignificantBits();
                raw[i++] = id.getLeastSignificantBits();
            }
            tag.putLongArray("owners", raw);
            return tag;
        }

        public boolean isOwner(UUID id) {
            return owners.contains(id);
        }

        public void addOwner(UUID id) {
            if (owners.add(id)) {
                setDirty();
            }
        }
    }
}
