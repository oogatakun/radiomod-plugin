package com.oogata.radiomod.client;

import com.oogata.radiomod.client.PlayerBarModel.Action;
import com.oogata.radiomod.sync.ClientSync;
import com.oogata.radiomod.sync.SlotRouter.NowPlaying;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** 画面の左下に出す、小さな音楽プレーヤーのバー。描画と、ボタンの当たり判定。 */
public final class PlayerBar {

    static final int WIDTH = 230;
    static final int HEIGHT = 24;
    static final int MARGIN_X = 8;
    static final int MARGIN_BOTTOM = 6;
    private static final int BUTTON_W = 18;

    private static final PlayerBarModel MODEL = new PlayerBarModel();
    private static NowPlaying current;

    private PlayerBar() {
    }

    /** 今出す曲を選び直す。毎フレーム呼んでよい。 */
    public static boolean update() {
        current = MODEL.choose(ClientSync.router().nowPlaying()).orElse(null);
        return current != null;
    }

    public static boolean visible() {
        return current != null;
    }

    private static int left() {
        return MARGIN_X;
    }

    private static int top(int screenHeight) {
        return screenHeight - HEIGHT - MARGIN_BOTTOM;
    }

    /** ボタン(右から: 閉じる、次へ、再生/一時停止、前へ)の左端の x。 */
    private static int buttonX(int index) {
        return left() + WIDTH - BUTTON_W * (index + 1) - 2;
    }

    /** mouseX が -1 のときは、カーソルなしで描く(普段の表示)。 */
    public static void render(GuiGraphics g, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        NowPlaying n = current;
        if (n == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int x = left();
        int y = top(screenHeight);
        g.fill(x, y, x + WIDTH, y + HEIGHT, 0xD0181818);

        boolean canControl = PlayerBarModel.command(n.slot(), Action.TOGGLE) != null;
        String[] labels = {"x", ">|", n.paused() ? ">" : "||", "|<"};
        for (int i = 0; i < labels.length; i++) {
            int bx = buttonX(i);
            boolean enabled = i == 0 || canControl;
            boolean hover = mouseX >= bx && mouseX < bx + BUTTON_W && mouseY >= y && mouseY < y + HEIGHT - 3;
            if (hover && enabled) {
                g.fill(bx, y + 2, bx + BUTTON_W, y + HEIGHT - 5, 0x60FFFFFF);
            }
            g.drawCenteredString(font, labels[i], bx + BUTTON_W / 2, y + 7, enabled ? 0xFFFFFFFF : 0xFF707070);
        }

        int textRight = buttonX(3) - 4;
        String title = (n.slot().slotType() == com.oogata.radiomod.net.Messages.Slot.RADIO ? "[ラジオ] " : "") + n.title();
        g.drawString(font, font.plainSubstrByWidth(title, textRight - (x + 8)), x + 8, y + 4, 0xFFFFFFFF, false);
        String time = n.durationMs() > 0 && n.positionMs() >= 0
                ? PlayerBarModel.time(n.positionMs()) + " / " + PlayerBarModel.time(n.durationMs())
                : n.durationMs() < 0 ? "LIVE" : "";
        g.drawString(font, time, x + 8, y + 14, 0xFFA0A0A0, false);

        // 進行バー
        g.fill(x, y + HEIGHT - 2, x + WIDTH, y + HEIGHT, 0xFF404040);
        double p = PlayerBarModel.progress(n);
        if (p >= 0) {
            g.fill(x, y + HEIGHT - 2, x + (int) (WIDTH * p), y + HEIGHT, 0xFFE8A0B8);
        }
    }

    /** クリックされたら、対応する操作をする。バーの上でクリックされたら true。 */
    public static boolean click(int screenHeight, double mouseX, double mouseY) {
        NowPlaying n = current;
        int y = top(screenHeight);
        if (n == null || mouseX < left() || mouseX >= left() + WIDTH || mouseY < y || mouseY >= y + HEIGHT) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            int bx = buttonX(i);
            if (mouseX >= bx && mouseX < bx + BUTTON_W) {
                switch (i) {
                    case 0 -> MODEL.dismiss(n.trackId());
                    case 1 -> send(n, Action.NEXT);
                    case 2 -> send(n, Action.TOGGLE);
                    default -> send(n, Action.PREVIOUS);
                }
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
