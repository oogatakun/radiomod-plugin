package com.oogata.radioplugin.radio;

import com.oogata.radioplugin.command.RadioPlayerCommand;
import com.oogata.radioplugin.net.Messages;
import com.oogata.radioplugin.net.NetService;
import com.oogata.radioplugin.player.BukkitPlayerHost;
import com.oogata.radioplugin.player.Clock;
import com.oogata.radioplugin.player.PlaybackEngine;
import com.oogata.radioplugin.player.RadioPlayerService;
import com.oogata.radioplugin.player.TrackResolver;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** /rp まわりの部品を組み立てて Bukkit に登録する。ラジオ管理（RadioManager）と再生（RadioPlayerService）をつなぐ。 */
public final class RadioPlayerModule implements RadioPlayerService.Radios, NetService.Handler {

    private static final long TICK_INTERVAL = 5;

    private final JavaPlugin plugin;
    private final RadioManager manager;
    private RadioPlayerService service;
    private BukkitTask task;

    private RadioPlayerModule(JavaPlugin plugin, RadioManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public static RadioPlayerModule enable(JavaPlugin plugin, NetService net, RadioManager manager,
                                           TrackResolver resolver) {
        RadioPlayerModule module = new RadioPlayerModule(plugin, manager);
        BukkitPlayerHost host = new BukkitPlayerHost(plugin, net,
                () -> plugin.getConfig().getString("announce", "actionbar"));
        PlaybackEngine.Settings engine = new PlaybackEngine.Settings(
                plugin.getConfig().getLong("start-lead-ms", 500),
                plugin.getConfig().getLong("sync-interval-seconds", 30) * 1000,
                plugin.getConfig().getInt("max-consecutive-errors", 3));
        module.service = new RadioPlayerService(host, resolver, Clock.SYSTEM, engine, module);

        net.addHandler(module);
        manager.onRemoved(r -> module.service.onRadioRemoved(r.id()));
        manager.onMoved(r -> {
            Messages.RadioTarget t = module.target(r.id());
            if (t != null) {
                module.service.onRadioMoved(r.id(), t.dimension(), t.x(), t.y(), t.z());
            }
        });
        RadioPlayerCommand command = new RadioPlayerCommand(manager, module.service);
        plugin.getCommand("radioplayer").setExecutor(command);
        plugin.getCommand("radioplayer").setTabCompleter(command);
        module.task = Bukkit.getScheduler().runTaskTimer(plugin, module.service::tick, TICK_INTERVAL, TICK_INTERVAL);

        // keep のラジオは、起動後に今の曲の頭から再生を再開する
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Radio r : manager.radios()) {
                if (r.keep() && r.saved() != null) {
                    module.service.restore(r.id(), r.saved());
                }
            }
        });
        return module;
    }

    public void disable() {
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public Messages.RadioTarget target(UUID radioId) {
        Radio r = manager.find(radioId);
        World world = r == null ? null : Bukkit.getWorld(r.world());
        if (world == null) {
            return null;
        }
        float range = (float) plugin.getConfig().getDouble("radio-range", 24);
        // 音の出どころは、設置位置の少し上（モデルの中ほど）に置く
        return new Messages.RadioTarget(dimensionOf(world), r.x(), r.y() + 0.5, r.z(), range);
    }

    /**
     * mod が見ている次元の名前(minecraft:overworld など)にそろえる。World#getKey はワールドのフォルダ名に
     * 由来することがあり、mod 側の次元名と一致しないため、標準の3つは環境から決める。
     */
    public static String dimensionOf(World world) {
        return switch (world.getEnvironment()) {
            case NORMAL -> "minecraft:overworld";
            case NETHER -> "minecraft:the_nether";
            case THE_END -> "minecraft:the_end";
            default -> world.getKey().toString();
        };
    }

    @Override
    public String name(UUID radioId) {
        Radio r = manager.find(radioId);
        return r == null ? "" : r.name();
    }

    @Override
    public void onControl(Player player, Messages.Control control) {
        if (control.slot().slotType() == Messages.Slot.RADIO && player.hasPermission("radioplugin.rp")) {
            service.control(player.getUniqueId(), control);
        }
    }

    @Override
    public void setNowPlaying(UUID radioId, String title) {
        manager.setNowPlaying(radioId, title);
    }

    @Override
    public void changed(UUID radioId, PlaybackEngine.Snapshot snapshot) {
        manager.playbackChanged(radioId, snapshot);
    }

    @Override
    public void onHello(Player player) {
        service.onHello(player.getUniqueId());
    }

    @Override
    public void onError(Player player, Messages.Error error) {
        service.onError(error);
    }
}
