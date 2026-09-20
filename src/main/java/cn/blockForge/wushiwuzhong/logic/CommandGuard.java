package cn.blockforge.wushiwuzhong.logic;

import cn.blockforge.wushiwuzhong.WushiwuzhongMod;
import com.mojang.brigadier.ParseResults;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指令保护。
 *
 * <ul>
 *   <li>{@code /ban}、{@code /ban-ip}、{@code /kick}：目标可能是持剑玩家时整条取消；</li>
 *   <li>{@code /clear}：持剑玩家的物品清不掉；</li>
 *   <li>{@code /kill}、模组的 {@code /damage} 之类伤害指令：拦下；</li>
 *   <li>唯一例外：{@code /kill} 若来自「其它模组的事件」——调用栈里出现了非原版、
 *       非 Forge、非本模组的类，就说明是别的模组在推着玩家走剧情，此时放行，
 *       并在这一个游戏刻内关闭不死保护，让死亡正常发生。</li>
 * </ul>
 *
 * <p>{@code execute ... run <cmd>} 会被拆到真正的子指令再判断。</p>
 */
@Mod.EventBusSubscriber(modid = WushiwuzhongMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommandGuard {

    private static final Set<String> EXILE_COMMANDS = Set.of("ban", "ban-ip", "kick");
    private static final Set<String> STRIP_COMMANDS = Set.of("clear");
    /** 能直接替换 / 改写背包内容的指令，同样不许落到持有者头上。 */
    private static final Set<String> INVENTORY_COMMANDS = Set.of("item", "data");
    /**
     * 「隔空翻别人背包 / 末影箱」这类指令的常见写法。
     *
     * <p>名字五花八门，靠穷举是不可能穷尽的，所以 {@link InventoryGuard}
     * 还在菜单层面兜了一道；这里挡的是最常见的几种，让报错更直白。</p>
     */
    private static final Set<String> PEEK_COMMANDS = Set.of(
            "invsee", "openinv", "openinventory", "seeinv", "inventory", "inv",
            "endersee", "enderchest", "ec", "backpack", "bp");

    /** 本刻被放行、允许真正死亡的玩家。 */
    private static final Set<UUID> DEATH_ALLOWED = ConcurrentHashMap.newKeySet();

    private CommandGuard() {
    }

    public static boolean isDeathAllowed(Player player) {
        return DEATH_ALLOWED.contains(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            DEATH_ALLOWED.clear();
        }
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        if (parse == null) {
            return;
        }
        String raw = normalize(parse.getReader().getString());
        if (raw.isEmpty()) {
            return;
        }
        String effective = stripExecuteChain(raw);
        String root = rootOf(effective);
        if (root.isEmpty()) {
            return;
        }

        CommandSourceStack source = parse.getContext().getSource();

        if (EXILE_COMMANDS.contains(root)) {
            if (targetsSwordHolder(source, effective)) {
                cancel(event, source, "message.wushiwuzhong.exile_blocked");
            }
            return;
        }
        if (STRIP_COMMANDS.contains(root)) {
            if (targetsSwordHolder(source, effective)) {
                cancel(event, source, "message.wushiwuzhong.clear_blocked");
            }
            return;
        }
        if (INVENTORY_COMMANDS.contains(root)) {
            if (targetsSwordHolder(source, effective)) {
                cancel(event, source, "message.wushiwuzhong.inventory_blocked");
            }
            return;
        }
        if (root.equals("deop")) {
            // 持剑者的 OP 是剑给的，谁也别想用指令摘掉。
            if (targetsSwordHolder(source, effective)) {
                cancel(event, source, "message.wushiwuzhong.op_blocked");
            }
            return;
        }
        if (PEEK_COMMANDS.contains(root)) {
            if (targetsSwordHolder(source, effective)) {
                cancel(event, source, "message.wushiwuzhong.inventory_peek_blocked");
            }
            return;
        }
        if (root.equals("kill")) {
            // 本模组自己的处决链发的 /kill，是流程的一部分，直接放行。
            if (!isOwnExecutionChain()) {
                handleKill(event, source, effective);
            }
            return;
        }
        if (root.equals("damage")) {
            if (targetsSwordHolder(source, effective)) {
                cancel(event, source, "message.wushiwuzhong.damage_blocked");
            }
        }
    }

    // ------------------------------------------------------------------
    // /kill 的特殊规则
    // ------------------------------------------------------------------

    private static void handleKill(CommandEvent event, CommandSourceStack source, String command) {
        Set<ServerPlayer> targets = swordHoldersTargeted(source, command);
        if (targets.isEmpty()) {
            return;
        }
        if (isForeignModContext()) {
            // 其它模组的事件在推着玩家走：这一个游戏刻内不设防。
            for (ServerPlayer player : targets) {
                DEATH_ALLOWED.add(player.getUUID());
            }
            return;
        }
        cancel(event, source, "message.wushiwuzhong.kill_blocked");
    }

    /**
     * 这条 {@code /kill} 是不是无始无终自己的兜底链发出来的。
     *
     * <p>右键清除与左键处决都需要走一次 {@code /kill}，那属于武器流程本身，
     * 不能被「保护持剑者」的规则误伤。认栈即可：调用链里出现
     * {@code VoidPurge} 就说明是自己人。</p>
     */
    private static boolean isOwnExecutionChain() {
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
                frame.getClassName().equals("cn.blockforge.wushiwuzhong.logic.VoidPurge")));
    }

    /**
     * 调用栈里是否出现了「非原版 / 非 Forge / 非本模组」的类。
     *
     * <p>玩家自己在聊天栏敲 {@code /kill} 时，整条栈都是原版类；
     * 别的模组在它的事件处理里发指令时，栈上会留下它自己的类名。</p>
     */
    private static boolean isForeignModContext() {
        return StackWalker.getInstance().walk(frames ->
                frames.map(StackWalker.StackFrame::getClassName).anyMatch(CommandGuard::isForeignClass));
    }

    private static boolean isForeignClass(String name) {
        return !name.startsWith("java.")
                && !name.startsWith("javax.")
                && !name.startsWith("jdk.")
                && !name.startsWith("sun.")
                && !name.startsWith("com.sun.")
                && !name.startsWith("com.mojang.")
                && !name.startsWith("net.minecraft.")
                && !name.startsWith("net.minecraftforge.")
                && !name.startsWith("cpw.mods.")
                && !name.startsWith("org.spongepowered.")
                && !name.startsWith("org.apache.")
                && !name.startsWith("org.slf4j.")
                && !name.startsWith("io.netty.")
                && !name.startsWith("it.unimi.")
                && !name.startsWith("com.google.")
                && !name.startsWith("org.joml.")
                && !name.startsWith("cn.blockforge.wushiwuzhong.");
    }

    // ------------------------------------------------------------------
    // 目标解析
    // ------------------------------------------------------------------

    /** 命令可能命中的、背包里带着剑的玩家。 */
    private static Set<ServerPlayer> swordHoldersTargeted(CommandSourceStack source, String command) {
        Set<ServerPlayer> result = new LinkedHashSet<>();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return result;
        }
        String args = argsOf(command);
        Entity sourceEntity = source.getEntity();

        if (args.isEmpty() || args.contains("@s")) {
            if (sourceEntity instanceof ServerPlayer self && SwordHolder.holdsSword(self)) {
                result.add(self);
            }
        }
        for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
            if (!SwordHolder.holdsSword(candidate)) {
                continue;
            }
            if (mentionsPlayer(args, candidate, sourceEntity)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static boolean targetsSwordHolder(CommandSourceStack source, String command) {
        return !swordHoldersTargeted(source, command).isEmpty();
    }

    /** 参数里有没有点名（或通配）到这位玩家。 */
    private static boolean mentionsPlayer(String args, ServerPlayer candidate, Entity sourceEntity) {
        if (args.contains("@a") || args.contains("@p") || args.contains("@r")) {
            return true;
        }
        if (args.contains("@s") && sourceEntity == candidate) {
            return true;
        }
        if (selectorHitsPlayers(args)) {
            return true;
        }
        String name = candidate.getGameProfile().getName();
        return !name.isEmpty() && args.contains(name);
    }

    /**
     * 粗解析第一个 {@code @e} 选择器：没有 {@code type=}、或者 type 点名玩家时，
     * 才算「可能命中玩家」。这样 {@code /kill @e[type=zombie]} 不会被误拦。
     */
    private static boolean selectorHitsPlayers(String args) {
        int index = args.indexOf("@e");
        if (index < 0) {
            return false;
        }
        String rest = args.substring(index + 2);
        if (!rest.startsWith("[")) {
            return true;
        }
        int close = rest.indexOf(']');
        if (close < 0) {
            return true;
        }
        String type = extractSelectorType(rest.substring(1, close));
        if (type == null) {
            return true;
        }
        if (type.startsWith("!")) {
            return !isPlayerType(type.substring(1));
        }
        return isPlayerType(type);
    }

    private static String extractSelectorType(String body) {
        for (String part : body.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("type=")) {
                return trimmed.substring("type=".length()).trim();
            }
        }
        return null;
    }

    private static boolean isPlayerType(String type) {
        return type.equals("player") || type.equals("minecraft:player");
    }

    // ------------------------------------------------------------------
    // 文本处理
    // ------------------------------------------------------------------

    private static String normalize(String raw) {
        String trimmed = raw.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1).trim() : trimmed;
    }

    /** {@code execute ... run kill @p} → {@code kill @p}。 */
    private static String stripExecuteChain(String command) {
        String result = command;
        while (result.toLowerCase(Locale.ROOT).startsWith("execute")) {
            int run = result.indexOf(" run ");
            if (run < 0) {
                return result;
            }
            result = result.substring(run + 5).trim();
        }
        return result;
    }

    private static String rootOf(String command) {
        String first = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int colon = first.indexOf(':');
        return colon >= 0 ? first.substring(colon + 1) : first;
    }

    private static String argsOf(String command) {
        int space = command.indexOf(' ');
        return space < 0 ? "" : command.substring(space + 1).trim();
    }

    private static void cancel(CommandEvent event, CommandSourceStack source, String translationKey) {
        event.setCanceled(true);
        source.sendSuccess(() -> Component.translatable(translationKey)
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }
}
