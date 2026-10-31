package com.oogata.radioplugin.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CodecTest {

    private static final UUID RADIO_ID = new UUID(0x0123456789ABCDEFL, 0xFEDCBA9876543210L);
    private static final Messages.Slot RADIO = Messages.Slot.radio(RADIO_ID);

    private static final List<Messages> ALL = List.of(
            new Messages.Play(Messages.Slot.PERSONAL_SLOT, 1L, "https://example.com/a.mp3", "曲名", 180_000L,
                    1_700_000_000_500L, null),
            new Messages.Play(RADIO, 2L, "https://example.com/live", "LIVE", -1L, 42L,
                    new Messages.RadioTarget("minecraft:overworld", 1.5, 64.0, -3.25, 24f)),
            new Messages.Pause(Messages.Slot.SHARED_SLOT, 12_345L),
            new Messages.Resume(RADIO, 99L),
            new Messages.Stop(Messages.Slot.PERSONAL_SLOT),
            new Messages.Sync(Messages.Slot.SHARED_SLOT, 7L),
            new Messages.Move(RADIO, "minecraft:the_nether", 1, 2, 3),
            new Messages.Time(123456789L, 1_700_000_000_000L),
            new Messages.Hello(Messages.PROTOCOL_VERSION, "1.0.0"),
            new Messages.Error(RADIO, 5L, Messages.Error.DECODE_FAILED, "失敗"),
            new Messages.TimeReq(-5L),
            new Messages.Queue(RADIO, "ラジオ1", Messages.Queue.PLAYING, Messages.Queue.LOOP_PLAYLIST, 1,
                    List.of(new Messages.QueueEntry("曲A", 180_000L), new Messages.QueueEntry("LIVE", -1L))),
            new Messages.Queue(Messages.Slot.PERSONAL_SLOT, "", Messages.Queue.STOPPED, Messages.Queue.LOOP_NONE, -1,
                    List.of()),
            new Messages.Open(Messages.Slot.SHARED_SLOT),
            new Messages.Control(RADIO, Messages.Control.JUMP, 7),
            new Messages.Control(Messages.Slot.PERSONAL_SLOT, Messages.Control.REFRESH, 0));

    @Test
    void すべてのメッセージが往復で同じ値になる() throws Exception {
        for (Messages m : ALL) {
            assertEquals(m, Codec.decode(Codec.encode(m)), m.toString());
        }
    }

    @Test
    void バイト列は仕様どおり() {
        // HELLO: 0x80 / int 2 / 文字列 "1.0"
        assertArrayEquals(new byte[] {(byte) 0x80, 0, 0, 0, 2, 0, 3, '1', '.', '0'},
                Codec.encode(new Messages.Hello(2, "1.0")));
        // STOP 個人スロット: 0x04 / 0x00
        assertArrayEquals(new byte[] {0x04, 0x00}, Codec.encode(new Messages.Stop(Messages.Slot.PERSONAL_SLOT)));
        // TIME_REQ: 0x82 / long 1（big-endian）
        assertArrayEquals(new byte[] {(byte) 0x82, 0, 0, 0, 0, 0, 0, 0, 1}, Codec.encode(new Messages.TimeReq(1)));
        // ラジオスロットは UUID の上位・下位64ビットが続く
        byte[] stop = Codec.encode(new Messages.Stop(RADIO));
        assertEquals(1 + 1 + 16, stop.length);
        assertEquals(0x01, stop[2]);
        assertEquals((byte) 0xFE, stop[10]);
    }

    @Test
    void titleは上限で切り詰め_文字の途中では切らない() throws Exception {
        String title = "あ".repeat(100); // 300バイト
        Messages.Play p = new Messages.Play(Messages.Slot.PERSONAL_SLOT, 1, "https://e/x", title, 1, 1, null);
        Messages.Play back = (Messages.Play) Codec.decode(Codec.encode(p));
        assertTrue(back.title().getBytes(StandardCharsets.UTF_8).length <= Codec.MAX_TEXT_BYTES);
        assertEquals("あ".repeat(85), back.title()); // 255バイト
    }

    @Test
    void urlが上限を超えたら送らない() {
        String url = "https://e/" + "a".repeat(Codec.MAX_URL_BYTES);
        Messages.Play p = new Messages.Play(Messages.Slot.PERSONAL_SLOT, 1, url, "t", 1, 1, null);
        assertThrows(IllegalArgumentException.class, () -> Codec.encode(p));
    }

    @Test
    void 未知の種別と長さ不足は例外で知らせる() {
        assertThrows(Codec.DecodeException.class, () -> Codec.decode(new byte[0]));
        assertThrows(Codec.DecodeException.class, () -> Codec.decode(new byte[] {0x7F}));
        assertThrows(Codec.DecodeException.class, () -> Codec.decode(new byte[] {(byte) 0x82, 0, 0}));
        assertThrows(Codec.DecodeException.class, () -> Codec.decode(new byte[] {0x04, 0x09}));
    }
}
