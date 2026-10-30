package com.oogata.radiomod.audio;

/**
 * 再生速度の微調整。目標は ±maxDeviation（既定 2%）に収め、実際の速度は目標に向けて少しずつ近づける。
 * 速度が急に変わって、音程や速さが目立たないようにするため。
 */
public final class SpeedSmoother {

    /** 速度の最大のずれ（0.02 = ±2%）。 */
    public static final double MAX_DEVIATION = 0.02;
    /** 1秒あたりに変えてよい速度のずれ（0.01 = 1秒で1%）。 */
    public static final double MAX_SLEW_PER_SECOND = 0.01;

    private final double maxDeviation;
    private final double maxSlewPerSecond;
    private double target;
    private double current;

    public SpeedSmoother() {
        this(MAX_DEVIATION, MAX_SLEW_PER_SECOND);
    }

    public SpeedSmoother(double maxDeviation, double maxSlewPerSecond) {
        this.maxDeviation = maxDeviation;
        this.maxSlewPerSecond = maxSlewPerSecond;
    }

    /** 目標の速度のずれ。+0.01 なら 1% 速く、-0.01 なら 1% 遅く。範囲外は丸める。 */
    public void setTarget(double deviation) {
        target = Math.max(-maxDeviation, Math.min(maxDeviation, deviation));
    }

    public double target() {
        return target;
    }

    /** dtSeconds 進めて、いまの再生速度（1.0 が等倍）を返す。 */
    public double step(double dtSeconds) {
        double limit = maxSlewPerSecond * Math.max(0, dtSeconds);
        double diff = target - current;
        if (Math.abs(diff) <= limit) {
            current = target;
        } else {
            current += Math.signum(diff) * limit;
        }
        return rate();
    }

    public double rate() {
        return 1.0 + current;
    }

    /** 等倍に戻す（曲の切り替え時）。 */
    public void reset() {
        target = 0;
        current = 0;
    }
}
