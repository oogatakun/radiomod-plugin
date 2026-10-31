package com.oogata.radiomod.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DriftCorrectorTest {

    @Test
    void 差が40ms未満なら補正しない() {
        DriftCorrector c = new DriftCorrector();
        assertEquals(0, c.update(39));
        assertEquals(0, c.update(-39));
        assertFalse(c.isCorrecting());
    }

    @Test
    void 遅れていれば速く_進んでいれば遅くする() {
        DriftCorrector c = new DriftCorrector();
        assertTrue(c.update(60) > 0);
        c.reset();
        assertTrue(c.update(-60) < 0);
    }

    @Test
    void 補正の幅は最大2パーセント() {
        DriftCorrector c = new DriftCorrector();
        assertEquals(0.02, c.update(5000), 1e-12);
        assertEquals(-0.02, c.update(-5000), 1e-12);
    }

    @Test
    void 一度始めたら10ms未満になるまで続ける() {
        DriftCorrector c = new DriftCorrector();
        c.update(45);
        assertTrue(c.isCorrecting());
        assertTrue(c.update(25) > 0); // 40ms を下回っても続ける
        assertTrue(c.update(10) > 0); // 10ms ちょうどはまだ続ける
        assertEquals(0, c.update(9.9));
        assertFalse(c.isCorrecting());
        assertEquals(0, c.update(30)); // 40ms 未満では再開しない
    }
}
