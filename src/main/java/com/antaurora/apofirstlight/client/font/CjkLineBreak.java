package com.antaurora.apofirstlight.client.font;

/**
 * Where wrapped text may break inside CJK runs (docs/ui/afl_font_v1.md section 4, item 6). Vanilla wraps only at spaces and
 * falls back to the last space on the line, so Chinese with spaced numbers ("紧凑可靠的 9mm 制式手枪…") broke right
 * after the number and left a short first line. A break is allowed before {@code cur} when:
 * <ul>
 *   <li>the previous character is CJK (so "12 号" and "48 格" never split, and Latin words keep vanilla spacing rules);</li>
 *   <li>{@code cur} is not a space or newline (vanilla handles those);</li>
 *   <li>{@code cur} may start a line and {@code prev} may end one (closing punctuation never starts a line, opening
 *       punctuation never ends one).</li>
 * </ul>
 */
public final class CjkLineBreak {
    private static final String NO_LINE_START = "，。、：；！？）」』】》〉〕〗〙〛～…—·％‰°℃”’,.:;!?)]}%っゃゅょッャュョー々";
    private static final String NO_LINE_END = "（「『【《〈〔〖〘〚“‘([{";

    private CjkLineBreak() {}

    public static boolean breakBefore(int prev, int cur) {
        if (prev < 0 || cur == ' ' || cur == '\n' || !cjk(prev)) return false;
        return NO_LINE_START.indexOf(cur) < 0 && NO_LINE_END.indexOf(prev) < 0;
    }

    public static boolean cjk(int cp) {
        if (cp >= 0x3000 && cp <= 0x303F || cp >= 0xFF00 && cp <= 0xFFEF) return true;
        Character.UnicodeScript script;
        try {
            script = Character.UnicodeScript.of(cp);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL
                || script == Character.UnicodeScript.BOPOMOFO;
    }
}
