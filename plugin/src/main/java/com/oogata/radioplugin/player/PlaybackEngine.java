package com.oogata.radioplugin.player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * 再生状態（キュー・何曲目か・位置・loop・状態）を持つ再生エンジン。
 * 個人・共有・ラジオのどれでも同じものを使うので、プレイヤーや Bukkit には依存しない。
 * 位置は「基準時刻（曲の頭に当たるサーバー時刻）」で持つ。スレッドセーフではない（メインスレッドから使う）。
 */
public final class PlaybackEngine {

    public enum State { STOPPED, PLAYING, PAUSED }

    public enum Loop { NONE, SINGLE, PLAYLIST }

    public enum Result {
        OK,
        /** 再生できる曲がない */
        EMPTY,
        /** 再生中でも一時停止中でもない */
        NOT_PLAYING,
        /** ライブ配信には使えない操作 */
        LIVE
    }

    public enum ErrorOutcome {
        /** 現在の曲の ERROR ではない（古い・重複）ので無視した */
        IGNORED,
        /** 失敗した曲を飛ばして次へ進んだ */
        SKIPPED,
        /** 連続失敗の上限に達して止めた */
        STOPPED_BY_LIMIT,
        /** 失敗した曲が最後で、次がなく止めた */
        STOPPED_AT_END
    }

    /** 状態の変化を外へ伝える。実際の送信先（mod・チャット）は呼び出し側が決める。 */
    public interface Output {
        void play(Track track, long trackId, long originMs);

        void pause(long positionMs);

        void resume(long originMs);

        void stop();

        void sync(long originMs);

        /** 曲が新しく始まった（タイトルの通知用）。 */
        default void trackStarted(Track track) {
        }

        /** 最後の曲が終わって、自然に止まった。 */
        default void ended() {
        }
    }

    public record Settings(long startLeadMs, long syncIntervalMs, int maxConsecutiveErrors) {
        public static Settings defaults() {
            return new Settings(500, 30_000, 3);
        }
    }

    /** 共有へのコピーなどに使う、ある時点の状態。 */
    public record Snapshot(List<Track> queue, int index, Loop loop, State state, long positionMs) {
    }

    private final Clock clock;
    private final Settings settings;
    private final LongSupplier trackIds;
    private final Output output;

    private final List<Track> queue = new ArrayList<>();
    private int index;
    private State state = State.STOPPED;
    private Loop loop = Loop.NONE;
    private long originMs;
    private long pausedPositionMs;
    private long trackId;
    private int consecutiveErrors;
    private long lastSyncMs;
    private Consumer<String> trace = line -> { };
    private String label = "";

    public PlaybackEngine(Clock clock, Settings settings, LongSupplier trackIds, Output output) {
        this.clock = clock;
        this.settings = settings;
        this.trackIds = trackIds;
        this.output = output;
    }

    /** 状態を変える操作の診断ログの出し先。label はどのエンジンか(例: 個人:alice)。 */
    public void setTrace(String label, Consumer<String> trace) {
        this.label = label;
        this.trace = trace;
    }

    private void trace(String op) {
        trace.accept("エンジン[" + label + "] " + op + ": state=" + state + " index=" + index + "/" + queue.size()
                + " trackId=" + trackId + " originMs=" + originMs + " pausedPositionMs=" + pausedPositionMs
                + " now=" + clock.nowMs());
    }

    // ---- 読み取り ----

    public State state() {
        return state;
    }

    public Loop loop() {
        return loop;
    }

    public int index() {
        return index;
    }

    public List<Track> queue() {
        return List.copyOf(queue);
    }

    public Track currentTrack() {
        return index >= 0 && index < queue.size() ? queue.get(index) : null;
    }

    public long trackId() {
        return trackId;
    }

    public long originMs() {
        return originMs;
    }

    /** 曲の頭からの再生位置（ミリ秒）。曲の頭より前（開始待ち）のあいだは負になる。 */
    public long positionMs() {
        return switch (state) {
            case PLAYING -> clock.nowMs() - originMs;
            case PAUSED -> pausedPositionMs;
            case STOPPED -> 0;
        };
    }

    public Snapshot snapshot() {
        return new Snapshot(List.copyOf(queue), index, loop, state, Math.max(0, positionMs()));
    }

    // ---- 操作 ----

    /** キューを空にして、1曲だけ再生する。 */
    public Result playSingle(Track track, boolean loopSingle) {
        queue.clear();
        queue.add(track);
        index = 0;
        loop = loopSingle && !track.live() ? Loop.SINGLE : Loop.NONE;
        startCurrent(true);
        return Result.OK;
    }

