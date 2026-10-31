package com.oogata.radiomod.client;

import com.oogata.radiomod.net.Messages;
import com.oogata.radiomod.net.RadioNet;
import com.oogata.radiomod.sync.ClientSync;
import com.oogata.radiomod.sync.SlotRouter.NowPlaying;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 音楽プレーヤー風の画面。再生中の曲、進行バー、操作ボタン、キューの一覧を出す。
 * 一覧の曲をクリックすると、その曲を最初から再生する。画面を開いている間、約1秒ごとにサーバーへ状態を問い合わせる。
 * 部品(ボタンなど)は使わず、自分で描いて、クリックの場所を調べる。
 */
public final class PlayerScreen extends Screen {

    private static final int PANEL_W = 270;
    private static final int PANEL_H = 232;
    private static final int HEADER_H = 22;
    private static final int NOW_H = 66;
    private static final int ROW_H = 16;
    private static final int PAD = 8;
    private static final int REFRESH_TICKS = 20;

    private static final int BG = 0xF0181818;
    private static final int HEADER_BG = 0xFF3A2A30;
    private static final int NOW_BG = 0xFF4A3038;
    private static final int ROW_CURRENT = 0x60E8A0B8;
    private static final int HOVER = 0x40FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int SUB_TEXT = 0xFFA0A0A0;
    private static final int ACCENT = 0xFFE8A0B8;

    private final Messages.Slot slot;
    private int ticks;
    private int scroll;
    private String shownTitle = "";
    private long titleSinceNanos = System.nanoTime();

    private PlayerScreen(Messages.Slot slot) {
        super(Component.empty());
        this.slot = slot;
    }

