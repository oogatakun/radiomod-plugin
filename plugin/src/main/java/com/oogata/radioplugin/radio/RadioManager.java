package com.oogata.radioplugin.radio;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.oogata.radioplugin.player.PlaybackEngine;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** ラジオの設置・撤去・移動・保存・復元をまとめる入口。 */
public final class RadioManager {

    private final JavaPlugin plugin;
    private final RadioStore store;
    private final RadioAppearance appearance;
    private final NamespacedKey wandKey;
    private final Map<UUID, Location> selections = new HashMap<>();
    private final Map<UUID, String> nowPlaying = new HashMap<>();
    private final Set<UUID> rebuilding = new HashSet<>();
    private final List<Consumer<Radio>> removedHooks = new ArrayList<>();
    private final List<Consumer<Radio>> movedHooks = new ArrayList<>();

    public RadioManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.store = new RadioStore(new File(plugin.getDataFolder(), "radios.yml"), plugin.getLogger());
        this.appearance = new RadioAppearance(plugin);
        this.wandKey = new NamespacedKey(plugin, "radio_wand");
    }

    public void enable() {
        store.load();
        Bukkit.getPluginManager().registerEvents(new RadioListener(this, plugin), plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::showSelections, 20L, 10L);
        // 起動時にすでに読み込まれているチャンクは、イベントが来ないので自分で確かめる
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World w : Bukkit.getWorlds()) {
                for (Chunk c : w.getLoadedChunks()) {
                    reconcile(c);
                }
            }
        });
    }

    public NamespacedKey wandKey() {
        return wandKey;
    }

    // ---- 参照 ----

    public Collection<Radio> radios() {
        return store.all();
    }

    public Radio find(String name) {
        return store.findByName(name);
    }

    public Radio find(UUID id) {
        return store.get(id);
    }

    public List<String> names() {
        return store.names();
    }

    // ---- 他のステップとの連携 ----

    /** 撤去されたラジオを受け取る（再生の停止などに使う）。 */
    public void onRemoved(Consumer<Radio> hook) {
        removedHooks.add(hook);
    }

    /** 移動したラジオを受け取る（移動後の座標が入っている）。 */
    public void onMoved(Consumer<Radio> hook) {
        movedHooks.add(hook);
    }

    /** 再生中の曲名を名前表示に出す。null か空なら表示を消し、ランプを消灯する。 */
    public void setNowPlaying(UUID radioId, String title) {
        Radio r = store.get(radioId);
        if (r == null) {
            return;
        }
        String shown = title == null || title.isEmpty() ? null : title;
        if (shown == null) {
            nowPlaying.remove(radioId);
        } else {
            nowPlaying.put(radioId, shown);
        }
        appearance.updateNowPlaying(r, shown);
    }

    // ---- 杖の選択 ----

    public void select(Player p, Location pos) {
        selections.put(p.getUniqueId(), pos);
    }

    public Location selection(Player p) {
        Location l = selections.get(p.getUniqueId());
        return l == null ? null : l.clone();
    }

    public void clearSelection(Player p) {
        selections.remove(p.getUniqueId());
    }

    private void showSelections() {
        for (Map.Entry<UUID, Location> e : selections.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            Location l = e.getValue();
            if (p == null || l.getWorld() == null || !l.getWorld().equals(p.getWorld())) {
                continue;
            }
            p.spawnParticle(Particle.VILLAGER_HAPPY, l, 8, 0.25, 0.4, 0.25, 0);
        }
    }

    // ---- keep ----

    /** 再生状態が変わった。keep のラジオだけ、radios.yml に保存する。 */
    public void playbackChanged(UUID radioId, PlaybackEngine.Snapshot snapshot) {
        Radio r = store.get(radioId);
        if (r != null && r.keep()) {
            r.setSaved(snapshot);
            try {
                store.save();
            } catch (IOException e) {
                plugin.getLogger().severe("radios.yml を保存できませんでした: " + e.getMessage());
            }
        }
    }

    /** keep を切り替える。入れたときは今の再生状態を保存し、切ったときは保存した内容を消す。 */
    public boolean toggleKeep(Radio r, PlaybackEngine.Snapshot current) {
        r.setKeep(!r.keep());
        r.setSaved(r.keep() ? current : null);
        save();
        return r.keep();
    }

    // ---- 操作 ----

    /** player が立っている側をラジオの前面に向けて設置する。 */
    public Radio create(String name, Location pos, Location facing) {
        String problem = RadioNames.validate(name);
        if (problem != null) {
            throw new RadioException(problem);
        }
        if (store.findByName(name) != null) {
            throw new RadioException("その名前のラジオはすでにあります: " + name);
        }
        requireLoaded(pos);
        int q = Orientation.quarterTurnsToward(facing.getX() - pos.getX(), facing.getZ() - pos.getZ());
        Radio r = new Radio(UUID.randomUUID(), name, pos.getWorld().getName(), pos.getX(), pos.getY(), pos.getZ(), q);
        buildInto(r);
        store.add(r);
        save();
        return r;
    }

    public void delete(Radio r) {
        // 先に再生などの後始末をしてから、見た目を消す
        for (Consumer<Radio> hook : removedHooks) {
            hook.accept(r);
        }
        appearance.remove(r);
        store.remove(r.id());
        nowPlaying.remove(r.id());
        save();
    }

    public void move(Radio r, Location pos) {
        requireLoaded(pos);
        String oldWorld = r.world();
        double ox = r.x();
        double oy = r.y();
        double oz = r.z();
        appearance.remove(r);
        r.setPosition(pos.getWorld().getName(), pos.getX(), pos.getY(), pos.getZ());
        try {
            buildInto(r);
        } catch (RadioException e) {
            // 失敗したら元の場所に戻す
            r.setPosition(oldWorld, ox, oy, oz);
            try {
                buildInto(r);
            } catch (RadioException again) {
                plugin.getLogger().warning("ラジオを元の場所に戻せませんでした: " + r.name());
            }
            throw e;
        }
        save();
        for (Consumer<Radio> hook : movedHooks) {
            hook.accept(r);
        }
    }

    public Location teleportTarget(Radio r) {
        World w = Bukkit.getWorld(r.world());
        if (w == null) {
            throw new RadioException("ワールドが見つかりません: " + r.world());
        }
        int q = r.rotation();
        return new Location(w,
                r.x() + Orientation.frontX(q) * 2.0,
                r.y(),
                r.z() + Orientation.frontZ(q) * 2.0,
                Orientation.yawLookingAtFront(q), 0f);
    }

    private void requireLoaded(Location pos) {
        if (pos.getWorld() == null || !pos.getWorld().isChunkLoaded(pos.getBlockX() >> 4, pos.getBlockZ() >> 4)) {
            throw new RadioException("その場所のチャンクが読み込まれていません");
        }
    }

    /** 見た目を作って、パーツ数と高さを r に書き込む。作れなかったら RadioException。 */
    private void buildInto(Radio r) {
        RadioAppearance.BuildResult result = appearance.build(r, nowPlaying.get(r.id()));
        if (result.parts() == 0) {
            throw new RadioException("ラジオの見た目を作れませんでした（サーバーのログを確認してください）");
        }
        r.setPartCount(result.parts());
        r.setHeight(result.height());
        plugin.getLogger().info("ラジオ「" + r.name() + "」を作りました: パーツ " + result.parts() + " 個（" + result.method() + "）");
    }

    private void save() {
        try {
            store.save();
        } catch (IOException e) {
            plugin.getLogger().severe("radios.yml を保存できませんでした: " + e.getMessage());
            throw new RadioException("radios.yml を保存できませんでした");
        }
    }

    // ---- 復元 ----

    /** チャンクの中のパーツを、保存データと照らし合わせる。持ち主のいないパーツは消し、欠けたラジオは作り直す。 */
    public void reconcile(Chunk chunk) {
        if (!chunk.isLoaded() || !chunk.isEntitiesLoaded()) {
            return;
        }
        Map<UUID, List<Display>> byRadio = new HashMap<>();
        for (Display d : appearance.partsIn(chunk)) {
            UUID id = appearance.idOf(d);
            Radio r = store.get(id);
            if (r == null || !RadioAppearance.isNear(r, d)) {
                d.remove(); // 撤去・移動で残った古いパーツ
                continue;
            }
            byRadio.computeIfAbsent(id, k -> new ArrayList<>()).add(d);
        }
        for (Radio r : store.all()) {
            if (!r.world().equals(chunk.getWorld().getName()) || r.chunkX() != chunk.getX() || r.chunkZ() != chunk.getZ()) {
                continue;
            }
            List<Display> found = byRadio.getOrDefault(r.id(), List.of());
            if (found.size() == r.partCount() + 1 || !rebuilding.add(r.id())) {
                continue;
            }
            plugin.getLogger().info("ラジオ「" + r.name() + "」のパーツが合わないので作り直します（" + found.size() + " / " + (r.partCount() + 1) + "）");
            for (Display d : found) {
                d.remove();
            }
            try {
                buildInto(r);
                save();
            } catch (RadioException e) {
                plugin.getLogger().warning("ラジオ「" + r.name() + "」を作り直せませんでした: " + e.getMessage());
            } finally {
                rebuilding.remove(r.id());
            }
        }
    }
}