    /** キューを入れ替えて、先頭から順に再生する。 */
    public Result playPlaylist(List<Track> tracks, boolean loopAll) {
        if (tracks.isEmpty()) {
            return Result.EMPTY;
        }
        queue.clear();
        queue.addAll(tracks);
        index = 0;
        loop = loopAll ? Loop.PLAYLIST : Loop.NONE;
        startCurrent(true);
        return Result.OK;
    }

    /** キューの後ろに足す。空か停止中なら、足した曲から再生を始める。 */
    public Result add(List<Track> tracks) {
        if (tracks.isEmpty()) {
            return Result.EMPTY;
        }
        int firstAdded = queue.size();
        queue.addAll(tracks);
        if (state == State.STOPPED) {
            index = firstAdded;
            startCurrent(true);
        }
        return Result.OK;
    }

    /** 現在のトラックを最初から再生する。 */
    public Result playCurrent() {
        if (currentTrack() == null) {
            return Result.EMPTY;
        }
        startCurrent(true);
        return Result.OK;
    }

    /** 再生中なら一時停止、一時停止中なら再開。停止中は NOT_PLAYING。 */
    public Result pause() {
        long now = clock.nowMs();
        switch (state) {
            case PLAYING -> {
                pausedPositionMs = Math.max(0, now - originMs);
                state = State.PAUSED;
                output.pause(pausedPositionMs);
                trace("pause");
                return Result.OK;
            }
            case PAUSED -> {
                originMs = now + settings.startLeadMs() - pausedPositionMs;
                state = State.PLAYING;
                lastSyncMs = now;
                output.resume(originMs);
                trace("resume");
                return Result.OK;
            }
            default -> {
                return Result.NOT_PLAYING;
            }
        }
    }

    /** 止めてキューを空にし、loop も解除する。 */
    public void stop() {
        queue.clear();
        index = 0;
        loop = Loop.NONE;
        stopPlayback();
    }

    public Result skip() {
        Track current = currentTrack();
        if (current == null) {
            return Result.EMPTY;
        }
        if (current.live()) {
            return Result.LIVE;
        }
        advance(false);
        return Result.OK;
    }

    public Result previous() {
        Track current = currentTrack();
        if (current == null) {
            return Result.EMPTY;
        }
        if (current.live()) {
            return Result.LIVE;
        }
        if (index > 0) {
            index--;
        }
        startCurrent(true);
        return Result.OK;
    }

    /** キューの index 番目の曲を、最初から再生する(プレーヤー画面で曲を選んだとき)。範囲外や、ライブの再生中は何もしない。 */
    public Result jump(int target) {
        if (target < 0 || target >= queue.size()) {
            return Result.EMPTY;
        }
        Track current = currentTrack();
        if (current != null && current.live() && state != State.STOPPED) {
            return Result.LIVE;
        }
        index = target;
        startCurrent(true);
        return Result.OK;
    }

    /**
     * キューの from 番目の曲を、to 番目へ動かす(プレーヤー画面のドラッグ)。再生は止めない。
     * 鳴っている曲は、並べ替えたあとも同じ曲のまま(何曲目かの番号が、それに合わせて変わる)。
     */
    public Result move(int from, int to) {
        int n = queue.size();
        if (from < 0 || from >= n || to < 0 || to >= n || from == to) {
            return Result.EMPTY;
        }
        Track moved = queue.remove(from);
        queue.add(to, moved);
        if (index == from) {
            index = to;
        } else if (from < index && to >= index) {
            index--;
        } else if (from > index && to <= index) {
            index++;
        }
        trace("move(" + from + "→" + to + ")");
        return Result.OK;
    }

    /**
     * 今の曲の再生位置を動かす(プレーヤー画面の進行バー)。ライブや停止中は何もしない。
     * SYNC では位置を飛ばせない(補正は速度の微調整だけ)ので、同じ曲の PLAY を、動かし先から鳴るように作った
     * 新しい基準時刻で送り直す。一時停止中なら、その位置で止めたままにする。
     */
    public Result seek(long positionMs) {
        Track track = currentTrack();
        if (track == null || state == State.STOPPED) {
            return Result.NOT_PLAYING;
        }
        if (track.live()) {
            return Result.LIVE;
        }
        long to = Math.max(0, positionMs);
        if (track.durationMs() > 0) {
            to = Math.min(to, Math.max(0, track.durationMs() - 1000)); // 曲の終わりぎわで、すぐ次へ進まないようにする
        }
        long now = clock.nowMs();
        trackId = trackIds.getAsLong();
        originMs = now + settings.startLeadMs() - to;
        lastSyncMs = now;
        consecutiveErrors = 0;
        output.play(track, trackId, originMs);
        if (state == State.PAUSED) {
            pausedPositionMs = to;
            output.pause(to);
        }
        trace("seek(" + to + ")");
        return Result.OK;
    }

