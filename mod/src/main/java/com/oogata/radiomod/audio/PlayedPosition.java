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

    /** 再生し終えたバッファのフレーム数を足す。 */
    public void addProcessed(int frames) {
        processedFrames += frames;
    }

    /** offsetFrames は、いま鳴っているバッファの先頭からのフレーム数。 */
    public long positionMs(long offsetFrames) {
        return baseMs + (processedFrames + Math.max(0, offsetFrames)) * 1000L / sampleRate;
    }

    public long processedFrames() {
        return processedFrames;
    }
}
