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
        p.start();
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
        p.start();
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
        p.start();
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
        p.start();
        p.addProcessed(4800); // 100ms 分を鳴らし終えた
        assertEquals(1505, p.positionMs(0));
        assertEquals(1505 + 20, p.positionMs(960)); // いまのバッファの中で 20ms 進んだ
    }

    @Test
    void 鳴らし始める前は_キューに積んだぶんも音源の古い値も数えない() {
        PlayedPosition p = new PlayedPosition(48000);
        p.reset(1413);
        // キューに 800ms 分（8個）積んで合わせ直しをしたあと、音源側が「処理済み」「位置あり」と返しても
        for (int i = 0; i < 8; i++) {
            p.addProcessed(4800);
        }
        assertEquals(1413, p.positionMs(38400));
        assertEquals(0, p.processedFrames());
        // 鳴らし始めても、再生済みは 0 から数える
        p.start();
        assertEquals(1413, p.positionMs(0));
        p.addProcessed(4800);
        assertEquals(1513, p.positionMs(0));
    }

    @Test
    void 曲を切り替えたら_前の曲の再生済みの数は残らない() {
        PlayedPosition p = new PlayedPosition(48000);
        p.reset(0); // 曲 A
        p.start();
        for (int i = 0; i < 170; i++) {
            p.addProcessed(4800); // 17 秒分
        }
        assertEquals(17000, p.positionMs(0));
        p.reset(405); // 曲 B（skip や新しい PLAY）
        assertEquals(405, p.positionMs(0));
        assertEquals(405, p.positionMs(48000 * 5)); // 音源が古い位置を返していても、鳴らし始めるまでは基準のまま
        assertEquals(0, p.processedFrames());
        assertFalse(p.isStarted());
    }
}
