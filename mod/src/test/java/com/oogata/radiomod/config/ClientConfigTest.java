package com.oogata.radiomod.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.oogata.radiomod.audio.AudioEngine;
import com.oogata.radiomod.net.Messages;
import com.oogata.radiomod.sync.DriftCorrector;
import com.oogata.radiomod.sync.SyncSettings;
import java.nio.file.Path;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientConfigTest {

    private static ClientConfig newConfig(ForgeConfigSpec[] specOut) {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ClientConfig c = new ClientConfig(b);
        specOut[0] = b.build();
        return c;
    }

    private static CommentedFileConfig open(Path file, ForgeConfigSpec spec) {
        CommentedFileConfig fc = CommentedFileConfig.builder(file).writingMode(WritingMode.REPLACE).sync().build();
        fc.load();
        spec.setConfig(fc);
        return fc;
    }

    @Test
    void 既定値() {
        ForgeConfigSpec[] spec = new ForgeConfigSpec[1];
        ClientConfig c = newConfig(spec);
        spec[0].setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
        assertEquals(100, c.musicVolume.get());
        assertEquals(100, c.radioVolume.get());
        assertTrue(c.radioEnabled.get());
        assertEquals(40.0, c.driftStartMs.get());
        assertEquals(10.0, c.driftStopMs.get());
        assertEquals(2.0, c.maxDeviationPercent.get());
        assertEquals(0, c.manualOffsetMs.get());
    }

    @Test
    void 保存して読み直すと値が残る(@TempDir Path dir) {
        Path file = dir.resolve("radiomod-client.toml");
        ForgeConfigSpec[] spec = new ForgeConfigSpec[1];
        ClientConfig c = newConfig(spec);
        CommentedFileConfig fc = open(file, spec[0]);
        c.musicVolume.set(35);
        c.radioVolume.set(60);
        c.radioEnabled.set(false);
        c.driftStartMs.set(55.0);
        c.driftStopMs.set(8.0);
        c.maxDeviationPercent.set(1.5);
        c.manualOffsetMs.set(-120);
        spec[0].save();
        fc.close();

        ForgeConfigSpec[] spec2 = new ForgeConfigSpec[1];
        ClientConfig c2 = newConfig(spec2);
        CommentedFileConfig fc2 = open(file, spec2[0]);
        assertTrue(spec2[0].isCorrect(fc2));
        assertEquals(35, c2.musicVolume.get());
        assertEquals(60, c2.radioVolume.get());
        assertFalse(c2.radioEnabled.get());
        assertEquals(55.0, c2.driftStartMs.get());
        assertEquals(8.0, c2.driftStopMs.get());
        assertEquals(1.5, c2.maxDeviationPercent.get());
        assertEquals(-120, c2.manualOffsetMs.get());
        fc2.close();
    }

    @Test
    void 範囲外の値は範囲の端に直される(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("radiomod-client.toml");
        java.nio.file.Files.writeString(file, "[volume]\nmusic = 250\nradio = -5\n");
        ForgeConfigSpec[] spec = new ForgeConfigSpec[1];
        ClientConfig c = newConfig(spec);
        CommentedFileConfig fc = open(file, spec[0]);
        spec[0].correct(fc);
        spec[0].afterReload();
        assertEquals(100, c.musicVolume.get());
        assertEquals(0, c.radioVolume.get());
        fc.close();
    }

    @Test
    void 反映すると音量と同期の設定が変わる() {
        ForgeConfigSpec[] spec = new ForgeConfigSpec[1];
        ClientConfig c = newConfig(spec);
        spec[0].setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
        c.musicVolume.set(30);
        c.radioVolume.set(70);
        c.driftStartMs.set(80.0);
        c.driftStopMs.set(20.0);
        c.maxDeviationPercent.set(1.0);
        c.apply();
        AudioEngine e = AudioEngine.get();
        assertEquals(0.3, e.volume(Messages.Slot.PERSONAL), 1e-9);
        assertEquals(0.3, e.volume(Messages.Slot.SHARED), 1e-9);
        assertEquals(0.7, e.volume(Messages.Slot.RADIO), 1e-9);

        // すでにある DriftCorrector にも、次の更新で効く。
        DriftCorrector d = new DriftCorrector();
        assertEquals(0, d.update(60));
        assertEquals(0.01, d.update(5000), 1e-12);

        c.radioEnabled.set(false);
        c.apply();
        assertEquals(0, e.volume(Messages.Slot.RADIO));
        SyncSettings.set(DriftCorrector.START_MS, DriftCorrector.STOP_MS, DriftCorrector.MAX_DEVIATION);
    }
}
