package com.oogata.radiomod.audio;

import com.oogata.radiomod.RadioMod;
import com.oogata.radiomod.net.Messages;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.function.DoubleSupplier;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;

/**
 * 1つのスロットの再生。LavaPlayer でデコードした PCM を、自分で作った OpenAL の音源に流す。
 *
 * <p>状態の変更と OpenAL の操作は全て {@code lock} の中で行う。専用のスレッドが約10msごとに
 * 音源の掃除・デコード済み音声の補充・速度の更新をする。
 *
 * <p>再生中に位置を飛ばすのは {@link #seek} だけで、呼び出し側が「曲の始め・再開・取得が途切れたあとの再開」
 * のときだけ使う。ずれの補正には {@link #setSpeedDeviation} の微調整を使う。
 */
public final class SlotPlayer implements AutoCloseable {

    public enum State { IDLE, LOADING, BUFFERING, PLAYING, PAUSED, ENDED }

    /** 1つの OpenAL バッファに入れる長さ（フレームの数）。20ms × 5 = 100ms。 */
    private static final int FRAMES_PER_BUFFER = 5;
    private static final int BUFFER_COUNT = 10;
    /** これだけ溜まるまで鳴らし始めない。 */
    private static final int PREBUFFER = 3;
    private static final int QUEUE_TARGET = 8;
    private static final long TICK_MS = 10;

    private final Messages.Slot slot;
    private final DoubleSupplier gain;
    private final PlaybackListener listener;
    private final Object lock = new Object();

    private final PlayedPosition position = new PlayedPosition(Decoder.SAMPLE_RATE);
    private final SpeedSmoother speed = new SpeedSmoother();
    private final ArrayDeque<Integer> freeBuffers = new ArrayDeque<>();
    private final ArrayDeque<int[]> queued = new ArrayDeque<>(); // {bufferId, frames}

    private State state = State.IDLE;
    private int generation;
    private AudioPlayer player;
    private AudioTrack track;
    private boolean trackDone;
    private boolean live;
    private long durationMs = -1;

    private long alContext;
    private int source;
    private int[] allBuffers;

    private ByteBuffer pending;
    private int pendingFrames;

    private long lastTickNanos;
    private Thread worker;
    private boolean closed;

    public SlotPlayer(Messages.Slot slot, DoubleSupplier gain, PlaybackListener listener) {
        this.slot = slot;
        this.gain = gain;
        this.listener = listener;
    }

    public Messages.Slot slot() {
        return slot;
    }

    // ---- 操作 ----

    /** URL を取得して、startMs の位置から鳴らす。前の曲はすぐ止める。失敗は listener に届く。 */
    public void play(String url, long startMs) {
        String checked;
        try {
            checked = UrlValidator.validate(url);
        } catch (AudioFailure f) {
            fail(f);
            return;
        }
        int gen;
        synchronized (lock) {
            if (closed) {
                return;
            }
            resetPlayback();
            gen = ++generation;
            state = State.LOADING;
            position.reset(Math.max(0, startMs));
        }
        try {
            Decoder.loader().loadItem(checked, new LoadHandler(gen, Math.max(0, startMs)));
        } catch (Throwable t) {
            fail(AudioFailure.other("読み込みを始められません: " + t));
        }
    }

    public void pause() {
        synchronized (lock) {
            if (state == State.PLAYING) {
                if (source != 0) {
                    AL10.alSourcePause(source);
                }
                state = State.PAUSED;
            } else if (state == State.BUFFERING) {
                state = State.PAUSED;
            }
        }
    }

    public void resume() {
        synchronized (lock) {
            if (state == State.PAUSED) {
                state = queued.isEmpty() ? State.BUFFERING : State.PLAYING;
                if (state == State.PLAYING && source != 0) {
                    AL10.alSourcePlay(source);
                }
            }
        }
    }

    public void stop() {
        synchronized (lock) {
            resetPlayback();
            generation++;
            state = State.IDLE;
        }
    }

