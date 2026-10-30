package com.oogata.radioplugin.radio;

import java.io.File;
import java.io.IOException;
import com.oogata.radioplugin.player.PlaybackEngine;
import com.oogata.radioplugin.player.Track;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * radios.yml の読み書きとラジオ一覧。読み込んだ YAML をそのまま持ち、ここで扱わないキー
 * （ほかのステップが足す再生内容など）は保存時にも残す。
 */
public final class RadioStore {

    private static final String ROOT = "radios";

    private final File file;
    private final Logger log;
    private YamlConfiguration yaml = new YamlConfiguration();
    private final Map<UUID, Radio> radios = new LinkedHashMap<>();

    public RadioStore(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public void load() {
        radios.clear();
        yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        ConfigurationSection section = yaml.getConfigurationSection(ROOT);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                log.warning("radios.yml: ラジオIDが不正なので読み飛ばします: " + key);
                continue;
            }
            String name = s == null ? null : s.getString("name");
            String world = s == null ? null : s.getString("world");
            if (name == null || world == null) {
                log.warning("radios.yml: 名前かワールドがないので読み飛ばします: " + key);
                continue;
            }
            Radio r = new Radio(id, name, world,
                    s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), s.getInt("rotation"));
            r.setKeep(s.getBoolean("keep"));
            r.setPartCount(s.getInt("parts"));
            r.setHeight(s.getDouble("height", 1.0));
            if (r.keep()) {
                r.setSaved(readSaved(s.getConfigurationSection("playback")));
            }
            radios.put(id, r);
        }
    }

    public void save() throws IOException {
        ConfigurationSection section = yaml.getConfigurationSection(ROOT);
        if (section == null) {
            section = yaml.createSection(ROOT);
        }
        for (String key : new ArrayList<>(section.getKeys(false))) {
            boolean known = false;
            try {
                known = radios.containsKey(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
                // 読み飛ばした不正なキーは、そのまま残す
                known = true;
            }
            if (!known) {
                section.set(key, null);
            }
        }
        for (Radio r : radios.values()) {
            ConfigurationSection s = section.getConfigurationSection(r.id().toString());
            if (s == null) {
                s = section.createSection(r.id().toString());
            }
            s.set("name", r.name());
            s.set("world", r.world());
            s.set("x", r.x());
            s.set("y", r.y());
            s.set("z", r.z());
            s.set("rotation", r.rotation());
            s.set("keep", r.keep());
            s.set("parts", r.partCount());
            s.set("height", r.height());
            writeSaved(s, r.keep() ? r.saved() : null);
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("フォルダを作れません: " + parent);
        }
        yaml.save(file);
    }

    /** keep の再生内容を読む。再生位置は保存しないので、曲の頭（0）から始める。 */
    private static PlaybackEngine.Snapshot readSaved(ConfigurationSection s) {
        if (s == null) {
            return null;
        }
        List<Track> queue = new ArrayList<>();
        for (Map<?, ?> m : s.getMapList("queue")) {
            Object url = m.get("url");
            Object title = m.get("title");
            Object duration = m.get("duration");
            if (url != null) {
                queue.add(new Track(url.toString(), title == null ? url.toString() : title.toString(),
                        duration instanceof Number n ? n.longValue() : Track.LIVE));
            }
        }
        PlaybackEngine.Loop loop = PlaybackEngine.Loop.NONE;
        PlaybackEngine.State state = PlaybackEngine.State.STOPPED;
        try {
            loop = PlaybackEngine.Loop.valueOf(s.getString("loop", "NONE"));
            state = PlaybackEngine.State.valueOf(s.getString("state", "STOPPED"));
        } catch (IllegalArgumentException ignored) {
            // 読めない値は、loop なし・停止中として扱う
        }
        int index = Math.max(0, Math.min(s.getInt("index"), Math.max(0, queue.size() - 1)));
        return new PlaybackEngine.Snapshot(queue, index, loop, state, 0);
    }

    private static void writeSaved(ConfigurationSection s, PlaybackEngine.Snapshot saved) {
        s.set("playback", null);
        if (saved == null) {
            return;
        }
        ConfigurationSection p = s.createSection("playback");
        List<Map<String, Object>> queue = new ArrayList<>();
        for (Track t : saved.queue()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("url", t.url());
            m.put("title", t.title());
            m.put("duration", t.durationMs());
            queue.add(m);
        }
        p.set("queue", queue);
        p.set("index", saved.index());
        p.set("loop", saved.loop().name());
        p.set("state", saved.state().name());
    }

    public Collection<Radio> all() {
        return Collections.unmodifiableCollection(radios.values());
    }

    public Radio get(UUID id) {
        return radios.get(id);
    }

    /** 名前で探す。大文字小文字は区別しない。 */
    public Radio findByName(String name) {
        if (name == null) {
            return null;
        }
        String key = name.toLowerCase(Locale.ROOT);
        for (Radio r : radios.values()) {
            if (r.name().toLowerCase(Locale.ROOT).equals(key)) {
                return r;
            }
        }
        return null;
    }

    public void add(Radio r) {
        radios.put(r.id(), r);
    }

    public Radio remove(UUID id) {
        return radios.remove(id);
    }

    public List<String> names() {
        List<String> out = new ArrayList<>();
        for (Radio r : radios.values()) {
            out.add(r.name());
        }
        return out;
    }
}