    /** loop を なし → 単曲 → 全体 → なし の順に切り替える。今の曲がライブなら、単曲は飛ばす。切り替えたあとの値を返す。 */
    public Loop cycleLoop() {
        Loop next = switch (loop) {
            case NONE -> Loop.SINGLE;
            case SINGLE -> Loop.PLAYLIST;
            case PLAYLIST -> Loop.NONE;
        };
        Track current = currentTrack();
        if (next == Loop.SINGLE && current != null && current.live()) {
            next = Loop.PLAYLIST;
        }
        loop = next;
        trace("loop=" + loop);
        return loop;
    }

    /** mod から ERROR が届いた。現在の曲のものだけを扱い、飛ばすか止める。 */
    public ErrorOutcome onError(long errorTrackId) {
        if (state == State.STOPPED || errorTrackId != trackId) {
            return ErrorOutcome.IGNORED;
        }
        consecutiveErrors++;
        if (consecutiveErrors >= settings.maxConsecutiveErrors()) {
            stopPlayback();
            return ErrorOutcome.STOPPED_BY_LIMIT;
        }
        return advance(true) ? ErrorOutcome.SKIPPED : ErrorOutcome.STOPPED_AT_END;
    }

    /** 共有など別のエンジンの状態を写して、始める。 */
    public void restore(Snapshot snapshot) {
        queue.clear();
        queue.addAll(snapshot.queue());
        index = snapshot.index();
        loop = snapshot.loop();
        if (snapshot.state() == State.STOPPED || currentTrack() == null) {
            state = State.STOPPED;
            return;
        }
        long now = clock.nowMs();
        consecutiveErrors = 0;
        trackId = trackIds.getAsLong();
        originMs = now + settings.startLeadMs() - snapshot.positionMs();
        state = State.PLAYING;
        lastSyncMs = now;
        output.play(currentTrack(), trackId, originMs);
        output.trackStarted(currentTrack());
        trace("restore(位置=" + snapshot.positionMs() + ")");
        if (snapshot.state() == State.PAUSED) {
            pausedPositionMs = snapshot.positionMs();
            state = State.PAUSED;
            output.pause(pausedPositionMs);
        }
    }

    /** 定期的に呼ぶ。曲の終わりで次へ進め、SYNC の間隔が来たら基準時刻を再通知する。 */
    public void tick() {
        if (state != State.PLAYING) {
            return;
        }
        long now = clock.nowMs();
        Track current = currentTrack();
        if (current != null && !current.live() && now >= originMs + current.durationMs()) {
            consecutiveErrors = 0;
            if (loop == Loop.SINGLE) {
                startCurrent(false);
            } else if (!advance(false)) {
                output.ended();
            }
            return;
        }
        if (now - lastSyncMs >= settings.syncIntervalMs()) {
            lastSyncMs = now;
            output.sync(originMs);
        }
    }

    // ---- 内部 ----

    /**
     * 次の曲へ進めて始める。最後の曲なら、PLAYLIST loop は先頭へ戻り、それ以外は止める（キューは残す）。
     * @return 次の曲を始めたら true、止めたら false
     */
    private boolean advance(boolean fromError) {
        if (index + 1 < queue.size()) {
            index++;
        } else if (loop == Loop.PLAYLIST && !queue.isEmpty()) {
            index = 0;
        } else {
            stopPlayback();
            return false;
        }
        startCurrent(!fromError);
        return true;
    }

    private void startCurrent(boolean resetErrors) {
        long now = clock.nowMs();
        if (resetErrors) {
            consecutiveErrors = 0;
        }
        trackId = trackIds.getAsLong();
        originMs = now + settings.startLeadMs();
        state = State.PLAYING;
        lastSyncMs = now;
        Track track = queue.get(index);
        output.play(track, trackId, originMs);
        output.trackStarted(track);
        trace("startCurrent");
    }

    private void stopPlayback() {
        if (state != State.STOPPED) {
            state = State.STOPPED;
            output.stop();
            trace("stop");
        }
    }
}
