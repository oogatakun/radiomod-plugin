package com.oogata.radiomod.sync;

import java.util.function.DoubleSupplier;

/**
 * ずれ（鳴らすべき位置 - 鳴っている位置）から、再生速度の目標のずれを決める。
 * 差が startMs 以上になったら補正を始め、stopMs 未満になったらやめる。速度は最大 ±maxDeviation。
 * 遅れているとき（差が正）は速く、進んでいるとき（差が負）は遅くする。
 *
 * <p>速度の変化をなめらかにするのは {@code SpeedSmoother} の役目。ここは目標だけを返す。
 */
public final class DriftCorrector {

    public static final double START_MS = 40;
    public static final double STOP_MS = 10;
    public static final double MAX_DEVIATION = 0.02;
    /** ずれがこの値（ミリ秒）のとき、目標が 1.0（=100%）になる比例の係数。 */
    private static final double GAIN_MS = 2000;

    private final DoubleSupplier startMs;
    private final DoubleSupplier stopMs;
    private final DoubleSupplier maxDeviation;
    private boolean correcting;

    /** 値は {@link SyncSettings}（クライアント設定）を、更新のたびに読む。 */
    public DriftCorrector() {
        this(SyncSettings::startMs, SyncSettings::stopMs, SyncSettings::maxDeviation);
    }

    public DriftCorrector(double startMs, double stopMs, double maxDeviation) {
        this(() -> startMs, () -> stopMs, () -> maxDeviation);
    }

    public DriftCorrector(DoubleSupplier startMs, DoubleSupplier stopMs, DoubleSupplier maxDeviation) {
        this.startMs = startMs;
        this.stopMs = stopMs;
        this.maxDeviation = maxDeviation;
    }

    /** diffMs = 鳴らすべき位置 - 鳴っている位置。戻り値は速度の目標のずれ（0.01 = 1% 速く）。 */
    public double update(double diffMs) {
        double abs = Math.abs(diffMs);
        if (!correcting && abs >= startMs.getAsDouble()) {
            correcting = true;
        } else if (correcting && abs < stopMs.getAsDouble()) {
            correcting = false;
        }
        if (!correcting) {
            return 0;
        }
        double max = maxDeviation.getAsDouble();
        return Math.max(-max, Math.min(max, diffMs / GAIN_MS));
    }

    public boolean isCorrecting() {
        return correcting;
    }

    public void reset() {
        correcting = false;
    }
}
