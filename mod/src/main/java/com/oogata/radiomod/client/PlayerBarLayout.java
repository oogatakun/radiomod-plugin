package com.oogata.radiomod.client;

/**
 * バーを置く場所の計算。Minecraft には依存しない。
 * ホットバーのすぐ右隣に、下の端をそろえて置く。画面の右が狭いときは、幅を縮める(縮めても足りなければ、ホットバーに少し重なる)。
 */
public record PlayerBarLayout(int x, int y, int width, int height) {

    public static final int HEIGHT = 48;
    public static final int MAX_WIDTH = 230;
    /** これより狭くはしない。右が足りなければ、ホットバーに重ねる。 */
    public static final int MIN_WIDTH = 110;
    /** ホットバーの半分の幅(182 / 2)。 */
    static final int HOTBAR_HALF = 91;
    /** ホットバーとバーの間のすき間。 */
    static final int GAP = 4;
    /** 画面の右端に残す余白。 */
    static final int RIGHT_MARGIN = 2;

    public static PlayerBarLayout of(int screenWidth, int screenHeight) {
        int x = screenWidth / 2 + HOTBAR_HALF + GAP;
        int width = Math.min(MAX_WIDTH, screenWidth - RIGHT_MARGIN - x);
        if (width < MIN_WIDTH) {
            width = Math.min(MIN_WIDTH, screenWidth - RIGHT_MARGIN * 2);
            x = screenWidth - RIGHT_MARGIN - width;
        }
        return new PlayerBarLayout(x, screenHeight - HEIGHT, width, HEIGHT);
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
