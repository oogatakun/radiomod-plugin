package com.oogata.radiomod.sync;

/**
 * ラジオ1台の置き場所（次元・座標・聞こえる範囲）と、聞く側からの距離・音量の計算。
 * Minecraft には依存せず、聞く側の位置は {@link Listener} から受け取る。
 */
public final class RadioPlace {

    /** 聞く側（カメラ）の次元と座標。 */
    public record ListenerPos(String dimension, double x, double y, double z) {
    }

    /** 聞く側の位置を返す。ワールドに入っていないときは null。 */
    @FunctionalInterface
    public interface Listener {
        ListenerPos position();
    }

    /** この距離までは、減衰せずに全開で聞こえる（ブロック）。 */
    public static final double FULL_VOLUME_DISTANCE = 2.0;

    private final Listener listener;
    private volatile String dimension = "";
    private volatile double x;
    private volatile double y;
    private volatile double z;
    private volatile float range = 24f;

    public RadioPlace(Listener listener) {
        this.listener = listener;
    }

    /** PLAY で届いた置き場所を入れる。 */
    public void set(String dimension, double x, double y, double z, float range) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.range = range;
    }

    /** MOVE で届いた新しい座標を入れる。範囲は変わらない。 */
    public void move(String dimension, double x, double y, double z) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String dimension() {
        return dimension;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float range() {
        return range;
    }

    /** 聞く側との距離。聞く側の位置が分からないときと、別の次元にいるときは NaN。 */
    public double distance() {
        ListenerPos p = listener.position();
        if (p == null || !dimension.equals(p.dimension())) {
            return Double.NaN;
        }
        double dx = p.x() - x;
        double dy = p.y() - y;
        double dz = p.z() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** いまの位置での距離による音量（0〜1）。聞こえない位置なら 0。 */
    public double gain() {
        double d = distance();
        return Double.isNaN(d) ? 0 : gainAt(d, range);
    }

    /** 距離 d・範囲 range のときの音量。FULL_VOLUME_DISTANCE までは 1、range で 0 になるまで直線的に下がる。 */
    public static double gainAt(double d, double range) {
        if (d <= FULL_VOLUME_DISTANCE) {
            return 1.0;
        }
        if (d >= range) {
            return 0.0;
        }
        return 1.0 - (d - FULL_VOLUME_DISTANCE) / (range - FULL_VOLUME_DISTANCE);
    }
}
