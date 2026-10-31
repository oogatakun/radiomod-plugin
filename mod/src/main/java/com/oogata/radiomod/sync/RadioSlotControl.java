package com.oogata.radiomod.sync;

import com.oogata.radiomod.net.Messages;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * ラジオ1台のスロットの操作。範囲の判定をして、範囲の外では取得を止め（inner を止める）、
 * 範囲に入ったら、鳴らすべき位置から再生をやり直す（protocol.md の 9・10）。
 *
 * <p>範囲に入る前から取得を始めて、音が出る範囲に着いたときには鳴らせるようにする（START_MARGIN）。
 * 出入りの境目で行ったり来たりしないよう、止めるのは少し外に出てから（STOP_MARGIN）。
 * {@link #tick} を一定間隔で呼ぶこと。
 */
public final class RadioSlotControl implements SlotControl {

    /** 範囲のこれだけ外から取得を始める（ブロック）。範囲の外では音量が 0 なので、聞こえない。 */
    static final double START_MARGIN = 4.0;
    /** 範囲のこれだけ外に出たら取得を止める（ブロック）。 */
    static final double STOP_MARGIN = 8.0;

    private final SlotControl inner;
    private final RadioPlace place;
    private final BooleanSupplier enabled;
    private final Runnable onDispose;

    private boolean wanted;
    private String url;
    private LongSupplier desired;
    private boolean paused;
    private long pausedPositionMs;
    private boolean active;

    public RadioSlotControl(SlotControl inner, RadioPlace place, BooleanSupplier enabled, Runnable onDispose) {
        this.inner = inner;
        this.place = place;
        this.enabled = enabled;
        this.onDispose = onDispose;
    }

    @Override
    public synchronized void place(Messages.RadioTarget target) {
        place.set(target.dimension(), target.x(), target.y(), target.z(), target.range());
    }

    @Override
    public synchronized void move(String dimension, double x, double y, double z) {
        place.move(dimension, x, y, z);
        evaluate();
    }

    @Override
    public synchronized void play(String url, LongSupplier desiredPositionMs) {
        if (active) {
            inner.stop();
            active = false;
        }
        this.url = url;
        this.desired = desiredPositionMs;
        this.wanted = true;
        this.paused = false;
        evaluate();
    }

    @Override
    public synchronized void pause(long positionMs) {
        paused = true;
        pausedPositionMs = positionMs;
        if (active) {
            inner.pause(positionMs);
        }
    }

    @Override
    public synchronized void resume() {
        paused = false;
        if (active) {
            inner.resume();
        } else {
            evaluate();
        }
    }

    @Override
    public synchronized void stop() {
        wanted = false;
        url = null;
        desired = null;
        paused = false;
        if (active) {
            inner.stop();
            active = false;
        }
    }

    @Override
    public void dispose() {
        stop();
        onDispose.run();
    }

    /** 一定間隔に呼ぶ。範囲の出入りを調べる。 */
    public synchronized void tick() {
        evaluate();
    }

    @Override
    public synchronized String stateName() {
        return inner.stateName() + (wanted && !active ? "(範囲外)" : "");
    }

    private void evaluate() {
        if (!wanted) {
            return;
        }
        boolean near = near();
        if (near && !active) {
            inner.play(url, desired);
            active = true;
            if (paused) {
                inner.pause(pausedPositionMs); // 取得中の一時停止: 止まった位置から取得して、止まったまま待つ
            }
        } else if (!near && active) {
            inner.stop();
            active = false;
        }
    }

    private boolean near() {
        if (!enabled.getAsBoolean()) {
            return false;
        }
        double d = place.distance();
        if (Double.isNaN(d)) {
            return false; // 別の次元にいる、またはワールドに入っていない
        }
        return d <= place.range() + (active ? STOP_MARGIN : START_MARGIN);
    }
}
