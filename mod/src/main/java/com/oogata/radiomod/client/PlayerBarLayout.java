package com.oogata.radiomod.client;

/** バーを置く場所の計算。Minecraft には依存しない。右下に置き、ホットバーに重ならないようにする。 */
public record PlayerBarLayout(int x, int y, int width, int height) {

    public static final int HEIGHT = 24;
    public static final int MAX_WIDTH = 230;
    /** これより狭くしか置けないときは、ホットバーの上に上げる。 */
    public static final int MIN_WIDTH = 150;
    static final int MARGIN = 8;
    /** ホットバーの半分の幅(182 / 2)。 */
    static final int HOTBAR_HALF = 91;
    /** ホットバーの右の余白(攻撃のインジケーターや、左利きのときのオフハンドの枠が出る)。 */
    static final int HOTBAR_RIGHT_GAP = 30;
    static final int BOTTOM_MARGIN = 6;
    /** ホットバーの上に上げるときの、画面の下からの距離(体力・満腹度のバーの上)。 */
    static final int RAISED_BOTTOM_MARGIN = 54;

    public static PlayerBarLayout of(int screenWidth, int screenHeight) {
        int right = screenWidth - MARGIN;
        int hotbarRight = screenWidth / 2 + HOTBAR_HALF + HOTBAR_RIGHT_GAP;
        int available = right - hotbarRight;
        if (available >= MIN_WIDTH) {
            int width = Math.min(MAX_WIDTH, available);
            return new PlayerBarLayout(right - width, screenHeight - HEIGHT - BOTTOM_MARGIN, width, HEIGHT);
        }
        int width = Math.max(60, Math.min(MAX_WIDTH, screenWidth - MARGIN * 2));
        return new PlayerBarLayout(right - width, screenHeight - HEIGHT - RAISED_BOTTOM_MARGIN, width, HEIGHT);
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
