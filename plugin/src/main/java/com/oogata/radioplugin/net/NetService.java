package com.oogata.radioplugin.net;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/** radiomod:main の送受信と、mod 導入済みプレイヤーの記録。 */
public final class NetService implements PluginMessageListener, Listener {

    /** 受信したメッセージを、再生側（player/ など）へ渡す。 */
    public interface Handler {
        default void onHello(Player player) {
        }

        default void onError(Player player, Messages.Error error) {
        }
    }

    private final Plugin plugin;
    private final List<Handler> handlers = new CopyOnWriteArrayList<>();
    private final ProtocolHandler protocol;

    public NetService(Plugin plugin) {
        this.plugin = plugin;
        this.protocol = new ProtocolHandler(
                (id, message) -> {
                    Player p = plugin.getServer().getPlayer(id);
                    if (p != null) {
                        p.sendPluginMessage(plugin, Messages.CHANNEL, Codec.encode(message));
                    }
                },
                System::currentTimeMillis, plugin.getLogger()::info, plugin.getLogger()::warning,
                new ProtocolHandler.Events() {
                    @Override
                    public void hello(UUID id) {
                        Player p = plugin.getServer().getPlayer(id);
                        if (p != null) {
                            handlers.forEach(h -> h.onHello(p));
                        }
                    }

                    @Override
                    public void error(UUID id, Messages.Error error) {
                        Player p = plugin.getServer().getPlayer(id);
                        if (p != null) {
                            handlers.forEach(h -> h.onError(p, error));
                        }
                    }
                });
    }

    public void register() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, Messages.CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, Messages.CHANNEL, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void unregister() {
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, Messages.CHANNEL);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, Messages.CHANNEL, this);
        protocol.clear();
    }

    public void addHandler(Handler handler) {
        handlers.add(handler);
    }

    public boolean hasMod(Player player) {
        return protocol.hasMod(player.getUniqueId());
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
        protocol.onMessage(player.getUniqueId(), player.getName(), message);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        protocol.remove(event.getPlayer().getUniqueId());
    }
}
