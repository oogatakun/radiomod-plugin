package com.oogata.radiomod.sync;

import com.oogata.radiomod.RadioMod;
import com.oogata.radiomod.audio.AudioEngine;
import com.oogata.radiomod.audio.AudioFailure;
import com.oogata.radiomod.audio.PlaybackListener;
import com.oogata.radiomod.net.Codec;
import com.oogata.radiomod.net.Messages;
import com.oogata.radiomod.net.RadioNet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

/**
 * クライアントの同期まわりをつなぐ。時計合わせ（TIME_REQ の送信）、受信メッセージの振り分け、
 * 失敗の ERROR 送信、切断時の停止。
 */
public final class ClientSync {

    /** 接続直後の時計合わせ（回数と間隔）と、そのあとの間隔。protocol.md の「時計合わせ」5。 */
    private static final int BURST_COUNT = 8;
    private static final long BURST_INTERVAL_MS = 100;
    private static final long REGULAR_INTERVAL_MS = 30_000;

    private static final ServerClock CLOCK = new ServerClock();
    /** プレイヤーが設定する、出力機器の遅れの補正値（ms）。クライアント設定から書き込まれる。 */
    private static volatile long manualOffsetMs = 0;
    private static final SlotRouter ROUTER =
            new SlotRouter(slot -> AudioEngine.get().control(slot), CLOCK, () -> manualOffsetMs);

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "radiomod-clock");
        t.setDaemon(true);
        return t;
    });
    private static int clockGeneration;
    private static int burstSent;

    private ClientSync() {
    }

    public static long manualOffsetMs() {
        return manualOffsetMs;
    }

    public static void setManualOffsetMs(long ms) {
        manualOffsetMs = ms;
    }

    public static ServerClock clock() {
        return CLOCK;
    }

    public static SlotRouter router() {
        return ROUTER;
    }

    public static void init() {
        AudioEngine engine = AudioEngine.get();
        engine.setMasterVolume(ClientSync::minecraftMasterVolume);
        engine.setRadioListener(ClientSync::listenerPosition);
        engine.setListener(new PlaybackListener() {
            @Override
            public void onStarted(Messages.Slot slot) {
                RadioMod.LOGGER.info("再生が始まりました: {}", slot);
            }

            @Override
            public void onFinished(Messages.Slot slot) {
                RadioMod.LOGGER.info("曲を鳴らし終えました: {}", slot);
            }

            @Override
            public void onError(Messages.Slot slot, AudioFailure failure) {
                // 無音にしたスロットのことをサーバーに知らせる。サーバーがその曲をスキップする。
                RadioNet.send(new Messages.Error(slot, ROUTER.trackIdOf(slot), failure.reason(), failure.detail()));
            }
        });
    }

    /** 聞く側の位置。OpenAL のリスナーに Minecraft が入れているのと同じ、カメラの位置。ワールドに入っていなければ null。 */
    private static RadioPlace.ListenerPos listenerPosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer == null) {
            return null;
        }
        var pos = mc.gameRenderer.getMainCamera().getPosition();
        return new RadioPlace.ListenerPos(mc.level.dimension().location().toString(), pos.x, pos.y, pos.z);
    }

    private static double minecraftMasterVolume() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options == null ? 1.0 : mc.options.getSoundSourceVolume(SoundSource.MASTER);
    }

    /** サーバーからのメッセージ。t1Nanos は、受け取った瞬間の nanoTime。 */
    public static void onMessage(Messages message, long t1Nanos) {
        if (message instanceof Messages.Time t) {
            CLOCK.onResponse(t.clientNanos(), t1Nanos, t.serverNowMs());
            return;
        }
        ROUTER.handle(message);
    }

    /** サーバーに入って HELLO を送った直後。時計合わせを始める。 */
    public static void onConnected() {
        int gen;
        synchronized (ClientSync.class) {
            gen = ++clockGeneration;
            burstSent = 0;
        }
        CLOCK.reset();
        SCHEDULER.schedule(() -> requestTime(gen), BURST_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /** 切断。時計合わせをやめ、全スロットを止める。 */
    public static void onDisconnected() {
        synchronized (ClientSync.class) {
            clockGeneration++;
        }
        CLOCK.reset();
        ROUTER.stopAll();
    }

    private static void requestTime(int gen) {
        long next;
        synchronized (ClientSync.class) {
            if (gen != clockGeneration) {
                return;
            }
            // 接続の準備ができていない間は、数えずに次の機会を待つ。
            if (RadioNet.send(new Messages.TimeReq(System.nanoTime()))) {
                burstSent++;
            }
            next = burstSent < BURST_COUNT ? BURST_INTERVAL_MS : REGULAR_INTERVAL_MS;
        }
        SCHEDULER.schedule(() -> requestTime(gen), next, TimeUnit.MILLISECONDS);
    }

    /** 受信ペイロードを読む。読めなければ警告を出して null。 */
    public static Messages decode(byte[] data) {
        try {
            return Codec.decode(data);
        } catch (Codec.DecodeException e) {
            RadioMod.LOGGER.warn("読めないメッセージを無視: {}", e.getMessage());
            return null;
        }
    }
}
