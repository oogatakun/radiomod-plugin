package com.oogata.radiomod.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StartGateTest {

    @Test
    void 鳴らすべき位置が負のあいだは待つ() {
        StartGate g = new StartGate();
        assertEquals(StartGate.Action.WAIT, g.decide(-1, 0, true, 0).action());
        assertEquals(StartGate.Action.WAIT, g.decide(-500, 0, false, 10).action());
    }

    @Test
    void 位置が合っていれば鳴らす() {
        StartGate g = new StartGate();
        assertEquals(StartGate.Action.PLAY, g.decide(0, 0, true, 0).action());
        assertEquals(StartGate.Action.PLAY, g.decide(60, 0, true, 10).action());
    }

    @Test
    void 準備の位置が先に進んでいるあいだは待つ() {
        StartGate g = new StartGate();
        assertEquals(StartGate.Action.WAIT, g.decide(100, 400, true, 0).action());
        assertEquals(StartGate.Action.PLAY, g.decide(400, 400, true, 300).action());
    }

    @Test
    void 大きく先へ進みすぎていたら読み直す() {
        StartGate g = new StartGate();
        assertEquals(StartGate.Action.SEEK, g.decide(0, StartGate.EARLY_MAX_MS + 1, true, 0).action());
    }

    @Test
    void 位置を合わせ直せない音源は遅れていても鳴らす() {
        StartGate g = new StartGate();
        assertEquals(StartGate.Action.PLAY, g.decide(5000, 0, false, 0).action());
    }

    @Test
    void 曲の途中から読み始めるときは_準備の間に進む分だけ先を読む() {
        assertEquals(0, StartGate.initialStart(-200));
        assertEquals(0, StartGate.initialStart(0));
        assertEquals(10_000 + StartGate.INITIAL_LEAD_MS, StartGate.initialStart(10_000));
    }

    @Test
    void 読み直しの最小の間隔より短い連続の呼び出しでは読み直さない() {
        StartGate g = new StartGate();
        assertEquals(StartGate.Action.SEEK, g.decide(10_000, 0, true, 1000).action());
        for (long t = 1010; t < 1000 + StartGate.MIN_SEEK_INTERVAL_MS; t += 10) {
            assertEquals(StartGate.Action.WAIT, g.decide(10_000 + (t - 1000), 0, true, t).action());
        }
    }

    @Test
    void 読み直しの回数に上限があり_超えたら遅れたまま鳴らす() {
        StartGate g = new StartGate();
        long now = 0;
        int seeks = 0;
        // 準備の位置がいつまでも遅れたままの場合
        for (int i = 0; i < 10; i++) {
            now += 1000;
            StartGate.Decision d = g.decide(50_000 + now, 0, true, now);
            if (d.action() == StartGate.Action.SEEK) {
                seeks++;
            } else {
                assertEquals(StartGate.Action.PLAY, d.action());
                break;
            }
        }
        assertEquals(StartGate.MAX_SEEKS, seeks);
        assertEquals(StartGate.MAX_SEEKS, g.seeks());
    }

    /**
     * 読み直しと準備に seekTakesMs かかるとして、PLAY になるまでを模擬する。
     * 時刻は10msずつ進め、鳴らすべき位置も同じだけ進む。
     */
    private static long simulate(long seekTakesMs, int[] seeksOut) {
        StartGate g = new StartGate();
        long now = 0;
        long desiredAtZero = 52_000; // 曲の途中に入る
        long ready = StartGate.initialStart(desiredAtZero);
        long readyAt = seekTakesMs; // 最初の取得にも同じだけかかる
        int seeks = 0;
        for (; now < 60_000; now += 10) {
            if (now < readyAt) {
                continue;
            }
            StartGate.Decision d = g.decide(desiredAtZero + now, ready, true, now);
            if (d.action() == StartGate.Action.PLAY) {
                seeksOut[0] = seeks;
                return now;
            }
            if (d.action() == StartGate.Action.SEEK) {
                seeks++;
                ready = d.seekToMs();
                readyAt = now + seekTakesMs;
            }
        }
        seeksOut[0] = seeks;
        return -1;
    }

    @Test
    void 読み直しに1500msかかっても_数回で鳴り始める() {
        int[] seeks = new int[1];
        long at = simulate(1500, seeks);
        assertTrue(at >= 0, "鳴り始めなかった");
        assertTrue(seeks[0] <= StartGate.MAX_SEEKS, "seeks=" + seeks[0]);
        assertTrue(at < 10_000, "at=" + at);
    }

    @Test
    void 読み直しが速くても遅くても鳴り始める() {
        for (long takes : new long[] {50, 300, 800, 1500, 3000, 6000}) {
            int[] seeks = new int[1];
            long at = simulate(takes, seeks);
            assertTrue(at >= 0, "takes=" + takes + " 鳴り始めなかった");
            assertTrue(seeks[0] <= StartGate.MAX_SEEKS, "takes=" + takes + " seeks=" + seeks[0]);
        }
    }

    @Test
    void 読み直しの上限のあとに大きく先へ進んでいても_待ち続けずに鳴らす() {
        StartGate g = new StartGate();
        long now = 0;
        // 読み直しても狙った位置に着かず、いつも準備の位置が 5 秒先になる音源
        int seeks = 0;
        StartGate.Action last = null;
        for (int i = 0; i < 20 && last != StartGate.Action.PLAY; i++) {
            now += 600;
            StartGate.Decision d = g.decide(100 + now, 100 + now + 5000, true, now);
            last = d.action();
            if (last == StartGate.Action.SEEK) {
                seeks++;
            }
        }
        assertEquals(StartGate.Action.PLAY, last);
        assertEquals(StartGate.MAX_SEEKS, seeks);
    }

    @Test
    void 上限のあとでも_少し先へ進んでいるだけなら追いつくまで待つ() {
        StartGate g = new StartGate();
        long now = 0;
        for (int i = 0; i < StartGate.MAX_SEEKS; i++) {
            now += 600;
            assertEquals(StartGate.Action.SEEK, g.decide(1000 + now, 0, true, now).action());
        }
        assertEquals(StartGate.Action.WAIT, g.decide(1000 + now, 1000 + now + 500, true, now + 600).action());
        assertEquals(StartGate.Action.PLAY, g.decide(1000 + now, 1000 + now, true, now + 600).action());
    }

    /** 1回目は間に合って始まり、停止のあとの2回目は3回続けて遅れる。どちらも最後は PLAY になること。 */
    @Test
    void 停止のあとの再生で_3回続けて合わせ直しになっても鳴り始める() {
        StartGate g = new StartGate();
        // 1回目: 1回の読み直しで合う
        assertEquals(StartGate.Action.SEEK, g.decide(500, 0, true, 1000).action());
        assertEquals(StartGate.Action.PLAY, g.decide(1000, 1000, true, 1500).action());
        // 停止 → 再生
        g.reset();
        long now = 20_000;
        int seeks = 0;
        StartGate.Action action = null;
        for (int i = 0; i < 10 && action != StartGate.Action.PLAY; i++) {
            now += 500;
            // 準備の位置はいつも鳴らすべき位置より 3 秒先（YouTube で読み直しが着かない場合を想定）
            StartGate.Decision d = g.decide(now - 20_000, now - 20_000 + 3000, true, now);
            action = d.action();
            if (action == StartGate.Action.SEEK) {
                seeks++;
            }
        }
        assertEquals(StartGate.MAX_SEEKS, seeks);
        assertEquals(StartGate.Action.PLAY, action);
        assertEquals(StartGate.MAX_SEEKS, g.seeks());
    }

    /** 鳴らすべき位置 1175ms、キューの先頭が 1405ms なら、追いつくまで待ってから鳴らし、補正は始まらない。 */
    @Test
    void 準備の位置が少し先なら_追いつくまで待ってから鳴らし_補正が上限に張り付かない() {
        StartGate g = new StartGate();
        DriftCorrector drift = new DriftCorrector();
        long ready = 1405;
        long now = 5000;
        long desired = 1175;
        StartGate.Decision d = g.decide(desired, ready, true, now);
        while (d.action() == StartGate.Action.WAIT) {
            now += 10;
            desired += 10;
            d = g.decide(desired, ready, true, now);
        }
        assertEquals(StartGate.Action.PLAY, d.action());
        assertTrue(Math.abs(desired - ready) <= 10, "鳴らし始めのずれ=" + (desired - ready));
        assertEquals(0, drift.update(desired - ready));
        assertEquals(0, g.seeks());
    }

    @Test
    void 鳴らし始めのずれが大きければ_補正に頼らず先に位置を合わせる() {
        StartGate g = new StartGate();
        assertEquals(StartGate.Action.SEEK, g.decide(1175, 800, true, 0).action()); // 375ms 遅れ
    }
}
