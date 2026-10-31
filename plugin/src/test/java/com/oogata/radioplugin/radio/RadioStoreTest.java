package com.oogata.radioplugin.radio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RadioStoreTest {

    @TempDir
    Path dir;

    private RadioStore store(File f) {
        return new RadioStore(f, Logger.getAnonymousLogger());
    }

    @Test
    void 保存して読み込むと同じ内容になる() throws IOException {
        File f = dir.resolve("radios.yml").toFile();
        RadioStore a = store(f);
        Radio r = new Radio(UUID.randomUUID(), "ロビー", "world", 10.5, 64.0, -3.5, 3);
        r.setKeep(true);
        r.setPartCount(604);
        r.setHeight(1.25);
        a.add(r);
        a.save();

        RadioStore b = store(f);
        b.load();
        Radio got = b.get(r.id());
        assertNotNull(got);
        assertEquals("ロビー", got.name());
        assertEquals("world", got.world());
        assertEquals(10.5, got.x());
        assertEquals(64.0, got.y());
        assertEquals(-3.5, got.z());
        assertEquals(3, got.rotation());
        assertTrue(got.keep());
        assertEquals(604, got.partCount());
        assertEquals(1.25, got.height());
    }

    @Test
    void 名前は大文字小文字を区別せず探せる() {
        RadioStore s = store(dir.resolve("radios.yml").toFile());
        Radio r = new Radio(UUID.randomUUID(), "Lobby", "world", 0, 0, 0, 0);
        s.add(r);
        assertEquals(r, s.findByName("lobby"));
        assertNull(s.findByName("other"));
    }

    @Test
    void 削除したラジオはファイルからも消える() throws IOException {
        File f = dir.resolve("radios.yml").toFile();
        RadioStore a = store(f);
        Radio x = new Radio(UUID.randomUUID(), "x", "world", 0, 0, 0, 0);
        Radio y = new Radio(UUID.randomUUID(), "y", "world", 1, 0, 0, 0);
        a.add(x);
        a.add(y);
        a.save();
        a.remove(x.id());
        a.save();

        RadioStore b = store(f);
        b.load();
        assertEquals(1, b.all().size());
        assertNull(b.get(x.id()));
        assertNotNull(b.get(y.id()));
    }

    @Test
    void 扱わないキーは保存し直しても残る() throws IOException {
        File f = dir.resolve("radios.yml").toFile();
        UUID id = UUID.randomUUID();
        Files.writeString(f.toPath(),
                "radios:\n  " + id + ":\n    name: a\n    world: w\n    x: 1.0\n    y: 2.0\n    z: 3.0\n"
                        + "    queue:\n      - http://example.com/a.mp3\n");
        RadioStore s = store(f);
        s.load();
        s.get(id).setKeep(true);
        s.save();

        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        assertEquals("http://example.com/a.mp3", y.getStringList("radios." + id + ".queue").get(0));
        assertTrue(y.getBoolean("radios." + id + ".keep"));
    }

    @Test
    void 不正なエントリは読み飛ばす() throws IOException {
        File f = dir.resolve("radios.yml").toFile();
        Files.writeString(f.toPath(), "radios:\n  not-a-uuid:\n    name: a\n    world: w\n");
        RadioStore s = store(f);
        s.load();
        assertTrue(s.all().isEmpty());
    }
}
