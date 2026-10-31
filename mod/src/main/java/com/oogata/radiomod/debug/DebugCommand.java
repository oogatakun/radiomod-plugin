package com.oogata.radiomod.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.oogata.radiomod.audio.AudioEngine;
import com.oogata.radiomod.audio.SlotPlayer;
import com.oogata.radiomod.net.Messages;
import com.oogata.radiomod.sync.ClientSync;
import com.oogata.radiomod.sync.ServerClock;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** 同期の確認用のクライアントコマンド /radiodebug。 */
public final class DebugCommand {

    private DebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("radiodebug")
                .then(Commands.literal("sync").executes(c -> {
                    sync(c.getSource());
                    return 1;
                })));
    }

    /** サーバーとの時計のずれ・往復時間と、各スロットの位置・ずれをチャットに出す。 */
    private static void sync(CommandSourceStack source) {
        ServerClock clock = ClientSync.clock();
        if (!clock.isSynced()) {
            reply(source, "時計合わせ: まだ終わっていません（サーバーに mod 対応の plugin が入っていないか、接続直後です）");
        } else {
            reply(source, String.format("時計合わせ: offset=%.2fms 往復(最短)=%.1fms 往復(直近)=%.1fms 手動の補正=%dms",
                    clock.offsetMs(), clock.bestRttMs(), clock.lastRttMs(), ClientSync.manualOffsetMs()));
        }
        Map<Messages.Slot, SlotPlayer> slots = AudioEngine.get().slots();
        boolean any = false;
        for (Map.Entry<Messages.Slot, SlotPlayer> e : slots.entrySet()) {
            SlotPlayer.Snapshot s = e.getValue().snapshot();
            if (s.state() == SlotPlayer.State.IDLE) {
                continue;
            }
            any = true;
            reply(source, describe(e.getKey(), s));
        }
        if (!any) {
            reply(source, "鳴っているスロットはありません");
        }
    }

    private static String describe(Messages.Slot slot, SlotPlayer.Snapshot s) {
        String name = switch (slot.slotType()) {
            case Messages.Slot.PERSONAL -> "個人";
            case Messages.Slot.SHARED -> "共有";
            default -> "ラジオ " + slot.radioId();
        };
        if (s.live() || !s.synced()) {
            return String.format("[%s] %s(%.1f秒) 鳴っている位置=%sms 溜まり=%d （%s）", name, s.state(),
                    s.stateMs() / 1000.0, ms(s.actualMs()), s.queued(),
                    s.live() ? "ライブ: 位置合わせなし" : "位置合わせなし");
        }
        String diff = s.desiredMs() < 0 || s.actualMs() < 0 ? "-" : String.format("%+d", s.desiredMs() - s.actualMs());
        return String.format("[%s] %s(%.1f秒) 鳴らすべき位置=%sms 鳴っている位置=%sms ずれ=%sms（正なら遅れ）速度=%+.2f%%%s 読み直し=%d回 溜まり=%d 直近の判定=%s",
                name, s.state(), s.stateMs() / 1000.0, ms(s.desiredMs()), ms(s.actualMs()), diff, (s.speed() - 1.0) * 100,
                s.correcting() ? " 補正中" : "", s.gateSeeks(), s.queued(), s.lastGate());
    }

    private static String ms(long value) {
        return value < 0 ? "-" : Long.toString(value);
    }

    private static void reply(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal("[radiodebug] " + text), false);
    }
}
