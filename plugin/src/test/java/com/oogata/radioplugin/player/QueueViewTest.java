package com.oogata.radioplugin.player;

import static com.oogata.radioplugin.player.Fakes.live;
import static com.oogata.radioplugin.player.Fakes.song;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.oogata.radioplugin.net.Messages;
import com.oogata.radioplugin.player.Fakes.FakeClock;
import com.oogata.radioplugin.player.Fakes.RecordingOutput;
import com.oogata.radioplugin.player.PlaybackEngine.Loop;
import com.oogata.radioplugin.player.PlaybackEngine.State;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueueViewTest {

    private static final Messages.Slot SLOT = Messages.Slot.PERSONAL_SLOT;

    private FakeClock clock;
    private RecordingOutput out;
    private PlaybackEngine engine;

    @BeforeEach
    void setUp() {
        clock = new FakeClock();
        out = new RecordingOutput();
        AtomicLong ids = new AtomicLong();
        engine = new PlaybackEngine(clock, new PlaybackEngine.Settings(500, 30_000, 3), ids::incrementAndGet, out);
    }

    private static Messages.Control control(byte action, int arg) {
        return new Messages.Control(SLOT, action, arg);
    }

    @Test
    void 空のキューは_停止と曲なしで送る() {
        Messages.Queue q = QueueView.of(SLOT, "", engine);
        assertEquals(Messages.Queue.STOPPED, q.state());
        assertEquals(-1, q.index());
        assertEquals(List.of(), q.entries());
    }

    @Test
    void キューの中身と何曲目か_loop_状態を送る() {
        engine.playPlaylist(List.of(song("a", 1000), song("b", 2000), live("c")), true);
        engine.skip();
        Messages.Queue q = QueueView.of(Messages.Slot.radio(java.util.UUID.randomUUID()), "ラジオ1", engine);
        assertEquals("ラジオ1", q.label());
        assertEquals(Messages.Queue.PLAYING, q.state());
        assertEquals(Messages.Queue.LOOP_PLAYLIST, q.loop());
        assertEquals(1, q.index());
        assertEquals(List.of(new Messages.QueueEntry("a", 1000), new Messages.QueueEntry("b", 2000),
                new Messages.QueueEntry("c", -1)), q.entries());
        engine.pause();
        assertEquals(Messages.Queue.PAUSED, QueueView.of(SLOT, "", engine).state());
    }

    @Test
    void 画面の操作がエンジンに届く() {
        engine.playPlaylist(List.of(song("a", 1000), song("b", 2000), song("c", 3000)), false);
        QueueView.apply(engine, control(Messages.Control.SKIP, 0));
        assertEquals(1, engine.index());
        QueueView.apply(engine, control(Messages.Control.PREVIOUS, 0));
        assertEquals(0, engine.index());
        QueueView.apply(engine, control(Messages.Control.JUMP, 2));
        assertEquals(2, engine.index());
        assertEquals(State.PLAYING, engine.state());
        QueueView.apply(engine, control(Messages.Control.PAUSE, 0));
        assertEquals(State.PAUSED, engine.state());
        QueueView.apply(engine, control(Messages.Control.REFRESH, 0)); // 何も変えない
        assertEquals(State.PAUSED, engine.state());
        QueueView.apply(engine, control(Messages.Control.STOP, 0));
        assertEquals(State.STOPPED, engine.state());
        assertEquals(List.of(), engine.queue());
    }

    @Test
    void 範囲外の曲番号へのJUMPは何もしない() {
        engine.playPlaylist(List.of(song("a", 1000), song("b", 2000)), false);
        QueueView.apply(engine, control(Messages.Control.JUMP, 5));
        QueueView.apply(engine, control(Messages.Control.JUMP, -1));
        assertEquals(0, engine.index());
    }

    @Test
    void loopは_なし_単曲_全体_の順に切り替わり_ライブでは単曲を飛ばす() {
        engine.playSingle(song("a", 1000), false);
        assertEquals(Loop.SINGLE, engine.cycleLoop());
        assertEquals(Loop.PLAYLIST, engine.cycleLoop());
        assertEquals(Loop.NONE, engine.cycleLoop());
        engine.playSingle(live("radio"), false);
        assertEquals(Loop.PLAYLIST, engine.cycleLoop());
    }

    @Test
    void ライブの再生中は曲を選べない() {
        engine.playPlaylist(List.of(live("l"), song("b", 2000)), false);
        assertEquals(PlaybackEngine.Result.LIVE, engine.jump(1));
        assertEquals(0, engine.index());
    }

    @Test
    void SEEKは新しい基準時刻で同じ曲を送り直し_ライブには効かない() {
        engine.playSingle(song("a", 600_000), false);
        clock.advance(10_000);
        QueueView.apply(engine, control(Messages.Control.SEEK, 120_000));
        // 動かし先から鳴るよう、基準時刻 = いま + lead - 動かし先
        assertEquals(clock.nowMs() + 500 - 120_000, engine.originMs());
        assertEquals(120_000 - 500, engine.positionMs());
        assertEquals(List.of("play:a", "play:a"), out.events);
        // 曲の長さを超える位置は、終わりの1秒前まで
        QueueView.apply(engine, control(Messages.Control.SEEK, 9_999_999));
        assertEquals(599_000 - 500, engine.positionMs());
        engine.playSingle(live("radio"), false);
        assertEquals(PlaybackEngine.Result.LIVE, engine.seek(5_000));
    }

    @Test
    void 一時停止中のSEEKは_動かし先で止めたままにする() {
        engine.playSingle(song("a", 600_000), false);
        clock.advance(10_000);
        engine.pause();
        out.events.clear();
        engine.seek(200_000);
        assertEquals(State.PAUSED, engine.state());
        assertEquals(200_000, engine.positionMs());
        assertEquals(List.of("play:a", "pause:200000"), out.events);
    }

    @Test
    void 停止中のSEEKは何もしない() {
        assertEquals(PlaybackEngine.Result.NOT_PLAYING, engine.seek(1000));
        assertEquals(List.of(), out.events);
    }

    @Test
    void キューの並べ替えは_鳴っている曲を同じ曲のまま追いかける() {
        engine.playPlaylist(List.of(song("a", 1000), song("b", 2000), song("c", 3000), song("d", 4000)), false);
        engine.skip(); // b を再生中(index 1)
        // 鳴っている曲を動かす: b を末尾へ
        QueueView.apply(engine, control(Messages.Control.MOVE, Messages.Control.packMove(1, 3)));
        assertEquals(List.of("a", "c", "d", "b"), titles());
        assertEquals(3, engine.index());
        // 鳴っている曲より後ろの曲を、前へ飛び越させる: c(1) を先頭(0)へ → b は index 3 のまま
        QueueView.apply(engine, control(Messages.Control.MOVE, Messages.Control.packMove(1, 0)));
        assertEquals(List.of("c", "a", "d", "b"), titles());
        assertEquals(3, engine.index());
        // 前にあった曲を、鳴っている曲の後ろへ: c(0) を最後(3)へ → b は index 2 に繰り上がる
        QueueView.apply(engine, control(Messages.Control.MOVE, Messages.Control.packMove(0, 3)));
        assertEquals(List.of("a", "d", "b", "c"), titles());
        assertEquals(2, engine.index());
        assertEquals("b", engine.currentTrack().title());
        // 後ろの曲を、鳴っている曲の前へ: c(3) を 0 へ → b は index 3 に繰り下がる
        QueueView.apply(engine, control(Messages.Control.MOVE, Messages.Control.packMove(3, 0)));
        assertEquals(List.of("c", "a", "d", "b"), titles());
        assertEquals("b", engine.currentTrack().title());
        assertEquals(State.PLAYING, engine.state());
    }

    @Test
    void 範囲外や同じ場所への並べ替えは何もしない() {
        engine.playPlaylist(List.of(song("a", 1000), song("b", 2000)), false);
        QueueView.apply(engine, control(Messages.Control.MOVE, Messages.Control.packMove(0, 0)));
        QueueView.apply(engine, control(Messages.Control.MOVE, Messages.Control.packMove(0, 5)));
        QueueView.apply(engine, control(Messages.Control.MOVE, Messages.Control.packMove(7, 1)));
        assertEquals(List.of("a", "b"), titles());
    }

    private List<String> titles() {
        return engine.queue().stream().map(Track::title).toList();
    }
}
