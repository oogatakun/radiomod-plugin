package com.oogata.radiomod.sync;

/**
 * 同期の設定の、いま使っている値。クライアント設定から書き込まれ、再生中でも次の更新で読まれる。
 * 設定を読み込む前は、既定値（補正を始める 40ms・やめる 10ms・上限 ±2%）。
 */
public final class SyncSettings {

    private static volatile double startMs = DriftCorrector.START_MS;
    private static volatile double stopMs = DriftCorrector.STOP_MS;
    private static volatile double maxDeviation = DriftCorrector.MAX_DEVIATION;

    private SyncSettings() {
    }

    public static double startMs() {
        return startMs;
    }

    public static double stopMs() {
        return stopMs;
    }

    public static double maxDeviation() {
        return maxDeviation;
    }

    public static void set(double startMs, double stopMs, double maxDeviation) {
        SyncSettings.startMs = startMs;
        SyncSettings.stopMs = stopMs;
        SyncSettings.maxDeviation = maxDeviation;
    }
}