    /** そのスロットのプレーヤー画面を開く(すでに同じ画面が開いていれば、何もしない)。 */
    public static void open(Messages.Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PlayerScreen s && s.slot.equals(slot)) {
            return;
        }
        mc.setScreen(new PlayerScreen(slot));
    }

    @Override
    protected void init() {
        refresh();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (++ticks >= REFRESH_TICKS) {
            ticks = 0;
            refresh();
        }
    }

    private void refresh() {
        RadioNet.send(new Messages.Control(slot, Messages.Control.REFRESH, 0));
    }

    private void control(byte action, int arg) {
        RadioNet.send(new Messages.Control(slot, action, arg));
    }

    // ---- 配置 ----

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - PANEL_H) / 2;
    }

    private int listTop() {
        return top() + HEADER_H + NOW_H + 4;
    }

    private int listBottom() {
        return top() + PANEL_H - PAD;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_H);
    }

    /** 操作ボタン(左から: 前へ、再生/一時停止、次へ、ループ、停止)の矩形。{x, y, w, h}。 */
    private int[] button(int index) {
        int w = 44;
        int gap = 4;
        int total = 5 * w + 4 * gap;
        int x = left() + (PANEL_W - total) / 2 + index * (w + gap);
        return new int[] {x, top() + HEADER_H + 40, w, 18};
    }

    private int[] closeButton() {
        return new int[] {left() + PANEL_W - 22, top() + 2, 18, 18};
    }

    private static boolean inside(int[] r, double x, double y) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    // ---- 描画 ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int l = left();
        int t = top();
        Messages.Queue q = QueueStore.get(slot);
        NowPlaying np = ClientSync.router().nowPlaying().stream().filter(n -> n.slot().equals(slot)).findFirst()
                .orElse(null);

        g.fill(l, t, l + PANEL_W, t + PANEL_H, BG);
        g.fill(l, t, l + PANEL_W, t + HEADER_H, HEADER_BG);
        String head = "再生中" + (q != null && !q.label().isEmpty() ? " - " + q.label()
                : slot.slotType() == Messages.Slot.SHARED ? " - 共有" : "");
        g.drawString(font, head, l + PAD, t + 7, TEXT, false);
        int[] close = closeButton();
        if (inside(close, mouseX, mouseY)) {
            g.fill(close[0], close[1], close[0] + close[2], close[1] + close[3], HOVER);
        }
        g.drawCenteredString(font, "x", close[0] + 9, close[1] + 5, TEXT);

        // 再生中の曲
        g.fill(l, t + HEADER_H, l + PANEL_W, t + HEADER_H + NOW_H, NOW_BG);
        String title = currentTitle(q, np);
        if (!title.equals(shownTitle)) {
            shownTitle = title;
            titleSinceNanos = System.nanoTime();
        }
        if (title.isEmpty()) {
            g.drawString(font, q == null ? "読み込み中…" : "キューは空です", l + PAD, t + HEADER_H + 8, SUB_TEXT, false);
        } else {
            Marquee.draw(g, font, title, l + PAD, l + PANEL_W - PAD, t + HEADER_H + 8, TEXT, titleSinceNanos);
        }
        drawProgress(g, font, np, l + PAD, t + HEADER_H + 24, PANEL_W - PAD * 2);

        boolean playing = q != null && q.state() == Messages.Queue.PLAYING;
        String loop = q == null ? "-" : switch (q.loop()) {
            case Messages.Queue.LOOP_SINGLE -> "1曲";
            case Messages.Queue.LOOP_PLAYLIST -> "全体";
            default -> "なし";
        };
        String[] labels = {"|<", playing ? "||" : ">", ">|", "ループ:" + loop, "停止"};
        for (int i = 0; i < labels.length; i++) {
            int[] b = button(i);
            boolean hover = inside(b, mouseX, mouseY);
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], hover ? 0xFF6A4A56 : 0xFF553A44);
            g.drawCenteredString(font, labels[i], b[0] + b[2] / 2, b[1] + 5, TEXT);
        }

        // キューの一覧
        int top = listTop();
        int rows = visibleRows();
        int count = q == null ? 0 : q.entries().size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, count - rows)));
        g.enableScissor(l, top, l + PANEL_W, listBottom());
        for (int row = 0; row < rows && scroll + row < count; row++) {
            int i = scroll + row;
            Messages.QueueEntry e = q.entries().get(i);
            int y = top + row * ROW_H;
            boolean current = i == q.index() && q.state() != Messages.Queue.STOPPED;
            boolean hover = mouseX >= l && mouseX < l + PANEL_W && mouseY >= y && mouseY < y + ROW_H
                    && mouseY < listBottom();
            if (current) {
                g.fill(l + 2, y, l + PANEL_W - 2, y + ROW_H, ROW_CURRENT);
            } else if (hover) {
                g.fill(l + 2, y, l + PANEL_W - 2, y + ROW_H, HOVER);
            }
            String dur = e.durationMs() < 0 ? "LIVE" : PlayerBarModel.time(e.durationMs());
            int durW = font.width(dur);
            String label = (current ? "> " : "") + (i + 1) + ". " + e.title();
            g.drawString(font, font.plainSubstrByWidth(label, PANEL_W - PAD * 2 - durW - 6), l + PAD, y + 4,
                    current ? TEXT : 0xFFD0D0D0, false);
            g.drawString(font, dur, l + PANEL_W - PAD - durW, y + 4, SUB_TEXT, false);
        }
        g.disableScissor();
        if (count > rows) { // スクロールの目印
            int barH = Math.max(8, (listBottom() - top) * rows / count);
            int barY = top + (listBottom() - top - barH) * scroll / Math.max(1, count - rows);
            g.fill(l + PANEL_W - 3, barY, l + PANEL_W - 1, barY + barH, 0xFF707070);
        }
    }

    private static String currentTitle(Messages.Queue q, NowPlaying np) {
        if (q != null && q.index() >= 0 && q.index() < q.entries().size() && q.state() != Messages.Queue.STOPPED) {
            return q.entries().get(q.index()).title();
        }
        return np != null ? np.title() : "";
    }

    private void drawProgress(GuiGraphics g, Font font, NowPlaying np, int x, int y, int w) {
        g.fill(x, y + 10, x + w, y + 12, 0xFF404040);
        if (np == null) {
            return;
        }
        double p = PlayerBarModel.progress(np);
        if (p >= 0) {
            g.fill(x, y + 10, x + (int) (w * p), y + 12, ACCENT);
        }
        String time = np.durationMs() > 0 && np.positionMs() >= 0 ? PlayerBarModel.time(np.positionMs())
                : np.durationMs() < 0 ? "LIVE" : "";
        g.drawString(font, time, x, y, SUB_TEXT, false);
        if (np.durationMs() > 0) {
            String total = PlayerBarModel.time(np.durationMs());
            g.drawString(font, total, x + w - font.width(total), y, SUB_TEXT, false);
        }
    }

    // ---- 入力 ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        if (inside(closeButton(), mx, my)) {
            onClose();
            return true;
        }
        byte[] actions = {Messages.Control.PREVIOUS, Messages.Control.PAUSE, Messages.Control.SKIP,
                Messages.Control.LOOP, Messages.Control.STOP};
        for (int i = 0; i < actions.length; i++) {
            if (inside(button(i), mx, my)) {
                control(actions[i], 0);
                return true;
            }
        }
        Messages.Queue q = QueueStore.get(slot);
        if (q != null && mx >= left() && mx < left() + PANEL_W && my >= listTop() && my < listBottom()) {
            int i = scroll + (int) ((my - listTop()) / ROW_H);
            if (i >= 0 && i < q.entries().size()) {
                control(Messages.Control.JUMP, i);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta) * 2;
        return true;
    }
}
