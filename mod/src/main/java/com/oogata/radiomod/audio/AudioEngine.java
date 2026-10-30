package com.oogata.radiomod.audio;

import com.oogata.radiomod.net.Messages;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

/**
 * スロット（個人・共有・ラジオごと）の再生を束ねる。スロットは互いに独立して鳴る。
 *
 * <p>音量は、スロットの種類（個人・共有・ラジオ）ごとに値を持つ。値は 0〜1。
 * 最終的な音量は、この値に Minecraft のマスター音量（{@code masterVolume}）を掛けたもの。
 */
public final class AudioEngine implements AutoCloseable {

    private static final AudioEngine INSTANCE = new AudioEngine();

    private final Map<Messages.Slot, SlotPlayer> players = new ConcurrentHashMap<>();
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
        return players.computeIfAbsent(slot, s -> new SlotPlayer(s, () -> gainFor(s.slotType()), new Forwarder()));
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
