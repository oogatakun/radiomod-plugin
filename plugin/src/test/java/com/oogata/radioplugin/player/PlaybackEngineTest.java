package com.oogata.radioplugin.player;

import static com.oogata.radioplugin.player.Fakes.live;
import static com.oogata.radioplugin.player.Fakes.song;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.oogata.radioplugin.player.Fakes.FakeClock;
import com.oogata.radioplugin.player.Fakes.RecordingOutput;
import com.oogata.radioplugin.player.PlaybackEngine.ErrorOutcome;
import com.oogata.radioplugin.player.PlaybackEngine.Loop;
import com.oogata.radioplugin.player.PlaybackEngine.Result;
import com.oogata.radioplugin.player.PlaybackEngine.State;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlaybackEngineTest {

    private static final long LEAD = 500;
    private static final long SYNC = 30_000;

    private FakeClock clock;
    private RecordingOutput out;
    private PlaybackEngine engine;

    @BeforeEach
    void setUp() {
        clock = new FakeClock();
        out = new RecordingOutput();
        AtomicLong ids = new AtomicLong();
        engine = new PlaybackEngine(clock, new PlaybackEngine.Settings(LEAD, SYNC, 3), ids::incrementAndGet, out);
    }

    @Test
    void 新しく始める曲の基準時刻は_いまにstartLeadを足した値() {
        long now = clock.nowMs();
        engine.playSingle(song("a", 60_000), false);
        assertEquals(now + LEAD, engine.originMs());
        assertEquals(List.of("play:a"), out.events);
        assertEquals(State.PLAYING, engine.state());
    }

    @Test
    void 再生位置は基準時刻との差で決まり_開始待ちのあいだは負() {
        engine.playSingle(song("a", 60_000), false);
        assertEquals(-LEAD, engine.positionMs());
        clock.advance(10_500);
        assertEquals(10_000, engine.positionMs());
    }

    @Test
    void 一時停止は位置を持ち_再開で新しい基準時刻を作る() {
        engine.playSingle(song("a", 60_000), false);
        clock.advance(10_500); // 位置 10000
        engine.pause();
        assertEquals(State.PAUSED, engine.state());
        assertEquals(10_000, engine.positionMs());
        clock.advance(5_000); // 止まっている間は進まない
        assertEquals(10_000, engine.positionMs());

        engine.pause();
        assertEquals(State.PLAYING, engine.state());
        assertEquals(clock.nowMs() + LEAD - 10_000, engine.originMs());
        assertEquals(List.of("play:a", "pause:10000", "resume"), out.events);
        clock.advance(LEAD);
        assertEquals(10_000, engine.positionMs());
    }

    @Test
    void 停止中のpauseは何もしない() {
        assertEquals(Result.NOT_PLAYING, engine.pause());
        assertEquals(List.of(), out.events);
    }

    @Test
    void stopはキューを空にしloopも解除する() {
        engine.playSingle(song("a", 60_000), true);
        engine.stop();
        assertEquals(State.STOPPED, engine.state());
        assertEquals(List.of(), engine.queue());
        assertEquals(Loop.NONE, engine.loop());
        assertEquals("stop", out.events.get(out.events.size() - 1));
    }

    @Test
    void playは単曲loopをつけられ_ライブでは無効() {
        engine.playSingle(song("a", 1000), true);
        assertEquals(Loop.SINGLE, engine.loop());
        engine.playSingle(live("radio"), true);
        assertEquals(Loop.NONE, engine.loop());
    }

    @Test
    void 曲が終わると次の曲へ進み_基準時刻を新しく作る() {
        engine.playPlaylist(List.of(song("a", 10_000), song("b", 10_000)), false);
        clock.advance(LEAD + 10_000);
        engine.tick();
        assertEquals(1, engine.index());
        assertEquals(clock.nowMs() + LEAD, engine.originMs());
        assertEquals(List.of("play:a", "play:b"), out.events);
    }

    @Test
    void 曲の終わりの前では進まない() {
        engine.playPlaylist(List.of(song("a", 10_000), song("b", 10_000)), false);
        clock.advance(LEAD + 9_999);
        engine.tick();
        assertEquals(0, engine.index());
    }

    @Test
    void 最後の曲が終わるとloopなしは停止しキューは残る() {
        engine.playPlaylist(List.of(song("a", 10_000), song("b", 10_000)), false);
        clock.advance(LEAD + 10_000);
        engine.tick();
        clock.advance(LEAD + 10_000);
        engine.tick();
        assertEquals(State.STOPPED, engine.state());
        assertEquals(2, engine.queue().size());
        assertEquals("ended", out.events.get(out.events.size() - 1));
        assertEquals("stop", out.events.get(out.events.size() - 2));
    }

    @Test
    void プレイリスト全体loopは先頭へ戻る() {
        engine.playPlaylist(List.of(song("a", 10_000), song("b", 10_000)), true);
        clock.advance(LEAD + 10_000);
        engine.tick();
        clock.advance(LEAD + 10_000);
        engine.tick();
        assertEquals(0, engine.index());
        assertEquals(State.PLAYING, engine.state());
        assertEquals(List.of("play:a", "play:b", "play:a"), out.events);
    }

    @Test
    void 単曲loopは同じ曲を繰り返す() {
        engine.playSingle(song("a", 10_000), true);
        clock.advance(LEAD + 10_000);
        engine.tick();
        assertEquals(List.of("play:a", "play:a"), out.events);
        assertEquals(State.PLAYING, engine.state());
    }

    @Test
    void skipは次の曲へ_最後でloopなしなら停止() {
        engine.playPlaylist(List.of(song("a", 10_000), song("b", 10_000)), false);
        engine.skip();
        assertEquals(1, engine.index());
        engine.skip();
        assertEquals(State.STOPPED, engine.state());
        assertEquals(2, engine.queue().size());
    }

    @Test
    void skipは最後でプレイリストloopなら先頭へ() {
        engine.playPlaylist(List.of(song("a", 10_000), song("b", 10_000)), true);
        engine.skip();
        engine.skip();
        assertEquals(0, engine.index());
        assertEquals(State.PLAYING, engine.state());
    }

    @Test
    void previousは1つ前の曲を最初から_先頭ならやり直し() {
        engine.playPlaylist(List.of(song("a", 10_000), song("b", 10_000)), false);
        engine.skip();
        clock.advance(5_000);
        engine.previous();
        assertEquals(0, engine.index());
        assertEquals(clock.nowMs() + LEAD, engine.originMs());
        engine.previous();
        assertEquals(0, engine.index());
        assertEquals(List.of("play:a", "play:b", "play:a", "play:a"), out.events);
    }

    @Test
    void ライブではskipとpreviousが無効() {
        engine.playPlaylist(List.of(live("radio"), song("a", 10_000)), false);
        assertEquals(Result.LIVE, engine.skip());
        assertEquals(Result.LIVE, engine.previous());
        assertEquals(0, engine.index());
    }

    @Test
    void ライブは曲の終わりで進まない() {
        engine.playPlaylist(List.of(live("radio"), song("a", 10_000)), false);
        clock.advance(LEAD + 100_000_000);
        engine.tick();
        assertEquals(0, engine.index());
    }

    @Test
    void addはキューの後ろに足し_停止中なら足した曲から始める() {
        engine.add(List.of(song("a", 10_000)));
        assertEquals(State.PLAYING, engine.state());
        engine.add(List.of(song("b", 10_000), song("c", 10_000)));
        assertEquals(3, engine.queue().size());
        assertEquals(0, engine.index());
        assertEquals(List.of("play:a"), out.events);

        engine.stop();
        engine.add(List.of(song("d", 10_000)));
        assertEquals("play:d", out.events.get(out.events.size() - 1));
    }

    @Test
    void 最後の曲で止まったあとのaddは_足した曲から始める() {
        engine.playSingle(song("a", 10_000), false);
        clock.advance(LEAD + 10_000);
        engine.tick();
        assertEquals(State.STOPPED, engine.state());
        engine.add(List.of(song("b", 10_000)));
        assertEquals(1, engine.index());
        assertEquals(State.PLAYING, engine.state());
    }

    @Test
    void playは現在のトラックを最初から_空なら何もしない() {
        assertEquals(Result.EMPTY, engine.playCurrent());
        engine.playPlaylist(List.of(song("a", 10_000), song("b", 10_000)), false);
        engine.skip();
        clock.advance(3_000);
        assertEquals(Result.OK, engine.playCurrent());
        assertEquals(1, engine.index());
        assertEquals(-LEAD, engine.positionMs());
    }

    @Test
    void SYNCは設定の間隔で基準時刻を再送する() {
        engine.playSingle(song("a", 600_000), false);
        clock.advance(SYNC - 1);
        engine.tick();
        assertEquals(List.of("play:a"), out.events);
        clock.advance(1);
        engine.tick();
        assertEquals(List.of("play:a", "sync"), out.events);
        assertEquals(engine.originMs(), out.origins.get(out.origins.size() - 1));
        clock.advance(SYNC);
        engine.tick();
        assertEquals(List.of("play:a", "sync", "sync"), out.events);
    }

    @Test
    void 一時停止中はSYNCを送らない() {
        engine.playSingle(song("a", 600_000), false);
        engine.pause();
        clock.advance(SYNC * 3);
        engine.tick();
        assertEquals(List.of("play:a", "pause:0"), out.events);
    }

    @Test
    void ERRORの曲は飛ばして次へ進む() {
        engine.playPlaylist(List.of(song("a", 10_000), song("b", 10_000)), false);
        assertEquals(ErrorOutcome.SKIPPED, engine.onError(engine.trackId()));
        assertEquals(1, engine.index());
        assertEquals(List.of("play:a", "play:b"), out.events);
    }

    @Test
    void 古いtrackIdのERRORは無視する() {
        engine.playPlaylist(List.of(song("a", 10_000), song("b", 10_000)), false);
        long old = engine.trackId();
        engine.skip();
        assertEquals(ErrorOutcome.IGNORED, engine.onError(old));
        assertEquals(1, engine.index());
    }

    @Test
    void 同じ曲のERRORが重なっても1回しか数えない() {
        engine.playPlaylist(List.of(song("a", 1), song("b", 1), song("c", 1)), false);
        long id = engine.trackId();
        assertEquals(ErrorOutcome.SKIPPED, engine.onError(id));
        assertEquals(ErrorOutcome.IGNORED, engine.onError(id));
        assertEquals(1, engine.index());
    }

    @Test
    void 連続3回失敗したら止める() {
        engine.playPlaylist(List.of(song("a", 1), song("b", 1), song("c", 1), song("d", 1)), false);
        assertEquals(ErrorOutcome.SKIPPED, engine.onError(engine.trackId()));
        assertEquals(ErrorOutcome.SKIPPED, engine.onError(engine.trackId()));
        assertEquals(ErrorOutcome.STOPPED_BY_LIMIT, engine.onError(engine.trackId()));
        assertEquals(State.STOPPED, engine.state());
    }

    @Test
    void 途中で成功した曲があれば_失敗の連続は数え直す() {
        engine.playPlaylist(List.of(song("a", 10_000), song("b", 10_000), song("c", 10_000),
                song("d", 10_000), song("e", 10_000)), false);
        engine.onError(engine.trackId());
        engine.onError(engine.trackId());
        clock.advance(LEAD + 10_000); // c が最後まで鳴った
        engine.tick();
        assertEquals(ErrorOutcome.SKIPPED, engine.onError(engine.trackId()));
        assertEquals(4, engine.index());
    }

    @Test
    void 最後の曲のERRORで止まる() {
        engine.playSingle(song("a", 1), false);
        assertEquals(ErrorOutcome.STOPPED_AT_END, engine.onError(engine.trackId()));
        assertEquals(State.STOPPED, engine.state());
    }

    @Test
    void restoreは一時停止の状態も写し_PLAYのあとにPAUSEを出す() {
        engine.playSingle(song("a", 60_000), false);
        clock.advance(10_500);
        engine.pause();
        PlaybackEngine.Snapshot snapshot = engine.snapshot();
        assertEquals(10_000, snapshot.positionMs());

        RecordingOutput out2 = new RecordingOutput();
        PlaybackEngine copy = new PlaybackEngine(clock, new PlaybackEngine.Settings(LEAD, SYNC, 3),
                new AtomicLong(100)::incrementAndGet, out2);
        copy.restore(snapshot);
        assertEquals(State.PAUSED, copy.state());
        assertEquals(10_000, copy.positionMs());
        assertEquals(List.of("play:a", "pause:10000"), out2.events);
    }

    @Test
    void restoreは再生中なら位置を引き継いで始める() {
        engine.playSingle(song("a", 60_000), false);
        clock.advance(10_500);
        PlaybackEngine.Snapshot snapshot = engine.snapshot();

        RecordingOutput out2 = new RecordingOutput();
        PlaybackEngine copy = new PlaybackEngine(clock, new PlaybackEngine.Settings(LEAD, SYNC, 3),
                new AtomicLong(100)::incrementAndGet, out2);
        copy.restore(snapshot);
        assertEquals(State.PLAYING, copy.state());
        assertEquals(clock.nowMs() + LEAD - 10_000, copy.originMs());
    }
}
