package com.oogata.radioplugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** ステップ1用の空の枠。/ps・/rd・/rp を登録するだけで、中身は後のステップで作る。 */
public final class StubCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("未実装です");
        return true;
    }
}
