package com.oogata.radioplugin.net;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/** radiomod:main の送受信と、mod 導入済みプレイヤーの記録。 */
public final class NetService implements PluginMessageListener, Listener {

    private static final long START_LEAD_MS = 500;

    /** 受信したメッセージを、再生側（player/ など）へ渡す。 */
    public interface Handler {
        default void onHello(Player player) {
        }

        default void onError(Player player, Messages.Error error) {
        }
    }

    private final Plugin plugin;
    private final List<Handler> handlers = new CopyOnWriteArrayList<>();
    private final Set<UUID> modPlayers = ConcurrentHashMap.newKeySet();

    public NetService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, Messages.CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, Messages.CHANNEL, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void unregister() {
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, Messages.CHANNEL);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, Messages.CHANNEL, this);
        modPlayers.clear();
    }

    public void addHandler(Handler handler) {
        handlers.add(handler);
    }

    public boolean hasMod(Player player) {
        return modPlayers.contains(player.getUniqueId());
    }

    /** mod 導入済みのプレイヤーにだけ送る。未導入者には何も送らない。 */
    public void send(Player player, Messages message) {
        if (!hasMod(player)) {
            return;
        }
        player.sendPluginMessage(plugin, Messages.CHANNEL, Codec.encode(message));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!Messages.CHANNEL.equals(channel)) {
            return;
        }
        Messages message;
        try {
            message = Codec.decode(data);
        } catch (Codec.DecodeException e) {
            plugin.getLogger().warning(player.getName() + " から読めないメッセージ: " + e.getMessage());
            return;
        }
        if (message instanceof Messages.Hello hello) {
            onHello(player, hello);
        } else if (message instanceof Messages.Error error) {
            handlers.forEach(h -> h.onError(player, error));
        } else {
            plugin.getLogger().fine(player.getName() + " から未処理のメッセージ: " + message);
        }
    }

    private void onHello(Player player, Messages.Hello hello) {
        modPlayers.add(player.getUniqueId());
        plugin.getLogger().info("HELLO を受信: " + player.getName() + " (mod " + hello.modVersion()
                + ", protocol " + hello.protocolVersion() + ")");

        // G1 の疎通確認用。ステップ3で外す。
        send(player, new Messages.Play(Messages.Slot.PERSONAL_SLOT, 0L, "test://hello", "ping", -1L,
                System.currentTimeMillis() + START_LEAD_MS, null));
        handlers.forEach(h -> h.onHello(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        modPlayers.remove(event.getPlayer().getUniqueId());
    }
}
