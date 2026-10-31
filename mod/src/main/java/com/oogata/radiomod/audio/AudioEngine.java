package com.oogata.radiomod.audio;

import com.oogata.radiomod.RadioMod;
import com.oogata.radiomod.net.Messages;
import com.oogata.radiomod.sync.RadioPlace;
import com.oogata.radiomod.sync.RadioSlotControl;
import com.oogata.radiomod.sync.SlotControl;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;

/**
 * スロット（個人・共有・ラジオごと）の再生を束ねる。スロットは互いに独立して鳴る。
 *
 * <p>音量は、スロットの種類（個人・共有・ラジオ）ごとに値を持つ。値は 0〜1。
 * 最終的な音量は、この値に Minecraft のマスター音量（{@code masterVolume}）を掛けたもの。
 */
public final class AudioEngine implements AutoCloseable {

    private static final AudioEngine INSTANCE = new AudioEngine();

    private static final long RANGE_CHECK_MS = 200;

    private final Map<Messages.Slot, SlotPlayer> players = new ConcurrentHashMap<>();
    private final Map<Messages.Slot, RadioSlotControl> radios = new ConcurrentHashMap<>();
    private volatile RadioPlace.Listener radioListener = () -> null;
    private ScheduledExecutorService ticker;
    private final double[] volumes = {1.0, 1.0, 1.0}; // 添字は Messages.Slot の slotType
    private volatile DoubleSupplier masterVolume = () -> 1.0;
    private volatile PlaybackListener listener = new PlaybackListener() {
    };

    private AudioEngine() {
    }

    public static AudioEngine get() {
        return INSTANCE;
    }

    /** そのスロットの再生を返す。なければ作る。 */
    public SlotPlayer slot(Messages.Slot slot) {
        if (slot.slotType() == Messages.Slot.RADIO) {
            control(slot);
            SlotPlayer p = players.get(slot);
            if (p != null) {
                return p;
            }
        }
        return players.computeIfAbsent(slot, s -> new SlotPlayer(s, () -> gainFor(s.slotType()), new Forwarder()));
    }

    /**
     * そのスロットの操作を返す。個人・共有は再生そのもの。ラジオは、範囲の判定をして
     * 範囲の外で取得を止める {@link RadioSlotControl}（中身は3Dにする再生）。
     */
    public SlotControl control(Messages.Slot slot) {
        if (slot.slotType() != Messages.Slot.RADIO) {
            return slot(slot);
        }
        startRadioTicker();
        return radios.computeIfAbsent(slot, s -> {
            RadioPlace place = new RadioPlace(() -> radioListener.position());
            SlotPlayer player = new SlotPlayer(s, () -> gainFor(s.slotType()), new Forwarder(), place);
            players.put(s, player);
            return new RadioSlotControl(player, place, () -> volumes[Messages.Slot.RADIO] > 0, () -> disposeRadio(s),
                    line -> RadioMod.LOGGER.info("[{}] {}", s.radioId(), line));
        });
    }

    /** 聞く側（カメラ）の次元と座標の取り方。クライアントの起動時に入れる。 */
    public void setRadioListener(RadioPlace.Listener listener) {
        this.radioListener = listener == null ? () -> null : listener;
    }

    private void disposeRadio(Messages.Slot slot) {
        radios.remove(slot);
        SlotPlayer p = players.remove(slot);
        if (p != null) {
            p.close();
        }
    }

    private synchronized void startRadioTicker() {
        if (ticker != null) {
            return;
        }
        ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "radiomod-radio-range");
            t.setDaemon(true);
            return t;
        });
        ticker.scheduleWithFixedDelay(() -> {
            for (RadioSlotControl c : radios.values()) {
                try {
                    c.tick();
                } catch (RuntimeException e) {
                    RadioMod.LOGGER.warn("ラジオの範囲の判定に失敗: {}", e.toString());
                }
            }
        }, RANGE_CHECK_MS, RANGE_CHECK_MS, TimeUnit.MILLISECONDS);
    }

    /** いま作られているスロットの再生。デバッグ表示用。 */
    public Map<Messages.Slot, SlotPlayer> slots() {
        return Map.copyOf(players);
    }

    public void setListener(PlaybackListener listener) {
        this.listener = listener == null ? new PlaybackListener() {
        } : listener;
    }

    public void setMasterVolume(DoubleSupplier masterVolume) {
        this.masterVolume = masterVolume;
    }

    /** スロットの種類（Messages.Slot.PERSONAL / SHARED / RADIO）ごとの音量。0〜1 に丸める。 */
    public void setVolume(byte slotType, double volume) {
        volumes[slotType] = Math.max(0, Math.min(1, volume));
    }

    public double volume(byte slotType) {
        return volumes[slotType];
    }

    private double gainFor(byte slotType) {
        return volumes[slotType] * masterVolume.getAsDouble();
    }

    /** 全スロットを止める。 */
    public void stopAll() {
        radios.values().forEach(RadioSlotControl::stop);
        players.values().forEach(SlotPlayer::stop);
    }

    @Override
    public void close() {
        players.values().forEach(SlotPlayer::close);
        players.clear();
    }

    private final class Forwarder implements PlaybackListener {
        @Override
        public void onStarted(Messages.Slot slot) {
            listener.onStarted(slot);
        }

        @Override
        public void onFinished(Messages.Slot slot) {
            listener.onFinished(slot);
        }

        @Override
        public void onError(Messages.Slot slot, AudioFailure failure) {
            listener.onError(slot, failure);
        }
    }
}
