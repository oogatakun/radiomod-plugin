package com.oogata.radioplugin.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MessageLogTest {

    @Test
    void PLAYはプレイヤー名_スロット_trackId_url_originMs_開始までの残りを含む() {
        Messages.Play play = new Messages.Play(Messages.Slot.PERSONAL_SLOT, 7L, "https://example.com/a.mp3", "曲",
                60_000L, 10_500L, null);
        String line = MessageLog.format(true, "alice", play, 10_000L);
        assertTrue(line.contains("alice"));
        assertTrue(line.contains("PLAY slot=個人 trackId=7 url=https://example.com/a.mp3"));
        assertTrue(line.contains("originMs=10500 (開始まで 500ms)"));
        assertTrue(line.endsWith("now=10000"));
    }

    @Test
    void PAUSEはpositionMs_SYNCはoriginMsを出す() {
        assertTrue(MessageLog.format(true, "a", new Messages.Pause(Messages.Slot.SHARED_SLOT, 8500), 0)
                .contains("PAUSE slot=共有 positionMs=8500"));
        assertTrue(MessageLog.format(true, "a", new Messages.Sync(Messages.Slot.SHARED_SLOT, 42), 40)
                .contains("SYNC slot=共有 originMs=42 (開始まで 2ms)"));
    }

    @Test
    void TIMEとTIME_REQだけが量の多いメッセージ() {
        assertTrue(MessageLog.isNoisy(new Messages.Time(1, 2)));
        assertTrue(MessageLog.isNoisy(new Messages.TimeReq(1)));
        assertFalse(MessageLog.isNoisy(new Messages.Hello(2, "x")));
        assertEquals("受信 ← a HELLO protocol=2 mod=x now=5",
                MessageLog.format(false, "a", new Messages.Hello(2, "x"), 5));
    }
}
