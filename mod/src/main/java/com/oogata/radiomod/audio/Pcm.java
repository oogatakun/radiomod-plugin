package com.oogata.radiomod.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** PCM の変換。 */
final class Pcm {

    private Pcm() {
    }

    /**
     * ステレオ16ビット（リトルエンディアン、L R L R …）を、左右の平均のモノラル16ビットにまとめる。
     * 3D サウンドは、ステレオのままだと位置が音に反映されないので、ラジオはモノラルで鳴らす。
     * stereo は position〜limit を読み、out は先頭から書いて flip 済みで返す（out は十分な大きさにすること）。
     */
    static ByteBuffer downmixToMono(ByteBuffer stereo, ByteBuffer out) {
        out.clear();
        out.order(ByteOrder.LITTLE_ENDIAN);
        int base = stereo.position();
        int frames = stereo.remaining() / 4;
        for (int i = 0; i < frames; i++) {
            int l = stereo.order(ByteOrder.LITTLE_ENDIAN).getShort(base + i * 4);
            int r = stereo.getShort(base + i * 4 + 2);
            out.putShort((short) ((l + r) / 2));
        }
        out.flip();
        return out;
    }
}
