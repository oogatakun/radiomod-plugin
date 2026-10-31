package com.oogata.radiomod.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayedPositionTest {

    @Test
    void startsAtRequestedPositionUntilFirstFrameArrives() {
        PlayedPosition p = new PlayedPosition(48000);
        p.reset(5000);
        assertFalse(p.isBased());
        assertEquals(5000, p.positionMs(0));
    }

    @Test
    void countsProcessedBuffersAndOffsetFromFirstFrameTimecode() {
        PlayedPosition p = new PlayedPosition(48000);
        p.reset(5000);
        p.setBase(5020); // 実際に着地した位置
        assertTrue(p.isBased());
        p.addProcessed(4800); // 100ms
        p.addProcessed(4800); // 100ms
        assertEquals(5020 + 200, p.positionMs(0));
        assertEquals(5020 + 200 + 50, p.positionMs(2400)); // 次のバッファの途中（50ms）
    }

    @Test
    void oneSecondOfSamplesIsOneSecond() {
        PlayedPosition p = new PlayedPosition(48000);
        p.reset(0);
        p.setBase(0);
        for (int i = 0; i < 10; i++) {
            p.addProcessed(4800);
        }
        assertEquals(1000, p.positionMs(0));
        assertEquals(48000, p.processedFrames());
    }

    @Test
    void resetClearsCounts() {
        PlayedPosition p = new PlayedPosition(48000);
        p.reset(0);
        p.setBase(0);
        p.addProcessed(48000);
        p.reset(30000);
        assertFalse(p.isBased());
        assertEquals(30000, p.positionMs(0));
    }

    @Test
    void negativeOffsetIsTreatedAsZero() {
        PlayedPosition p = new PlayedPosition(44100);
        p.reset(100);
        assertEquals(100, p.positionMs(-5));
    }

    @Test
    void 基準は要求した位置で_キューの先頭の位置から数える() {
        PlayedPosition p = new PlayedPosition(48000);
        p.reset(1405); // 1405ms から取得を始めた
        p.markBased(); // 最初のフレームが届いても、基準は動かさない
        assertEquals(1405, p.positionMs(0)); // キュー（8個・約800ms分）を積んだだけでは進まない
        p.addProcessed(4800); // 100ms 分を鳴らし終えた
        assertEquals(1505, p.positionMs(0));
        assertEquals(1505 + 20, p.positionMs(960)); // いまのバッファの中で 20ms 進んだ
    }
}
