package com.oogata.radioplugin.player;

import com.oogata.radioplugin.net.Messages;
import com.oogata.radioplugin.net.NetService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/** PlayerHost の Bukkit 実装。チャット・スコアボード・mod への送信を行う。 */
public final class BukkitPlayerHost implements PlayerHost {

    private final Plugin plugin;
    private final NetService net;
    private final Supplier<String> announceMode;

    public BukkitPlayerHost(Plugin plugin, NetService net, Supplier<String> announceMode) {
        this.plugin = plugin;
        this.net = net;
        this.announceMode = announceMode;
    }

    @Override
    public String nameOf(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name != null ? name : "誰か";
    }

    @Override
    public boolean isOnline(UUID id) {
        return Bukkit.getPlayer(id) != null;
    }

    @Override
    public boolean hasMod(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p != null && net.hasMod(p);
    }

    @Override
    public Collection<UUID> modPlayers() {
        List<UUID> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (net.hasMod(p)) {
                result.add(p.getUniqueId());
            }
        }
        return result;
    }

    @Override
    public void send(UUID id, Messages message) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) {
            return;
        }
        try {
            net.send(p, message);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("送れないメッセージ: " + e.getMessage());
        }
    }

    @Override
    public void tell(UUID id, String text) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            p.sendMessage(text);
        }
    }

    @Override
    public void tellButtons(UUID id, String text, List<Button> buttons) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) {
            return;
        }
        ComponentBuilder builder = new ComponentBuilder(text + " ");
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            TextComponent c = new TextComponent(b.label());
            c.setColor(i == 0 ? ChatColor.GREEN : ChatColor.RED);
            c.setBold(true);
            c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, b.command()));
            c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(b.command())));
            builder.append(c, ComponentBuilder.FormatRetention.NONE).append(" ", ComponentBuilder.FormatRetention.NONE);
        }
        p.spigot().sendMessage(builder.create());
    }

    @Override
    public void announceTrack(UUID id, String title) {
        Player p = Bukkit.getPlayer(id);
        if (p == null || !net.hasMod(p)) {
            return;
        }
        String text = "♪ " + title;
        switch (announceMode.get()) {
            case "chat" -> p.sendMessage(text);
            case "none" -> {
            }
            default -> p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
        }
    }

    @Override
    public void showScore(UUID id, String title, List<String> lines) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("radio_queue", "dummy", title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < lines.size(); i++) {
            objective.getScore(lines.get(i)).setScore(lines.size() - i); // 上から順に並ぶよう、点数を降順にする
        }
        p.setScoreboard(board);
    }

    @Override
    public void hideScore(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }
}
