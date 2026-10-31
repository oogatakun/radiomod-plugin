package com.oogata.radiomod.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerBarLayoutTest {

    @Test
    void ホットバーのすぐ右隣に_下をそろえて置く() {
        PlayerBarLayout l = PlayerBarLayout.of(960, 540);
        assertEquals(960 / 2 + 91 + 4, l.x());
        assertEquals(540 - PlayerBarLayout.HEIGHT, l.y());
        assertEquals(PlayerBarLayout.MAX_WIDTH, l.width());
        assertTrue(l.x() + l.width() <= 960);
    }

    @Test
    void 右が狭いときは幅を縮める() {
        // 幅 427: 右隣の x = 213 + 91 + 4 = 308、残りは 427 - 2 - 308 = 117
        PlayerBarLayout l = PlayerBarLayout.of(427, 240);
        assertEquals(308, l.x());
        assertEquals(117, l.width());
        assertEquals(427 - 2, l.x() + l.width());
    }

    @Test
    void それでも足りないときは_最小の幅でホットバーに少し重ねる() {
        PlayerBarLayout l = PlayerBarLayout.of(380, 240); // 右隣の x = 285、残り 93 < 110
        assertEquals(PlayerBarLayout.MIN_WIDTH, l.width());
        assertEquals(380 - 2, l.x() + l.width());
        assertTrue(l.x() < 380 / 2 + 91 + 4);
    }

    @Test
    void 当たり判定() {
        PlayerBarLayout l = PlayerBarLayout.of(960, 540);
        assertTrue(l.contains(l.x() + 1, l.y() + 1));
        assertFalse(l.contains(l.x() - 1, l.y() + 1));
        assertFalse(l.contains(l.x() + l.width(), l.y()));
    }
}
