package com.oogata.radiomod.sync;

import java.util.ArrayDeque;
import java.util.function.LongSupplier;

/**
 * 「いまのサーバー時刻」の推定（docs/protocol.md の「時計合わせ」）。
 *
 * <p>TIME の返事ごとに、往復時間と offset（サーバー時刻 - 自分の nanoTime）を記録する。
 * 直近8回のうち往復時間が最短のものの offset を採用し、採用値は一度に5msまでしか動かさない。
 */
public final class ServerClock {

    /** 時計が合っていないときの {@link #nowServerMs()} の値。 */
    public static final long UNSYNCED = Long.MIN_VALUE;

    static final int WINDOW = 8;
    static final long MAX_STEP_NS = 5_000_000L;
    private static final long NS_PER_MS = 1_000_000L;

    private record Sample(long rttNs, long offsetNs) {
    }

    private final LongSupplier nanoSource;
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private boolean synced;
    private long offsetNs;
    private long lastRttNs = -1;

    public ServerClock() {
        this(System::nanoTime);
    }

    public ServerClock(LongSupplier nanoSource) {
        this.nanoSource = nanoSource;
    }

    /**
     * TIME を受け取ったとき。t0 は TIME_REQ を送る直前の nanoTime（返事にそのまま入っている）、
     * t1 は TIME を受け取った時点の nanoTime。
     */
    public synchronized void onResponse(long t0Nanos, long t1Nanos, long serverNowMs) {
        long rtt = t1Nanos - t0Nanos;
        if (rtt < 0) {
            return;
        }
        long mid = t0Nanos + rtt / 2;
        long offset = serverNowMs * NS_PER_MS - mid;
        samples.addLast(new Sample(rtt, offset));
        while (samples.size() > WINDOW) {
            samples.removeFirst();
        }
        Sample best = null;
        for (Sample s : samples) {
            if (best == null || s.rttNs <= best.rttNs) {
                best = s;
            }
        }
        if (!synced) {
            offsetNs = best.offsetNs;
            synced = true;
        } else {
            long delta = best.offsetNs - offsetNs;
            offsetNs += Math.max(-MAX_STEP_NS, Math.min(MAX_STEP_NS, delta));
        }
        lastRttNs = rtt;
    }

    public synchronized boolean isSynced() {
        return synced;
    }

    /** いまのサーバー時刻（ミリ秒）の推定値。まだ合っていなければ {@link #UNSYNCED}。 */
    public synchronized long nowServerMs() {
        if (!synced) {
            return UNSYNCED;
        }
        return Math.floorDiv(nanoSource.getAsLong() + offsetNs, NS_PER_MS);
    }

    /** サーバー時刻 - 自分の時計（ミリ秒）。デバッグ表示用。 */
    public synchronized double offsetMs() {
        return offsetNs / (double) NS_PER_MS;
    }

    /** 直近の往復時間（ミリ秒）。まだなければ -1。 */
    public synchronized double lastRttMs() {
        return lastRttNs < 0 ? -1 : lastRttNs / (double) NS_PER_MS;
    }

    /** 採用中の（最短の）往復時間。まだなければ -1。 */
    public synchronized double bestRttMs() {
        long best = -1;
        for (Sample s : samples) {
            if (best < 0 || s.rttNs < best) {
                best = s.rttNs;
            }
        }
        return best < 0 ? -1 : best / (double) NS_PER_MS;
    }

    public synchronized void reset() {
        samples.clear();
        synced = false;
        offsetNs = 0;
        lastRttNs = -1;
    }
}
