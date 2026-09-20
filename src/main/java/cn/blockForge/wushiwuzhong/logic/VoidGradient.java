package cn.blockforge.wushiwuzhong.logic;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;

/**
 * 无始无终的「灰 → 白 → 灰」无限渐变色。
 *
 * <p>颜色不是写死的，而是按「当前毫秒 + 第几个字」算出来的一条三角波：
 * 灰 {@code #808080} 爬到白 {@code #FFFFFF}，再原路退回灰，如此往复。
 * 因为每个字各带一份独立的颜色样式，整串文字看上去就是一道不断流动的灰白渐变。</p>
 *
 * <p>物品名与悬浮说明每一帧都会重新构建，所以这里不需要任何「定时刷新」的额外逻辑，
 * 只要取值随时间变化，颜色自己就会一直循环下去。</p>
 */
public final class VoidGradient {

    /** 走完一整轮「灰 → 白 → 灰」所需的毫秒数。 */
    private static final long CYCLE_MILLIS = 2400L;

    /** 多少个字跨过完整的一轮渐变；数字越小，同一行里的颜色落差越明显。 */
    private static final double CHARS_PER_CYCLE = 14.0D;

    /** 渐变最暗的一端（灰）。 */
    private static final int DARKEST = 0x80;

    private VoidGradient() {
    }

    /** 把一整段文字（含翻译键）逐字染成当前时刻的渐变色。 */
    public static MutableComponent of(Component source) {
        return of(source, 0);
    }

    /**
     * @param offset 该段文字在本行里的起始字序，用来让整行连成一道连续的渐变
     */
    public static MutableComponent of(Component source, int offset) {
        MutableComponent out = Component.empty();
        int[] index = {offset};
        source.visit((FormattedText.StyledContentConsumer<Object>) (style, text) -> {
            for (int i = 0; i < text.length(); i++) {
                out.append(Component.literal(String.valueOf(text.charAt(i)))
                        .withStyle(style.withColor(colorAt(index[0]++))));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /** 纯字符串版本。 */
    public static MutableComponent of(String text) {
        return of(Component.literal(text));
    }

    /**
     * 翻译键版本。
     *
     * <p>先把译文取出来再逐字染色——但只有在语言文件真的加载了的时候才这么做。
     * 专用服务端没有语言文件，{@code getString()} 会原样吐出翻译键；那种情况下
     * 直接把可翻译组件交回去，让客户端用自己的语言去解析，免得名字变成一串键名。</p>
     */
    public static Component ofTranslatable(String key) {
        Component base = Component.translatable(key);
        if (base.getString().equals(key)) {
            return base;
        }
        return of(base);
    }

    /** 带字序偏移的翻译键版本，用于让一整行连成一道连续渐变。 */
    public static Component ofTranslatable(String key, int offset) {
        Component base = Component.translatable(key);
        if (base.getString().equals(key)) {
            return base;
        }
        return of(base, offset);
    }

    /** 第 {@code charIndex} 个字此刻应该用哪一档灰。 */
    private static int colorAt(int charIndex) {
        double phase = (System.currentTimeMillis() % CYCLE_MILLIS) / (double) CYCLE_MILLIS
                + charIndex / CHARS_PER_CYCLE;
        double wrapped = phase - Math.floor(phase);
        double triangle = wrapped < 0.5D ? wrapped * 2.0D : 2.0D - wrapped * 2.0D;
        int value = DARKEST + (int) Math.round((0xFF - DARKEST) * triangle);
        return (value << 16) | (value << 8) | value;
    }
}
