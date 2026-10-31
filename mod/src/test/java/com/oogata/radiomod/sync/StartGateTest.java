package com.oogata.radiomod.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StartGateTest {

    @Test
    void 鳴らすべき位置が負のあいだは待つ() {
        StartGate g = new StartGate();
        assertEquals(StartGate.Action.WAIT, g.decide(-1, 0, true).action());
        assertEquals(StartGate.Action.WAIT, g.decide(-500, 0, false).action());
    }

    @Test
    void 位置が合っていれば鳴らす() {
        StartGate g = new StartGate();
        assertEquals(StartGate.Action.PLAY, g.decide(0, 0, true).action());
        assertEquals(StartGate.Action.PLAY, g.decide(60, 0, true).action());
    }

    @Test
    void 準備の位置が先に進んでいるあいだは待つ() {
        StartGate g = new StartGate();
        assertEquals(StartGate.Action.WAIT, g.decide(100, 400, true).action());
        assertEquals(StartGate.Action.PLAY, g.decide(400, 400, true).action());
    }

    @Test
    void 遅れていたら少し先から読み直し_続けて遅れたら先を大きく狙う() {
        StartGate g = new StartGate();
        StartGate.Decision d1 = g.decide(1000, 0, true);
        assertEquals(StartGate.Action.SEEK, d1.action());
        assertEquals(1000 + StartGate.INITIAL_LEAD_MS, d1.seekToMs());
        StartGate.Decision d2 = g.decide(2000, 1400, true);
        assertEquals(2000 + StartGate.INITIAL_LEAD_MS * 2, d2.seekToMs());
    }

    @Test
    void 大きく先へ進みすぎていたら読み直す() {
        StartGate g = new StartGate();
        assertEquals(StartGate.Action.SEEK, g.decide(0, StartGate.EARLY_MAX_MS + 1, true).action());
    }

    @Test
    void 位置を合わせ直せない音源は遅れていても鳴らす() {
        StartGate g = new StartGate();
        assertEquals(StartGate.Action.PLAY, g.decide(5000, 0, false).action());
    }

    @Test
    void 曲の途中から読み始めるときは_準備の間に進む分だけ先を読む() {
        assertEquals(0, StartGate.initialStart(-200));
        assertEquals(0, StartGate.initialStart(0));
        assertEquals(10_000 + StartGate.INITIAL_LEAD_MS, StartGate.initialStart(10_000));
    }
}
