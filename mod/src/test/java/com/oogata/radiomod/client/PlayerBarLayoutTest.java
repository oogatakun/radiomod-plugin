package com.oogata.radiomod.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerBarLayoutTest {

    /** ホットバー(中央の 182px)の矩形と、バーが重ならないこと。 */
    private static boolean overlapsHotbar(PlayerBarLayout l, int w, int h) {
        int hbLeft = w / 2 - 91;
        int hbRight = w / 2 + 91;
        int hbTop = h - 22;
        boolean xOverlap = l.x() < hbRight && l.x() + l.width() > hbLeft;
        boolean yOverlap = l.y() + l.height() > hbTop;
        return xOverlap && yOverlap;
    }

    @Test
    void 画面の幅が十分なら_右下の端に置く() {
        PlayerBarLayout l = PlayerBarLayout.of(960, 540);
        assertEquals(PlayerBarLayout.MAX_WIDTH, l.width());
        assertEquals(960 - 8, l.x() + l.width());
        assertEquals(540 - PlayerBarLayout.HEIGHT - 6, l.y());
        assertFalse(overlapsHotbar(l, 960, 540));
    }

    @Test
    void 幅が足りないときは_ホットバーを避けて幅を縮める() {
        // GUI の幅 427 (854px / 2): ホットバーの右端 = 213 + 91 + 30 = 334。右の余りは 427 - 8 - 334 = 85 < 150 なので持ち上げる
        PlayerBarLayout narrow = PlayerBarLayout.of(427, 240);
        assertFalse(overlapsHotbar(narrow, 427, 240));
        // 余りが 150 以上あれば、下のまま幅を縮める: 幅 560 → 右端 280+91+30=401、右の余り 560-8-401=151
        PlayerBarLayout shrunk = PlayerBarLayout.of(560, 300);
        assertEquals(151, shrunk.width());
        assertEquals(300 - PlayerBarLayout.HEIGHT - 6, shrunk.y());
        assertFalse(overlapsHotbar(shrunk, 560, 300));
    }

    @Test
    void 持ち上げたバーは_ホットバーにも体力バーにも重ならない() {
        PlayerBarLayout l = PlayerBarLayout.of(427, 240);
        assertTrue(l.y() + l.height() <= 240 - 52);
        assertTrue(l.x() >= 0);
        assertTrue(l.x() + l.width() <= 427);
    }

    @Test
    void 当たり判定() {
        PlayerBarLayout l = PlayerBarLayout.of(960, 540);
        assertTrue(l.contains(l.x() + 1, l.y() + 1));
        assertFalse(l.contains(l.x() - 1, l.y() + 1));
        assertFalse(l.contains(l.x() + l.width(), l.y()));
    }
}
