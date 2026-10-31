package com.oogata.radiomod.config;

import com.oogata.radiomod.audio.AudioEngine;
import com.oogata.radiomod.net.Messages;
import com.oogata.radiomod.sync.ClientSync;
import com.oogata.radiomod.sync.SyncSettings;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * クライアント設定（config/radiomod-client.toml）。音量・ラジオ音のオン・オフ・同期の設定を持つ。
 * 値を変えたら {@link #apply()} で、再生と同期の側へ反映する。再生中でも次の更新で効く。
 */
public final class ClientConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ClientConfig INSTANCE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        INSTANCE = new ClientConfig(builder);
        SPEC = builder.build();
    }

    /** 個人・共有の再生の音量（%）。 */
    public final ForgeConfigSpec.IntValue musicVolume;
    /** ラジオの再生の音量（%）。 */
    public final ForgeConfigSpec.IntValue radioVolume;
    public final ForgeConfigSpec.BooleanValue radioEnabled;
    /** 補正を始める幅（ms）。 */
    public final ForgeConfigSpec.DoubleValue driftStartMs;
    /** 補正をやめる幅（ms）。 */
    public final ForgeConfigSpec.DoubleValue driftStopMs;
    /** 速度調整の上限（±%）。 */
    public final ForgeConfigSpec.DoubleValue maxDeviationPercent;
    /** 出力機器の遅れの手動補正（ms）。 */
    public final ForgeConfigSpec.IntValue manualOffsetMs;

    public ClientConfig(ForgeConfigSpec.Builder b) {
        b.push("volume");
        musicVolume = b.comment("音楽（/ps）の音量（%）").defineInRange("music", 100, 0, 100);
        radioVolume = b.comment("ラジオの音量（%）").defineInRange("radio", 100, 0, 100);
        radioEnabled = b.comment("false にするとラジオの音を鳴らさない").define("radioEnabled", true);
        b.pop();
        b.push("sync");
        driftStartMs = b.comment("再生速度の補正を始めるずれの幅（ms）")
                .defineInRange("driftStartMs", 40.0, 1.0, 1000.0);
        driftStopMs = b.comment("再生速度の補正をやめるずれの幅（ms）。始める幅より小さくする")
                .defineInRange("driftStopMs", 10.0, 0.0, 1000.0);
        maxDeviationPercent = b.comment("再生速度の調整の上限（±%）")
                .defineInRange("maxDeviationPercent", 2.0, 0.0, 10.0);
        manualOffsetMs = b.comment("出力機器の遅れの手動補正（ms）。音が遅れて聞こえるなら正の値")
                .defineInRange("manualOffsetMs", 0, -2000, 2000);
        b.pop();
    }

    /** 設定の値を、再生と同期の側へ反映する。 */
    public void apply() {
        AudioEngine engine = AudioEngine.get();
        double music = musicVolume.get() / 100.0;
        engine.setVolume(Messages.Slot.PERSONAL, music);
        engine.setVolume(Messages.Slot.SHARED, music);
        engine.setVolume(Messages.Slot.RADIO, radioEnabled.get() ? radioVolume.get() / 100.0 : 0);

        double start = driftStartMs.get();
        // 始める幅より小さい幅でやめる。逆なら、補正が止まらなくなる。
        double stop = Math.min(driftStopMs.get(), start);
        SyncSettings.set(start, stop, maxDeviationPercent.get() / 100.0);
        ClientSync.setManualOffsetMs(manualOffsetMs.get());
    }
}
