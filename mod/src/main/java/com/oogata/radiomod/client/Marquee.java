package com.oogata.radiomod.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** 入りきらない文字を、右から左へ流して描く。 */
final class Marquee {

    private static final double SPEED = 28; // px/秒
    private static final int GAP = 28;
    private static final double HOLD_SECONDS = 1.5;

    private Marquee() {
    }

    /** 入りきるならそのまま。入りきらないときは、最初の 1.5 秒は止めて、そのあと流す。startNanos は流し始めの基準。 */
    static void draw(GuiGraphics g, Font font, String text, int left, int right, int y, int color, long startNanos) {
        int width = font.width(text);
        if (width <= right - left) {
            g.drawString(font, text, left, y, color, false);
            return;
        }
        double seconds = (System.nanoTime() - startNanos) / 1e9;
        int period = width + GAP;
        int offset = (int) (Math.max(0, seconds - HOLD_SECONDS) * SPEED) % period;
        g.enableScissor(left, y - 1, right, y + 10);
        g.drawString(font, text, left - offset, y, color, false);
        g.drawString(font, text, left - offset + period, y, color, false);
        g.disableScissor();
    }
}
