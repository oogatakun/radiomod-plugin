package com.oogata.radioplugin.player;

import static com.oogata.radioplugin.player.Fakes.song;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oogata.radioplugin.player.Fakes.FakeClock;
import com.oogata.radioplugin.player.Fakes.RecordingOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class EngineTraceTest {

    @Test
    void 同じ曲をstopしてからもう一度再生すると_trackIdも基準時刻も新しくなり_前の位置を引き継がない() {
        FakeClock clock = new FakeClock();
        RecordingOutput out = new RecordingOutput();
        AtomicLong ids = new AtomicLong();
        PlaybackEngine engine = new PlaybackEngine(clock, PlaybackEngine.Settings.defaults(), ids::incrementAndGet, out);
        List<String> lines = new ArrayList<>();
        engine.setTrace("個人:alice", lines::add);

        engine.playSingle(song("a", 60_000), false);
        long id1 = engine.trackId();
        long origin1 = engine.originMs();
        clock.advance(20_000);
        engine.stop();
        clock.advance(3_000);
        engine.playSingle(song("a", 60_000), false);

        assertNotEquals(id1, engine.trackId());
        assertEquals(clock.nowMs() + 500, engine.originMs()); // 曲の頭(0ms)から。前回の位置は残らない
        assertNotEquals(origin1, engine.originMs());
        assertEquals(-500, engine.positionMs());
        assertEquals(3, lines.size()); // startCurrent, stop, startCurrent
        assertTrue(lines.get(0).contains("エンジン[個人:alice] startCurrent"));
        assertTrue(lines.get(1).contains("stop: state=STOPPED"));
    }

    @Test
    void pauseとresumeも状態を1行で出す() {
        FakeClock clock = new FakeClock();
        PlaybackEngine engine = new PlaybackEngine(clock, PlaybackEngine.Settings.defaults(),
                new AtomicLong()::incrementAndGet, new RecordingOutput());
        List<String> lines = new ArrayList<>();
        engine.setTrace("共有", lines::add);
        engine.playSingle(song("a", 60_000), false);
        clock.advance(5_500);
        engine.pause();
        engine.pause();
        assertTrue(lines.get(1).contains("pause: state=PAUSED") && lines.get(1).contains("pausedPositionMs=5000"));
        assertTrue(lines.get(2).contains("resume: state=PLAYING"));
    }
}
