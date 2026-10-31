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
                controls.apply(m.slot()).stop();
            }
        } else if (message instanceof Messages.Sync m) {
            SlotState s = states.get(m.slot());
            if (s != null) {
                s.originMs = m.originMs(); // 位置は飛ばさず、補正の目標だけを変える
            }
        } else if (message instanceof Messages.Move) {
            // ラジオのスロットはステップ4で扱う
        } else {
            RadioMod.LOGGER.warn("想定外のメッセージを無視: {}", message);
        }
    }

    private void play(Messages.Play m) {
        if (m.slot().slotType() == Messages.Slot.RADIO) {
            RadioMod.LOGGER.info("ラジオスロットの PLAY は未対応のため無視: {}", m.slot());
            return;
        }
        boolean live = m.durationMs() < 0;
        SlotState state = new SlotState(m.trackId(), live, m.originMs());
        states.put(m.slot(), state);
        // ライブは位置合わせをしない（protocol.md 11）。
        controls.apply(m.slot()).play(m.url(), live ? null : desired(state));
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
            controls.apply(slot).stop();
        }
        states.clear();
    }
}