    /**
     * 位置を合わせ直す。ライブなど合わせ直せない音源では何もせず false を返す。
     * 音が飛ぶので、曲の始め・再開・取得が途切れたあとの再開のときだけ使うこと。
     */
    public boolean seek(long positionMs) {
        synchronized (lock) {
            if (track == null || live || !track.isSeekable()) {
                return false;
            }
            long target = Math.max(0, positionMs);
            if (durationMs > 0) {
                target = Math.min(target, durationMs);
            }
            flushOutput();
            trackDone = false;
            position.reset(target);
            track.setPosition(target);
            if (state == State.PLAYING || state == State.ENDED) {
                state = State.BUFFERING;
            }
            return true;
        }
    }

    /** 速度の目標のずれ。+0.01 なら 1% 速く。最大 ±2% で、実際の速度はなめらかに近づく。 */
    public void setSpeedDeviation(double deviation) {
        synchronized (lock) {
            speed.setTarget(deviation);
        }
    }

    /** 実際に鳴っている位置（曲の頭からのミリ秒）。鳴らす曲がなければ -1。 */
    public long getPositionMs() {
        synchronized (lock) {
            if (track == null || state == State.IDLE || state == State.LOADING) {
                return -1;
            }
            long offset = 0;
            if (source != 0 && !queued.isEmpty() && contextValid()) {
                offset = AL10.alGetSourcei(source, org.lwjgl.openal.AL11.AL_SAMPLE_OFFSET);
            }
            return position.positionMs(offset);
        }
    }

    public State state() {
        synchronized (lock) {
            return state;
        }
    }

    public boolean isLive() {
        synchronized (lock) {
            return live;
        }
    }

    public boolean isSeekable() {
        synchronized (lock) {
            return track != null && !live && track.isSeekable();
        }
    }

    public long durationMs() {
        synchronized (lock) {
            return durationMs;
        }
    }

    public double currentSpeed() {
        synchronized (lock) {
            return speed.rate();
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            resetPlayback();
            generation++;
            state = State.IDLE;
            closed = true;
            releaseAl();
            if (player != null) {
                player.destroy();
                player = null;
            }
        }
    }

    // ---- 内部 ----

    private void fail(AudioFailure failure) {
        synchronized (lock) {
            resetPlayback();
            generation++;
            state = State.IDLE;
        }
        RadioMod.LOGGER.warn("再生に失敗 [{}] reason={} {}", describe(), failure.reason(), failure.detail());
        listener.onError(slot, failure);
    }

    private String describe() {
        return slot.slotType() + (slot.radioId() != null ? ":" + slot.radioId() : "");
    }

    /** 鳴らしている曲を捨てて、音源を空にする（OpenAL の音源自体は残す）。lock の中で呼ぶ。 */
    private void resetPlayback() {
        if (player != null) {
            player.stopTrack();
        }
        track = null;
        trackDone = false;
        live = false;
        durationMs = -1;
        flushOutput();
        speed.reset();
        position.reset(0);
    }

    private void flushOutput() {
        if (source != 0 && contextValid()) {
            AL10.alSourceStop(source);
            while (!queued.isEmpty()) {
                int[] q = queued.poll();
                AL10.alSourceUnqueueBuffers(source);
                freeBuffers.add(q[0]);
            }
            AL10.alSourcef(source, AL10.AL_PITCH, 1.0f);
        } else {
            queued.clear();
        }
        pending = null;
        pendingFrames = 0;
    }

    private boolean contextValid() {
        return alContext != 0 && ALC10.alcGetCurrentContext() == alContext;
    }

