package com.oogata.radioplugin.command;

import com.oogata.radioplugin.player.RadioPlayerService;
import com.oogata.radioplugin.radio.Radio;
import com.oogata.radioplugin.radio.RadioManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /radioplayer（/rp）。ラジオ名を読んで権限を確かめ、再生サービスへ渡す。keep はここで扱う。 */
public final class RadioPlayerCommand implements TabExecutor {

    private static final String PERM = "radioplugin.rp";

    private final RadioManager radios;
    private final RadioPlayerService service;

    public RadioPlayerCommand(RadioManager radios, RadioPlayerService service) {
        this.radios = radios;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーだけが使えます。");
            return true;
        }
        if (!player.hasPermission(PERM)) {
            player.sendMessage("このコマンドを使う権限がありません。");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("使い方: /rp <ラジオ名> <play|playlist|add|pause|stop|list|skip|previous|keep>");
            return true;
        }
        Radio radio = radios.find(args[0]);
        if (radio == null) {
            player.sendMessage("そのラジオはありません: " + args[0] + "（/rd radiolist で一覧を見られます）");
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        List<String> rest = new ArrayList<>(Arrays.asList(args).subList(2, args.length));
        if (sub.equals("keep")) {
            boolean on = radios.toggleKeep(radio, service.snapshot(radio.id()));
            player.sendMessage(on ? "ラジオ「" + radio.name() + "」の再生内容を、再起動後も残します。"
                    : "ラジオ「" + radio.name() + "」の再生内容を、再起動後は残さないようにしました。");
            return true;
        }
        service.command(player.getUniqueId(), radio.id(), radio.name(), sub, rest);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> candidates = new ArrayList<>();
        if (!sender.hasPermission(PERM)) {
            return candidates;
        }
        if (args.length == 1) {
            candidates.addAll(radios.names());
        } else if (args.length == 2) {
            candidates.addAll(RadioPlayerService.SUB_COMMANDS);
        } else {
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (args.length == 3 && sub.equals("list")) {
                candidates.add("score");
            } else if (args.length == 4 && (sub.equals("play") || sub.equals("playlist"))) {
                candidates.add("loop"); // loop は URL の後ろだけ
            }
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        candidates.removeIf(c -> !c.toLowerCase(Locale.ROOT).startsWith(typed));
        return candidates;
    }
}
