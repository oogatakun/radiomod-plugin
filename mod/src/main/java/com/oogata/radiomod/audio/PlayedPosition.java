package com.oogata.radiomod.audio;

/**
 * 実際に鳴っている位置（曲の頭からのミリ秒）を、再生済みのサンプル数から計算する。
 *
 * <p>基準位置（最初のフレームの位置）に、再生し終えたバッファのフレーム数と、
 * いま鳴っているバッファの中の位置を足す。再生速度を変えても、数えるのは音源側のフレーム数なので、
 * 曲の位置としてそのまま使える。
 */
public final class PlayedPosition {

    private final int sampleRate;
    private long baseMs;
    private boolean based;
    private long processedFrames;
    private boolean started;

    public PlayedPosition(int sampleRate) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate");
        }
        this.sampleRate = sampleRate;
    }

    /** 新しく鳴らし始める（または位置を合わせ直す）。基準位置は、最初のフレームが届いたときに決める。 */
    public void reset(long requestedStartMs) {
        baseMs = requestedStartMs;
        based = false;
        processedFrames = 0;
        started = false;
    }

    public boolean isBased() {
        return based;
    }

    /** 最初のフレームが届いた。基準位置は、reset で指定した（要求した）位置のままにする。 */
    public void markBased() {
        based = true;
    }

    /** 最初のフレームの位置が分かったときに呼ぶ。 */
    public void setBase(long firstFrameMs) {
        baseMs = firstFrameMs;
        based = true;
    }

    /**
     * 音源で実際に鳴らし始めた（alSourcePlay を呼んだ）。reset から始めるまでの間は、まだ1サンプルも鳴っていないので、
     * 再生済みの数も音源の位置も数えない（キューに積んだバッファや、音源側の古い値を、再生済みに数えないため）。
     */
    public void start() {
        started = true;
    }

    public boolean isStarted() {
        return started;
    }

    /** 再生し終えたバッファのフレーム数を足す。鳴らし始める前は数えない。 */
    public void addProcessed(int frames) {
        if (started) {
            processedFrames += frames;
        }
    }

    /** offsetFrames は、いま鳴っているバッファの先頭からのフレーム数。鳴らし始める前は、基準位置のまま。 */
    public long positionMs(long offsetFrames) {
        if (!started) {
            return baseMs;
        }
        return baseMs + (processedFrames + Math.max(0, offsetFrames)) * 1000L / sampleRate;
    }

    public long processedFrames() {
        return processedFrames;
    }
}
