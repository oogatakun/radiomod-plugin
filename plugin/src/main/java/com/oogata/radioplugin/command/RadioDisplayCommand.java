package com.oogata.radioplugin.command;

import com.oogata.radioplugin.radio.Radio;
import com.oogata.radioplugin.radio.RadioException;
import com.oogata.radioplugin.radio.RadioListener;
import com.oogata.radioplugin.radio.RadioManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /radiodisplay（/rd）。ラジオの設置・管理。 */
public final class RadioDisplayCommand implements CommandExecutor, TabCompleter {

    private static final String MANAGE = "radioplugin.rd.manage";
    private static final String TP = "radioplugin.rd.tp";
    private static final String LIST = "radioplugin.rd.list";
    private static final String USAGE = "使い方: /rd <wand|create|delete|move|tp|radiolist> [ラジオ名]";

    private final RadioManager radios;

    public RadioDisplayCommand(RadioManager radios) {
        this.radios = radios;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(USAGE);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "wand" -> wand(sender);
                case "create" -> create(sender, args);
                case "delete" -> delete(sender, args);
                case "move" -> move(sender, args);
                case "tp" -> tp(sender, args);
                case "radiolist" -> radiolist(sender);
                default -> sender.sendMessage(USAGE);
            }
        } catch (RadioException e) {
            sender.sendMessage(e.getMessage());
        }
        return true;
    }

    private boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage("このコマンドを使う権限がありません");
        return false;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player p) {
            return p;
        }
        sender.sendMessage("このコマンドはゲーム内のプレイヤーだけが使えます");
        return null;
    }

    private Radio requireRadio(CommandSender sender, String[] args, String usage) {
        if (args.length < 2) {
            sender.sendMessage("使い方: " + usage);
            return null;
        }
        Radio r = radios.find(args[1]);
        if (r == null) {
            sender.sendMessage("そのラジオはありません: " + args[1] + "（/rd radiolist で一覧を見られます）");
        }
        return r;
    }

    private void wand(CommandSender sender) {
        if (!allowed(sender, MANAGE)) {
            return;
        }
        Player p = player(sender);
        if (p == null) {
            return;
        }
        p.getInventory().addItem(RadioListener.createWand(radios));
        p.sendMessage("ラジオ設置の杖を渡しました。ブロックを右クリックして場所を選んでください");
    }

    private void create(CommandSender sender, String[] args) {
        if (!allowed(sender, MANAGE)) {
            return;
        }
        Player p = player(sender);
        if (p == null) {
            return;
        }
        if (args.length < 2) {
            p.sendMessage("使い方: /rd create <名前>");
            return;
        }
        Location pos = radios.selection(p);
        if (pos == null) {
            p.sendMessage("杖で場所を選んでください");
            return;
        }
        Radio r = radios.create(args[1], pos, p.getLocation());
        radios.clearSelection(p);
        p.sendMessage("ラジオ「" + r.name() + "」を設置しました");
    }

    private void delete(CommandSender sender, String[] args) {
        if (!allowed(sender, MANAGE)) {
            return;
        }
        Radio r = requireRadio(sender, args, "/rd delete <名前>");
        if (r == null) {
            return;
        }
        radios.delete(r);
        sender.sendMessage("ラジオ「" + r.name() + "」を撤去しました");
    }

    private void move(CommandSender sender, String[] args) {
        if (!allowed(sender, MANAGE)) {
            return;
        }
        Player p = player(sender);
        if (p == null) {
            return;
        }
        Radio r = requireRadio(sender, args, "/rd move <名前>");
        if (r == null) {
            return;
        }
        Location pos = radios.selection(p);
        if (pos == null) {
            p.sendMessage("杖で場所を選んでください");
            return;
        }
        radios.move(r, pos);
        radios.clearSelection(p);
        p.sendMessage("ラジオ「" + r.name() + "」を移動しました");
    }

    private void tp(CommandSender sender, String[] args) {
        if (!allowed(sender, TP)) {
            return;
        }
        Player p = player(sender);
        if (p == null) {
            return;
        }
        Radio r = requireRadio(sender, args, "/rd tp <名前>");
        if (r == null) {
            return;
        }
        p.teleport(radios.teleportTarget(r));
        p.sendMessage("ラジオ「" + r.name() + "」の前にテレポートしました");
    }

    private void radiolist(CommandSender sender) {
        if (!allowed(sender, LIST)) {
            return;
        }
        if (radios.radios().isEmpty()) {
            sender.sendMessage("ラジオはまだありません");
            return;
        }
        sender.sendMessage("ラジオ一覧（" + radios.radios().size() + "台）");
        boolean canTp = sender.hasPermission(TP) && sender instanceof Player;
        for (Radio r : radios.radios()) {
            TextComponent line = new TextComponent("- ");
            TextComponent name = new TextComponent(r.name());
            if (canTp) {
                name.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rd tp " + r.name()));
                name.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("クリックでテレポート")));
                name.setUnderlined(true);
            }
            line.addExtra(name);
            line.addExtra(new ComponentBuilder(String.format("  %s (%d, %d, %d)",
                    r.world(), (int) Math.floor(r.x()), (int) Math.floor(r.y()), (int) Math.floor(r.z()))).create()[0]);
            sender.spigot().sendMessage(line);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission(MANAGE)) {
                out.addAll(List.of("wand", "create", "delete", "move"));
            }
            if (sender.hasPermission(TP)) {
                out.add("tp");
            }
            if (sender.hasPermission(LIST)) {
                out.add("radiolist");
            }
            return filter(out, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            boolean names = (sub.equals("delete") || sub.equals("move")) && sender.hasPermission(MANAGE)
                    || sub.equals("tp") && sender.hasPermission(TP);
            if (names) {
                return filter(radios.names(), args[1]);
            }
        }
        return out;
    }

    private static List<String> filter(List<String> candidates, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String c : candidates) {
            if (c.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(c);
            }
        }
        return out;
    }
}
