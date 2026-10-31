package com.oogata.radiomod.client;

import com.oogata.radiomod.client.PlayerBarModel.Action;
import com.oogata.radiomod.net.Messages;
import com.oogata.radiomod.sync.ClientSync;
import com.oogata.radiomod.sync.SlotRouter.NowPlaying;
import com.oogata.radiomod.net.RadioNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** 画面の右下に出す、小さな音楽プレーヤーのバー。描画と、ボタンの当たり判定。 */
public final class PlayerBar {

    private static final int BUTTON_W = 18;
    /** 2段目(時間とボタン)の、バーの上端からの位置と高さ。 */
    private static final int ROW2_TOP = 16;
    private static final int ROW2_H = 18;
    /** 右から: 閉じる、キューを見る、次へ、再生/一時停止、前へ。 */
    private static final int BUTTONS = 5;
    private static final int HOVER = 0x60FFFFFF;

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

        // 1段目: タイトル(長ければ流す)。2段目: 時間と、ボタン
        String title = (n.slot().slotType() == Messages.Slot.RADIO ? "[ラジオ] " : "") + n.title();
        drawMarquee(g, font, title, l.x() + 8, l.x() + l.width() - 8, l.y() + 5);

        // ボタンは左から: 前へ、再生/一時停止、次へ、キュー、閉じる
        String[] labels = {"|<", n.paused() ? ">" : "||", ">|", "^", "x"};
        int rowTop = l.y() + ROW2_TOP;
        for (int i = 0; i < BUTTONS; i++) {
            int bx = buttonX(l, BUTTONS - 1 - i);
            boolean hover = mouseX >= bx && mouseX < bx + BUTTON_W && mouseY >= rowTop && mouseY < rowTop + ROW2_H;
            if (hover) {
                g.fill(bx, rowTop, bx + BUTTON_W, rowTop + ROW2_H, HOVER);
            }
            g.drawCenteredString(font, labels[i], bx + BUTTON_W / 2, rowTop + 5, 0xFFFFFFFF);
        }
        String time = n.durationMs() > 0 && n.positionMs() >= 0
                ? PlayerBarModel.time(n.positionMs()) + " / " + PlayerBarModel.time(n.durationMs())
                : n.durationMs() < 0 ? "LIVE" : "";
        g.drawString(font, time, l.x() + 8, rowTop + 5, 0xFFA0A0A0, false);

        // 進行バー
        g.fill(l.x(), l.y() + l.height() - 2, l.x() + l.width(), l.y() + l.height(), 0xFF404040);
        double p = PlayerBarModel.progress(n);
        if (p >= 0) {
            g.fill(l.x(), l.y() + l.height() - 2, l.x() + (int) (l.width() * p), l.y() + l.height(), 0xFFE8A0B8);
        }
    }

    private static void drawMarquee(GuiGraphics g, Font font, String text, int left, int right, int y) {
        Marquee.draw(g, font, text, left, right, y, 0xFFFFFFFF, shownSinceNanos);
    }

    /** クリックされたら、対応する操作をする。バーの上でクリックされたら true。 */
    public static boolean click(int screenWidth, int screenHeight, double mouseX, double mouseY) {
        NowPlaying n = current;
        PlayerBarLayout l = PlayerBarLayout.of(screenWidth, screenHeight);
        if (n == null || !l.contains(mouseX, mouseY)) {
            return false;
        }
        if (mouseY < l.y() + ROW2_TOP || mouseY >= l.y() + ROW2_TOP + ROW2_H) {
            return true; // タイトルの段や進行バーのクリックは、何もしない(バーの上なので、ゲームには渡さない)
        }
        Action[] actions = {Action.CLOSE, Action.LIST, Action.NEXT, Action.TOGGLE, Action.PREVIOUS}; // 右から
        for (int i = 0; i < BUTTONS; i++) {
            int bx = buttonX(l, i);
            if (mouseX >= bx && mouseX < bx + BUTTON_W) {
                if (actions[i] == Action.CLOSE) {
                    MODEL.dismiss(n.trackId()); // バーを隠して、曲も止める(止められるかはサーバーが権限で決める)
                }
                if (actions[i] == Action.LIST) {
                    PlayerScreen.open(n.slot());
                } else {
                    send(n, actions[i]);
                }
                break;
            }
        }
        return true;
    }

    private static void send(NowPlaying n, Action action) {
        Byte control = PlayerBarModel.controlAction(action);
        if (control != null) {
            RadioNet.send(new Messages.Control(n.slot(), control, 0));
        }
    }
}
