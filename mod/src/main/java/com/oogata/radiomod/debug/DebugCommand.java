package com.oogata.radiomod.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.oogata.radiomod.audio.AudioEngine;
import com.oogata.radiomod.audio.AudioFailure;
import com.oogata.radiomod.audio.PlaybackListener;
import com.oogata.radiomod.audio.SlotPlayer;
import com.oogata.radiomod.net.Messages;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

/**
 * 動作確認用のクライアントコマンド /radiodebug。個人スロットだけを鳴らす。
 * ステップ3で外す（/radiodebug sync はそのとき作り直して残す）。
 */
public final class DebugCommand {

    private DebugCommand() {
    }

    public static void onRegister(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
        AudioEngine.get().setMasterVolume(DebugCommand::minecraftMasterVolume);
        AudioEngine.get().setListener(new PlaybackListener() {
            @Override
            public void onStarted(Messages.Slot slot) {
                say("再生が始まりました");
            }

            @Override
            public void onFinished(Messages.Slot slot) {
                say("曲が終わりました");
            }

            @Override
            public void onError(Messages.Slot slot, AudioFailure failure) {
                say("エラー reason=" + failure.reason() + " : " + failure.detail());
            }
        });
    }

    private static double minecraftMasterVolume() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options == null ? 1.0 : mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER);
    }

    private static SlotPlayer player() {
        return AudioEngine.get().slot(Messages.Slot.PERSONAL_SLOT);
    }

    /** どのスレッドからでも、チャットに1行出す。 */
    private static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("[radiodebug] " + text));
            }
        });
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("radiodebug")
                .then(Commands.literal("play")
                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                .executes(c -> play(c, 0)))
                        .then(Commands.literal("at")
                                .then(Commands.argument("ms", LongArgumentType.longArg(0))
                                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                                .executes(c -> play(c, LongArgumentType.getLong(c, "ms")))))))
                .then(Commands.literal("pause").executes(c -> run("一時停止", player()::pause)))
                .then(Commands.literal("resume").executes(c -> run("再開", player()::resume)))
                .then(Commands.literal("stop").executes(c -> run("停止", player()::stop)))
                .then(Commands.literal("seek")
                        .then(Commands.argument("ms", LongArgumentType.longArg(0)).executes(c -> {
                            long ms = LongArgumentType.getLong(c, "ms");
                            say(player().seek(ms) ? ms + "ms へ移動" : "位置を合わせ直せません（ライブか、再生していません）");
                            return 1;
                        })))
                .then(Commands.literal("speed")
                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(-2, 2)).executes(c -> {
                            double p = DoubleArgumentType.getDouble(c, "percent");
                            player().setSpeedDeviation(p / 100.0);
                            say("速度の目標を " + p + "% にしました（なめらかに近づきます）");
                            return 1;
                        })))
                .then(Commands.literal("volume")
                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0, 100)).executes(c -> {
                            double p = DoubleArgumentType.getDouble(c, "percent");
                            AudioEngine.get().setVolume(Messages.Slot.PERSONAL, p / 100.0);
                            say("個人スロットの音量を " + p + "% にしました");
                            return 1;
                        })))
                .then(Commands.literal("pos").executes(c -> {
                    SlotPlayer p = player();
                    say("状態=" + p.state() + " 位置=" + p.getPositionMs() + "ms 長さ=" + p.durationMs()
                            + "ms ライブ=" + p.isLive() + " 速度=" + String.format("%.4f", p.currentSpeed()));
                    return 1;
                })));
    }

    private static int play(CommandContext<CommandSourceStack> c, long startMs) {
        String url = StringArgumentType.getString(c, "url");
        say("取得中: " + url);
        player().play(url, startMs);
        return 1;
    }

    private static int run(String label, Runnable action) {
        action.run();
        say(label);
        return 1;
    }
}
