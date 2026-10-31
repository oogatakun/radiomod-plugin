package com.oogata.radiomod.sync;

import com.oogata.radiomod.RadioMod;
import com.oogata.radiomod.net.Messages;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * サーバーから届いた PLAY・PAUSE・RESUME・STOP・SYNC を、スロットごとの再生の操作に結びつける。
 *
 * <p>各スロットの originMs（曲の頭に当たるサーバー時刻）を持ち、
 * 「鳴らすべき位置 = いまのサーバー時刻 - originMs + 手動の補正値」を再生側へ渡す。
 */
public final class SlotRouter {

    private static final class SlotState {
        final long trackId;
        final boolean live;
        volatile long originMs;

        SlotState(long trackId, boolean live, long originMs) {
            this.trackId = trackId;
            this.live = live;
            this.originMs = originMs;
        }
    }

    private final Function<Messages.Slot, SlotControl> controls;
    private final ServerClock clock;
    private final LongSupplier manualOffsetMs;
    private final Map<Messages.Slot, SlotState> states = new ConcurrentHashMap<>();

    public SlotRouter(Function<Messages.Slot, SlotControl> controls, ServerClock clock, LongSupplier manualOffsetMs) {
        this.controls = controls;
        this.clock = clock;
        this.manualOffsetMs = manualOffsetMs;
    }

    /** TIME 以外のサーバーからのメッセージを処理する。 */
    public synchronized void handle(Messages message) {
        logReceived(message);
        if (message instanceof Messages.Play m) {
            play(m);
        } else if (message instanceof Messages.Pause m) {
            if (states.containsKey(m.slot())) {
                controls.apply(m.slot()).pause(m.positionMs());
            }
        } else if (message instanceof Messages.Resume m) {
            SlotState s = states.get(m.slot());
            if (s != null) {
                s.originMs = m.originMs();
                controls.apply(m.slot()).resume();
            }
        } else if (message instanceof Messages.Stop m) {
            if (states.remove(m.slot()) != null) {
                SlotControl control = controls.apply(m.slot());
                control.stop();
                if (m.slot().slotType() == Messages.Slot.RADIO) {
                    control.dispose(); // 撤去されたラジオの再生を片付ける
                }
            }
        } else if (message instanceof Messages.Sync m) {
            SlotState s = states.get(m.slot());
            if (s != null) {
                s.originMs = m.originMs(); // 位置は飛ばさず、補正の目標だけを変える
            }
        } else if (message instanceof Messages.Move m) {
            if (states.containsKey(m.slot())) {
                controls.apply(m.slot()).move(m.dimension(), m.x(), m.y(), m.z());
            }
        } else {
            RadioMod.LOGGER.warn("想定外のメッセージを無視: {}", message);
        }
    }

    /** 受け取ったメッセージを1行で記録する（TIME は量が多いので出さない）。 */
    private void logReceived(Messages message) {
        Messages.Slot slot;
        long origin = Long.MIN_VALUE;
        String extra = "";
        if (message instanceof Messages.Play m) {
            slot = m.slot();
            origin = m.originMs();
            extra = " trackId=" + m.trackId() + " 長さ=" + m.durationMs() + "ms url=" + m.url();
        } else if (message instanceof Messages.Pause m) {
            slot = m.slot();
            extra = " positionMs=" + m.positionMs();
        } else if (message instanceof Messages.Resume m) {
            slot = m.slot();
            origin = m.originMs();
        } else if (message instanceof Messages.Stop m) {
            slot = m.slot();
        } else if (message instanceof Messages.Sync m) {
            slot = m.slot();
            origin = m.originMs();
        } else if (message instanceof Messages.Move m) {
            slot = m.slot();
            extra = " " + m.dimension() + " " + m.x() + "," + m.y() + "," + m.z();
        } else {
            return;
        }
        long now = clock.nowServerMs();
        String nowText = now == ServerClock.UNSYNCED ? "未同期" : Long.toString(now);
        String desired = now == ServerClock.UNSYNCED || origin == Long.MIN_VALUE
                ? "-" : Long.toString(now - origin + manualOffsetMs.getAsLong());
        SlotControl control = states.containsKey(slot) ? controls.apply(slot) : null;
        RadioMod.LOGGER.info("受信 {} slot={} originMs={} サーバー時刻(推定)={} 鳴らすべき位置={}ms 状態={}{}",
                message.getClass().getSimpleName().toUpperCase(java.util.Locale.ROOT), slot,
                origin == Long.MIN_VALUE ? "-" : origin, nowText, desired,
                control == null ? "-" : control.stateName(), extra);
    }

    private void play(Messages.Play m) {
        boolean live = m.durationMs() < 0;
        SlotState state = new SlotState(m.trackId(), live, m.originMs());
        states.put(m.slot(), state);
        // ライブは位置合わせをしない（protocol.md 11）。
        SlotControl control = controls.apply(m.slot());
        if (m.radio() != null) {
            control.place(m.radio());
        }
        control.play(m.url(), live ? null : desired(state));
    }

    private LongSupplier desired(SlotState state) {
        return () -> {
            long now = clock.nowServerMs();
            if (now == ServerClock.UNSYNCED) {
                return -1; // 時計が合うまで待つ
            }
            return now - state.originMs + manualOffsetMs.getAsLong();
        };
    }

    /** そのスロットの、いま再生中の曲の trackId。なければ -1。ERROR に載せる。 */
    public long trackIdOf(Messages.Slot slot) {
        SlotState s = states.get(slot);
        return s == null ? -1 : s.trackId;
    }

    /** そのスロットの originMs。なければ null。デバッグ表示用。 */
    public Long originOf(Messages.Slot slot) {
        SlotState s = states.get(slot);
        return s == null ? null : s.originMs;
    }

    /** 切断したとき。全スロットを止めて、状態を捨てる。 */
    public synchronized void stopAll() {
        for (Messages.Slot slot : states.keySet()) {
            SlotControl control = controls.apply(slot);
            control.stop();
            if (slot.slotType() == Messages.Slot.RADIO) {
                control.dispose();
            }
        }
        states.clear();
    }
}
