package com.oogata.radiomod.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class PcmTest {

    @Test
    void ステレオは左右の平均のモノラルになる() {
        ByteBuffer stereo = ByteBuffer.allocateDirect(16).order(ByteOrder.LITTLE_ENDIAN);
        short[][] frames = {{1000, 3000}, {-2000, 2000}, {32767, 32767}, {-32768, -32768}};
        for (short[] f : frames) {
            stereo.putShort(f[0]).putShort(f[1]);
        }
        stereo.flip();
        ByteBuffer out = Pcm.downmixToMono(stereo, ByteBuffer.allocateDirect(8));
        assertEquals(8, out.remaining());
        assertEquals(2000, out.getShort());
        assertEquals(0, out.getShort());
        assertEquals(32767, out.getShort());
        assertEquals(-32768, out.getShort());
    }

    @Test
    void 片側だけの音も半分の大きさで残る() {
        ByteBuffer stereo = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN);
        stereo.putShort((short) 10000).putShort((short) 0).flip();
        assertEquals(5000, Pcm.downmixToMono(stereo, ByteBuffer.allocateDirect(2)).getShort());
    }
}
