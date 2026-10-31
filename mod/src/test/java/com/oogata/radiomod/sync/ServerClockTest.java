package com.oogata.radiomod.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ServerClockTest {

    private static final long MS = 1_000_000L;

    @Test
    void 時計合わせの前は未同期() {
        ServerClock clock = new ServerClock(() -> 0);
        assertFalse(clock.isSynced());
        assertEquals(ServerClock.UNSYNCED, clock.nowServerMs());
    }

    @Test
    void 往復の真ん中をサーバー時刻とみなして推定する() {
        AtomicLong nano = new AtomicLong();
        ServerClock clock = new ServerClock(nano::get);
        // 自分の時計 1000ms に送り、1040ms に返事。往復40ms なので、1020ms の時点がサーバー時刻 5_000_020
        clock.onResponse(1000 * MS, 1040 * MS, 5_000_020L);
        nano.set(2000 * MS);
        assertTrue(clock.isSynced());
        assertEquals(5_001_000L, clock.nowServerMs());
        assertEquals(40.0, clock.lastRttMs(), 1e-6);
    }

    @Test
    void 往復が最短の結果を採用する() {
        AtomicLong nano = new AtomicLong();
        ServerClock clock = new ServerClock(nano::get);
        clock.onResponse(0, 10 * MS, 1005L); // 往復10ms, offset=1000
        // 往復が長い結果は、offset がずれていても採用されない
        clock.onResponse(1000 * MS, 1100 * MS, 2350L); // 往復100ms, offset=1300
        nano.set(0);
        assertEquals(1000L, clock.nowServerMs());
        assertEquals(10.0, clock.bestRttMs(), 1e-6);
    }

    @Test
    void 採用値の変化は1回あたり5ms以内() {
        ServerClock clock = new ServerClock(() -> 0);
        clock.onResponse(0, 2 * MS, 1001L); // offset=1000
        double prev = clock.offsetMs();
        for (int i = 0; i < 30; i++) {
            clock.onResponse(0, 2 * MS, 1101L); // offset=1100 の結果が続く
            double now = clock.offsetMs();
            assertTrue(now - prev <= 5.0 + 1e-9, "step=" + (now - prev));
            prev = now;
        }
        assertEquals(1100.0, prev, 1e-6); // 最終的には追いつく
    }

    @Test
    void 負の往復時間は捨てる() {
        ServerClock clock = new ServerClock(() -> 0);
        clock.onResponse(100, 50, 1000L);
        assertFalse(clock.isSynced());
    }

    @Test
    void リセットすると未同期に戻る() {
        ServerClock clock = new ServerClock(() -> 0);
        clock.onResponse(0, 2 * MS, 1001L);
        clock.reset();
        assertFalse(clock.isSynced());
        assertEquals(-1, clock.lastRttMs(), 0);
    }
}
