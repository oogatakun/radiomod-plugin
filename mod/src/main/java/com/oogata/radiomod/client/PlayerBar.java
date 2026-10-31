package com.oogata.radiomod.client;

import com.oogata.radiomod.client.PlayerBarModel.Action;
import com.oogata.radiomod.net.Messages;
import com.oogata.radiomod.sync.ClientSync;
import com.oogata.radiomod.sync.SlotRouter.NowPlaying;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** 画面の右下に出す、小さな音楽プレーヤーのバー。描画と、ボタンの当たり判定。 */
public final class PlayerBar {

    private static final int BUTTON_W = 18;
    /** 右から: 閉じる、キューを見る、次へ、再生/一時停止、前へ。 */
    private static final int BUTTONS = 5;
    private static final int HOVER = 0x60FFFFFF;
    /** 長いタイトルを流す速さ(px/秒)と、一周したときの空白(px)。 */
    private static final double SCROLL_SPEED = 28;
    private static final int SCROLL_GAP = 28;

    private static final PlayerBarModel MODEL = new PlayerBarModel();
    private static NowPlaying current;
    private static long shownTrackId = -1;
    private static long shownSinceNanos;

    private PlayerBar() {
    }

    /** 今出す曲を選び直す。毎フレーム呼んでよい。 */
    public static boolean update() {
        current = MODEL.choose(ClientSync.router().nowPlaying()).orElse(null);
        long id = current == null ? -1 : current.trackId();
        if (id != shownTrackId) {
            shownTrackId = id;
            shownSinceNanos = System.nanoTime(); // 曲が変わったら、タイトルを頭から流し直す
        }
        return current != null;
    }

    public static boolean visible() {
        return current != null;
    }

    /** ボタン(右から数えて index 番目)の左端の x。 */
    private static int buttonX(PlayerBarLayout l, int index) {
        return l.x() + l.width() - BUTTON_W * (index + 1) - 2;
    }

    /** mouseX が -1 のときは、カーソルなしで描く(普段の表示)。 */
    public static void render(GuiGraphics g, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        NowPlaying n = current;
        if (n == null) {
            return;
        }
        PlayerBarLayout l = PlayerBarLayout.of(screenWidth, screenHeight);
        Font font = Minecraft.getInstance().font;
        g.fill(l.x(), l.y(), l.x() + l.width(), l.y() + l.height(), 0xD0181818);

        boolean canControl = PlayerBarModel.command(n.slot(), Action.TOGGLE) != null;
        // 左から: 前へ、再生/一時停止、次へ、キュー、閉じる
        String[] labels = {"|<", n.paused() ? ">" : "||", ">|", "^", "x"};
        for (int i = 0; i < BUTTONS; i++) {
            int bx = buttonX(l, BUTTONS - 1 - i);
            boolean enabled = canControl;
            boolean hover = mouseX >= bx && mouseX < bx + BUTTON_W && mouseY >= l.y() && mouseY < l.y() + l.height() - 3;
            if (hover && enabled) {
                g.fill(bx, l.y() + 2, bx + BUTTON_W, l.y() + l.height() - 5, HOVER);
            }
            g.drawCenteredString(font, labels[i], bx + BUTTON_W / 2, l.y() + 7, enabled ? 0xFFFFFFFF : 0xFF707070);
        }

        int textLeft = l.x() + 8;
        int textRight = buttonX(l, BUTTONS - 1) - 4;
        String title = (n.slot().slotType() == Messages.Slot.RADIO ? "[ラジオ] " : "") + n.title();
        drawMarquee(g, font, title, textLeft, textRight, l.y() + 4);
        String time = n.durationMs() > 0 && n.positionMs() >= 0
                ? PlayerBarModel.time(n.positionMs()) + " / " + PlayerBarModel.time(n.durationMs())
                : n.durationMs() < 0 ? "LIVE" : "";
        g.drawString(font, time, textLeft, l.y() + 14, 0xFFA0A0A0, false);

        // 進行バー
        g.fill(l.x(), l.y() + l.height() - 2, l.x() + l.width(), l.y() + l.height(), 0xFF404040);
        double p = PlayerBarModel.progress(n);
        if (p >= 0) {
            g.fill(l.x(), l.y() + l.height() - 2, l.x() + (int) (l.width() * p), l.y() + l.height(), 0xFFE8A0B8);
        }
    }

    /** 入りきるタイトルはそのまま。入りきらないときは、右から左へ流す(一周したら、空白をあけて頭から)。 */
    private static void drawMarquee(GuiGraphics g, Font font, String text, int left, int right, int y) {
        int width = font.width(text);
        int available = right - left;
        if (width <= available) {
            g.drawString(font, text, left, y, 0xFFFFFFFF, false);
            return;
        }
        double seconds = (System.nanoTime() - shownSinceNanos) / 1e9;
        int period = width + SCROLL_GAP;
        // 最初の 1.5 秒は止めて読ませる
        double moving = Math.max(0, seconds - 1.5);
        int offset = (int) (moving * SCROLL_SPEED) % period;
        g.enableScissor(left, y - 1, right, y + 10);
        g.drawString(font, text, left - offset, y, 0xFFFFFFFF, false);
        g.drawString(font, text, left - offset + period, y, 0xFFFFFFFF, false);
        g.disableScissor();
    }

    /** クリックされたら、対応する操作をする。バーの上でクリックされたら true。 */
    public static boolean click(int screenWidth, int screenHeight, double mouseX, double mouseY) {
        NowPlaying n = current;
        PlayerBarLayout l = PlayerBarLayout.of(screenWidth, screenHeight);
        if (n == null || !l.contains(mouseX, mouseY)) {
            return false;
        }
        Action[] actions = {Action.CLOSE, Action.LIST, Action.NEXT, Action.TOGGLE, Action.PREVIOUS}; // 右から
        for (int i = 0; i < BUTTONS; i++) {
            int bx = buttonX(l, i);
            if (mouseX >= bx && mouseX < bx + BUTTON_W) {
                if (actions[i] == Action.CLOSE) {
                    MODEL.dismiss(n.trackId()); // バーを隠す。曲を止められるもの(個人・共有)は、止めるコマンドも送る
                }
                send(n, actions[i]);
                break;
            }
        }
        return true;
    }

    private static void send(NowPlaying n, Action action) {
        String command = PlayerBarModel.command(n.slot(), action);
        var player = Minecraft.getInstance().player;
        if (command != null && player != null) {
            player.connection.sendCommand(command);
        }
    }
}
