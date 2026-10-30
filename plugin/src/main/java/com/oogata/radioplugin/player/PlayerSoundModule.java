package com.oogata.radioplugin.player;

import com.oogata.radioplugin.command.PsCommand;
import com.oogata.radioplugin.net.Messages;
import com.oogata.radioplugin.net.NetService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** /ps まわりの部品（再生サービス・LavaPlayer・コマンド・イベント）を組み立てて、Bukkit に登録する。 */
public final class PlayerSoundModule implements Listener, NetService.Handler {

    /** 曲の終わりの検知と SYNC の再送を進める間隔（tick）。 */
    private static final long TICK_INTERVAL = 5;

    private final PlayerSoundService service;
    private final TrackResolver resolver;
    private BukkitTask task;

    private PlayerSoundModule(PlayerSoundService service, TrackResolver resolver) {
        this.service = service;
        this.resolver = resolver;
    }

    public static PlayerSoundModule enable(JavaPlugin plugin, NetService net) {
        FileConfiguration config = plugin.getConfig();
        PlaybackEngine.Settings engine = new PlaybackEngine.Settings(
                config.getLong("start-lead-ms", 500),
                config.getLong("sync-interval-seconds", 30) * 1000,
                config.getInt("max-consecutive-errors", 3));
        TrackResolver resolver = new LavaPlayerResolver(command -> runOnMain(plugin, command), plugin.getLogger(),
                config.getInt("max-playlist-size", 100));
        BukkitPlayerHost host = new BukkitPlayerHost(plugin, net,
                () -> plugin.getConfig().getString("announce", "actionbar"));
        PlayerSoundService service = new PlayerSoundService(host, resolver, Clock.SYSTEM,
                new PlayerSoundService.Config(engine, 30_000));

        PlayerSoundModule module = new PlayerSoundModule(service, resolver);
        plugin.getServer().getPluginManager().registerEvents(module, plugin);
        net.addHandler(module);
        PsCommand command = new PsCommand(service);
        plugin.getCommand("playersound").setExecutor(command);
        plugin.getCommand("playersound").setTabCompleter(command);
        module.task = Bukkit.getScheduler().runTaskTimer(plugin, service::tick, TICK_INTERVAL, TICK_INTERVAL);
        return module;
    }

    /** /rp でも同じ URL の取得を使う。 */
    public TrackResolver resolver() {
        return resolver;
    }

    public void disable() {
        if (task != null) {
            task.cancel();
        }
        resolver.close();
    }

    private static void runOnMain(Plugin plugin, Runnable command) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, command);
        }
    }

    @Override
    public void onHello(Player player) {
        service.onHello(player.getUniqueId());
    }

    @Override
    public void onError(Player player, Messages.Error error) {
        service.onError(player.getUniqueId(), error);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.onQuit(event.getPlayer().getUniqueId());
    }
}
