package com.oogata.radioplugin.command;

import com.oogata.radioplugin.player.PlayerSoundService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /playersound（/ps）。引数を読んで権限を確かめ、再生サービスへ渡す。 */
public final class PsCommand implements TabExecutor {

    private static final String PERM_PS = "radioplugin.ps";
    private static final String PERM_ALL = "radioplugin.ps.all";
    private static final String PERM_LISTEN = "radioplugin.ps.listen";

    private final PlayerSoundService service;

    public PsCommand(PlayerSoundService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーだけが使えます。");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("使い方: /ps <play|playlist|add|pause|stop|list|skip|previous|all|listen|leave>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> rest = Arrays.asList(args).subList(1, args.length);
        boolean all = sub.equals("all");
        if (all) {
            sub = rest.isEmpty() ? "" : rest.get(0).toLowerCase(Locale.ROOT);
            rest = rest.isEmpty() ? rest : rest.subList(1, rest.size());
        }
        if (!player.hasPermission(permissionFor(all, sub))) {
            player.sendMessage("このコマンドを使う権限がありません。");
            return true;
        }
        service.command(player.getUniqueId(), all, sub, new ArrayList<>(rest));
        return true;
    }

    private static String permissionFor(boolean all, String sub) {
        if (all) {
            return PERM_ALL;
        }
        return sub.equals("listen") || sub.equals("leave") ? PERM_LISTEN : PERM_PS;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> candidates = new ArrayList<>();
        String first = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        boolean all = first.equals("all") && args.length >= 2;
        int offset = all ? 1 : 0;
        int position = args.length - 1 - offset; // 0 ならサブコマンドの位置
        String sub = args.length > offset ? args[offset].toLowerCase(Locale.ROOT) : "";

        if (position == 0) {
            if (all) {
                if (sender.hasPermission(PERM_ALL)) {
                    candidates.addAll(PlayerSoundService.SUB_COMMANDS);
                }
            } else {
                if (sender.hasPermission(PERM_PS)) {
                    candidates.addAll(PlayerSoundService.SUB_COMMANDS);
                }
                if (sender.hasPermission(PERM_ALL)) {
                    candidates.add("all");
                }
                if (sender.hasPermission(PERM_LISTEN)) {
                    candidates.add("listen");
                    candidates.add("leave");
                }
            }
        } else if (position == 1 && sub.equals("list")) {
            candidates.add("score");
        } else if (position == 2 && (sub.equals("play") || sub.equals("playlist"))) {
            candidates.add("loop"); // loop は URL の後ろだけ
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        candidates.removeIf(c -> !c.startsWith(typed));
        return candidates;
    }
}