    private void ensureAl() throws AudioFailure {
        long ctx = ALC10.alcGetCurrentContext();
        if (ctx == 0) {
            throw AudioFailure.other("OpenAL がまだ使えません");
        }
        if (source != 0 && ctx == alContext) {
            return;
        }
        if (source != 0) {
            // サウンドエンジンが作り直された。古い音源はもう無効なので、捨てて作り直す。
            source = 0;
            allBuffers = null;
            freeBuffers.clear();
            queued.clear();
        }
        AL10.alGetError();
        source = AL10.alGenSources();
        alCheck("alGenSources");
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0f);
        allBuffers = new int[BUFFER_COUNT];
        for (int i = 0; i < BUFFER_COUNT; i++) {
            allBuffers[i] = AL10.alGenBuffers();
            freeBuffers.add(allBuffers[i]);
        }
        alCheck("alGenBuffers");
        alContext = ctx;
    }

    private void releaseAl() {
        if (source != 0 && contextValid()) {
            AL10.alSourceStop(source);
            AL10.alSourcei(source, AL10.AL_BUFFER, 0);
            AL10.alDeleteSources(source);
            for (int b : allBuffers) {
                AL10.alDeleteBuffers(b);
            }
        }
        source = 0;
        allBuffers = null;
        freeBuffers.clear();
        queued.clear();
    }

    private static void alCheck(String op) throws AudioFailure {
        int err = AL10.alGetError();
        if (err != AL10.AL_NO_ERROR) {
            throw AudioFailure.other("OpenAL エラー (" + op + "): 0x" + Integer.toHexString(err));
        }
    }

    private void startWorker() {
        if (worker == null) {
            worker = new Thread(this::run, "radiomod-audio-" + describe());
            worker.setDaemon(true);
            worker.start();
        }
    }

    private void run() {
        while (true) {
            AudioFailure failure = null;
            boolean finished = false;
            boolean started = false;
            synchronized (lock) {
                if (closed) {
                    worker = null;
                    return;
                }
                if (track != null) {
                    State before = state;
                    try {
                        tick();
                    } catch (AudioFailure f) {
                        failure = f;
                    } catch (RuntimeException e) {
                        failure = AudioFailure.other("再生中の内部エラー: " + e);
                    }
                    started = before == State.BUFFERING && state == State.PLAYING;
                    if (failure == null && state == State.ENDED) {
                        finished = true;
                        track = null;
                        player.stopTrack();
                    }
                }
            }
            if (failure != null) {
                fail(failure);
            } else if (started) {
                listener.onStarted(slot);
            } else if (finished) {
                listener.onFinished(slot);
            }
            try {
                Thread.sleep(TICK_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void tick() throws AudioFailure {
        if (state == State.LOADING || state == State.IDLE || state == State.ENDED) {
            return;
        }
        ensureAl();
        long now = System.nanoTime();
        double dt = lastTickNanos == 0 ? 0 : (now - lastTickNanos) / 1e9;
        lastTickNanos = now;

        // 鳴らし終えたバッファを回収して、再生済みのフレーム数に足す。
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed && !queued.isEmpty(); i++) {
            int[] q = queued.poll();
            AL10.alSourceUnqueueBuffers(source);
            position.addProcessed(q[1]);
            freeBuffers.add(q[0]);
        }

        if (state != State.PAUSED) {
            fill();
        }

        AL10.alSourcef(source, AL10.AL_PITCH, (float) speed.step(dt));
        AL10.alSourcef(source, AL10.AL_GAIN, (float) Math.max(0, gain.getAsDouble()));

        if (state == State.BUFFERING && (queued.size() >= PREBUFFER || (trackDone && !queued.isEmpty()))) {
            AL10.alSourcePlay(source);
            state = State.PLAYING;
        } else if (state == State.PLAYING) {
            int alState = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
            if (alState != AL10.AL_PLAYING && !queued.isEmpty()) {
                // 取得が間に合わず、音が途切れた。溜まったぶんからまた鳴らす。
                AL10.alSourcePlay(source);
            } else if (queued.isEmpty() && trackDone && pending == null) {
                state = State.ENDED;
            }
        }
        alCheck("tick");
    }

    /** デコード済みのフレームを集めて、OpenAL のバッファに入れて音源に積む。 */
    private void fill() throws AudioFailure {
        while (queued.size() < QUEUE_TARGET && !freeBuffers.isEmpty()) {
            AudioFrame frame = player.provide();
            if (frame == null) {
                if (trackDone && pending != null) {
                    upload();
                }
                return;
            }
            if (!position.isBased()) {
                long tc = frame.getTimecode();
                position.setBase(tc >= 0 ? tc : 0);
            }
            if (pending == null) {
                pending = ByteBuffer.allocateDirect(FRAMES_PER_BUFFER * Decoder.FRAME_SAMPLES * Decoder.CHANNELS * 2)
                        .order(ByteOrder.LITTLE_ENDIAN);
                pendingFrames = 0;
            }
            byte[] data = frame.getData();
            int usable = Math.min(data.length, pending.remaining());
            pending.put(data, 0, usable);
            pendingFrames += usable / (Decoder.CHANNELS * 2);
            if (!pending.hasRemaining()) {
                upload();
            }
        }
    }

    private void upload() throws AudioFailure {
        pending.flip();
        int buffer = freeBuffers.poll();
        AL10.alBufferData(buffer, AL10.AL_FORMAT_STEREO16, pending, Decoder.SAMPLE_RATE);
        AL10.alSourceQueueBuffers(source, buffer);
        queued.add(new int[] {buffer, pendingFrames});
        pending = null;
        pendingFrames = 0;
        alCheck("upload");
    }

    private final class LoadHandler implements AudioLoadResultHandler {
        private final int gen;
        private final long startMs;

        LoadHandler(int gen, long startMs) {
            this.gen = gen;
            this.startMs = startMs;
        }

        @Override
        public void trackLoaded(AudioTrack loaded) {
            begin(loaded);
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            AudioTrack pick = playlist.getSelectedTrack();
            if (pick == null && !playlist.getTracks().isEmpty()) {
                pick = playlist.getTracks().get(0);
            }
            if (pick == null) {
                failIfCurrent(AudioFailure.fetch("プレイリストが空です"));
            } else {
                begin(pick);
            }
        }

        @Override
        public void noMatches() {
            failIfCurrent(AudioFailure.unsupported("対応していないURLです"));
        }

        @Override
        public void loadFailed(FriendlyException e) {
            failIfCurrent(AudioFailure.fetch(e.getMessage() == null ? e.toString() : e.getMessage()));
        }

        private void failIfCurrent(AudioFailure f) {
            synchronized (lock) {
                if (gen != generation || closed) {
                    return;
                }
            }
            fail(f);
        }

        private void begin(AudioTrack loaded) {
            synchronized (lock) {
                if (gen != generation || closed) {
                    return;
                }
                live = loaded.getInfo().isStream;
                durationMs = live ? -1 : loaded.getDuration();
                long start = startMs;
                if (start > 0 && (live || !loaded.isSeekable())) {
                    start = 0; // 位置を合わせ直せない音源は、取得した時点から鳴らす
                }
                if (start > 0) {
                    loaded.setPosition(start);
                }
                position.reset(start);
                if (player == null) {
                    player = Decoder.createPlayer();
                    player.addListener(new AudioEventAdapter() {
                        @Override
                        public void onTrackEnd(AudioPlayer p, AudioTrack t, AudioTrackEndReason reason) {
                            synchronized (lock) {
                                if (t == track && reason.mayStartNext) {
                                    trackDone = true;
                                }
                            }
                        }

                        @Override
                        public void onTrackException(AudioPlayer p, AudioTrack t, FriendlyException e) {
                            AudioFailure f;
                            synchronized (lock) {
                                if (t != track) {
                                    return;
                                }
                                f = AudioFailure.decode(e.getMessage() == null ? e.toString() : e.getMessage());
                            }
                            fail(f);
                        }
                    });
                }
                track = loaded;
                trackDone = false;
                lastTickNanos = 0;
                state = State.BUFFERING;
                player.playTrack(loaded);
                startWorker();
            }
        }
    }
}
