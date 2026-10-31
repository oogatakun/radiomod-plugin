package com.oogata.radioplugin.player;

import com.oogata.radioplugin.net.Codec;
import com.oogata.radioplugin.net.Messages;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * /rp の中身。ラジオごとに独立した再生エンジンを持つ。ラジオのスロット（RADIO）で、mod 導入済みの
 * 全プレイヤーへ送る（範囲と次元の判定は mod が行う）。Bukkit には依存しない。
 */
public final class RadioPlayerService {

    public static final List<String> SUB_COMMANDS =
            List.of("play", "playlist", "add", "pause", "stop", "list", "skip", "previous", "keep");

    /** ラジオ側（設置位置・名前表示・保存）との境界。 */
    public interface Radios {
        /** 送信用の位置情報。そのラジオがなければ null。 */
        Messages.RadioTarget target(UUID radioId);

        /** 名前表示に出す曲名。null なら消す。 */
        void setNowPlaying(UUID radioId, String title);

        /** 再生状態が変わった（keep の保存用）。 */
        void changed(UUID radioId, PlaybackEngine.Snapshot snapshot);
    }

    private final PlayerHost host;
    private final TrackResolver resolver;
    private final Clock clock;
    private final PlaybackEngine.Settings settings;
    private final Radios radios;
    private final AtomicLong trackIds = new AtomicLong(System.currentTimeMillis());
    private final Map<UUID, PlaybackEngine> engines = new LinkedHashMap<>();

    public RadioPlayerService(PlayerHost host, TrackResolver resolver, Clock clock,
                              PlaybackEngine.Settings settings, Radios radios) {
        this.host = host;
        this.resolver = resolver;
        this.clock = clock;
        this.settings = settings;
        this.radios = radios;
    }

    // ---- 外から呼ばれる入口 ----

    /** keep 以外のサブコマンドを実行する。sub は小文字。 */
    public void command(UUID actor, UUID radioId, String radioName, String sub, List<String> args) {
        Consumer<String> say = text -> host.tell(actor, text);
        String prefix = "/rp " + radioName + " ";
        switch (sub) {
            case "play" -> {
                if (args.isEmpty()) {
                    PlaybackEngine e = engine(radioId);
                    if (e.playCurrent() == PlaybackEngine.Result.OK) {
                        say.accept("再生します: " + e.currentTrack().title());
                    } else {
                        say.accept("再生できる曲がありません。");
                    }
                    return;
                }
                UrlArgs a = parseUrl(say, args, true, "使い方: " + prefix + "play <URL> [loop]");
                if (a != null) {
                    load(actor, a.url(), result -> {
                        Track track = result.tracks().get(0);
                        engine(radioId).playSingle(track, a.loop());
                        say.accept("再生します: " + track.title() + (a.loop() && !track.live() ? "（単曲ループ）" : ""));
                    });
                }
            }
            case "playlist" -> {
                UrlArgs a = parseUrl(say, args, true, "使い方: " + prefix + "playlist <URL> [loop]");
                if (a != null) {
                    load(actor, a.url(), result -> {
                        engine(radioId).playPlaylist(result.tracks(), a.loop());
                        say.accept(result.tracks().size() + "曲を再生します: " + result.tracks().get(0).title()
                                + (a.loop() ? "（プレイリスト全体をループ）" : "") + truncatedNote(result));
                    });
                }
            }
            case "add" -> {
                UrlArgs a = parseUrl(say, args, false, "使い方: " + prefix + "add <URL>");
                if (a != null) {
                    load(actor, a.url(), result -> {
                        PlaybackEngine e = engine(radioId);
                        boolean wasStopped = e.state() == PlaybackEngine.State.STOPPED;
                        e.add(result.tracks());
                        radios.changed(radioId, e.snapshot());
                        say.accept(result.tracks().size() + "曲をキューに追加しました"
                                + (wasStopped ? "。再生を始めます" : "") + truncatedNote(result));
                    });
                }
            }
            case "pause" -> {
                PlaybackEngine e = engine(radioId);
                if (e.pause() == PlaybackEngine.Result.NOT_PLAYING) {
                    say.accept("再生していません。");
                } else {
                    say.accept(e.state() == PlaybackEngine.State.PAUSED ? "一時停止しました。" : "再開しました。");
                }
            }
            case "stop" -> {
                engine(radioId).stop();
                say.accept("再生を止めて、キューを空にしました。");
            }
            case "skip" -> afterMove(say, radioId, engine(radioId).skip());
            case "previous" -> afterMove(say, radioId, engine(radioId).previous());
            case "list" -> list(actor, radioId, args, say);
            default -> say.accept("使い方: " + prefix + "<play|playlist|add|pause|stop|list|skip|previous|keep>");
        }
    }

    /** そのラジオの今の状態。まだ何も再生していなければ null。 */
    public PlaybackEngine.Snapshot snapshot(UUID radioId) {
        PlaybackEngine e = engines.get(radioId);
        return e == null ? null : e.snapshot();
    }

    /** 保存しておいた状態から、先頭（曲の頭）から再生を始める。 */
    public void restore(UUID radioId, PlaybackEngine.Snapshot saved) {
        engine(radioId).restore(saved);
    }

    /** ラジオが撤去された。STOP を送って、再生状態を破棄する。 */
    public void onRadioRemoved(UUID radioId) {
        PlaybackEngine e = engines.remove(radioId);
        if (e != null) {
            e.stop();
        }
        radios.setNowPlaying(radioId, null);
    }

    /** ラジオが移動した。新しい座標を送る。 */
    public void onRadioMoved(UUID radioId, String dimension, double x, double y, double z) {
        Messages.Move move = new Messages.Move(Messages.Slot.radio(radioId), dimension, x, y, z);
        for (UUID p : host.modPlayers()) {
            host.send(p, move);
        }
    }

    /** mod から HELLO が届いた。再生中のラジオ全部の状態を送る。 */
    public void onHello(UUID player) {
        for (Map.Entry<UUID, PlaybackEngine> entry : engines.entrySet()) {
            PlaybackEngine e = entry.getValue();
            Track track = e.currentTrack();
            Messages.RadioTarget target = radios.target(entry.getKey());
            if (track == null || target == null || e.state() == PlaybackEngine.State.STOPPED) {
                continue;
            }
            Messages.Slot slot = Messages.Slot.radio(entry.getKey());
            long origin = e.state() == PlaybackEngine.State.PAUSED
                    ? clock.nowMs() + settings.startLeadMs() - e.positionMs() : e.originMs();
            host.send(player, new Messages.Play(slot, e.trackId(), track.url(), track.title(),
                    track.durationMs(), origin, target));
            if (e.state() == PlaybackEngine.State.PAUSED) {
                host.send(player, new Messages.Pause(slot, e.positionMs()));
            }
        }
    }

    /** mod から ERROR が届いた（ラジオのスロットのものだけ扱う）。 */
    public void onError(Messages.Error error) {
        if (error.slot().slotType() != Messages.Slot.RADIO) {
            return;
        }
        PlaybackEngine e = engines.get(error.slot().radioId());
        if (e != null) {
            e.onError(error.trackId());
        }
    }

    /** 一定間隔に呼ぶ。曲の終わりと SYNC の再送を進める。 */
    public void tick() {
        for (PlaybackEngine e : new ArrayList<>(engines.values())) {
            e.tick();
        }
    }

    // ---- 内部 ----

    private PlaybackEngine engine(UUID radioId) {
        return engines.computeIfAbsent(radioId, id ->
                new PlaybackEngine(clock, settings, trackIds::incrementAndGet, new RadioOutput(id)));
    }

    private void afterMove(Consumer<String> say, UUID radioId, PlaybackEngine.Result result) {
        PlaybackEngine e = engine(radioId);
        switch (result) {
            case EMPTY -> say.accept("再生できる曲がありません。");
            case LIVE -> say.accept("ライブ配信では使えません。");
            default -> say.accept(e.state() == PlaybackEngine.State.STOPPED
                    ? "最後の曲だったので、停止しました。" : "再生します: " + e.currentTrack().title());
        }
    }

    private void list(UUID actor, UUID radioId, List<String> args, Consumer<String> say) {
        if (args.size() > 1 || (args.size() == 1 && !args.get(0).equalsIgnoreCase("score"))) {
            say.accept("使い方: /rp <ラジオ名> list [score]");
            return;
        }
        PlaybackEngine e = engine(radioId);
        if (!args.isEmpty()) {
            scoreToggle(actor, e);
        } else if (e.queue().isEmpty()) {
            say.accept("キューは空です。");
        } else {
            QueueFormatter.chatLines(e).forEach(say);
        }
    }

    private final java.util.Set<UUID> scoreShown = new java.util.HashSet<>();

    private void scoreToggle(UUID actor, PlaybackEngine e) {
        if (scoreShown.remove(actor)) {
            host.hideScore(actor);
            return;
        }
        scoreShown.add(actor);
        host.showScore(actor, "キュー", QueueFormatter.scoreLines(e.queue(), e.index(),
                e.state() != PlaybackEngine.State.STOPPED));
    }

    private record UrlArgs(String url, boolean loop) {
    }

    private static UrlArgs parseUrl(Consumer<String> say, List<String> args, boolean allowLoop, String usage) {
        boolean loop = args.size() == 2 && allowLoop && args.get(1).equalsIgnoreCase("loop");
        if (args.isEmpty() || args.size() > (allowLoop ? 2 : 1) || (args.size() == 2 && !loop)) {
            say.accept(usage);
            return null;
        }
        switch (UrlPolicy.check(args.get(0))) {
            case INVALID -> {
                say.accept(usage + "（URL は http または https のみ）");
                return null;
            }
            case INTERNAL -> {
                say.accept("内部アドレスの URL は使えません。");
                return null;
            }
            default -> {
                return new UrlArgs(args.get(0), loop);
            }
        }
    }

    private static String truncatedNote(TrackResolver.Result result) {
        return result.truncated() ? "（上限のため、先頭の" + result.tracks().size() + "曲だけです）" : "";
    }

    private void load(UUID actor, String url, Consumer<TrackResolver.Result> onSuccess) {
        host.tell(actor, "取得しています…");
        resolver.resolve(url, result -> {
            if (!result.ok()) {
                host.tell(actor, switch (result.failure()) {
                    case FORBIDDEN -> "内部アドレスの URL は使えません。";
                    case UNSUPPORTED -> "非対応のURLです。";
                    default -> "取得に失敗しました" + (result.detail().isEmpty() ? "。" : ": " + result.detail());
                });
                return;
            }
            List<Track> usable = new ArrayList<>();
            for (Track t : result.tracks()) {
                if (t.url().getBytes(StandardCharsets.UTF_8).length <= Codec.MAX_URL_BYTES) {
                    usable.add(t);
                }
            }
            if (usable.isEmpty()) {
                host.tell(actor, "URL が長すぎて再生できません。");
                return;
            }
            onSuccess.accept(usable.size() == result.tracks().size() ? result
                    : result.isPlaylist() ? TrackResolver.Result.playlist(usable, result.truncated())
                    : TrackResolver.Result.single(usable.get(0)));
        });
    }

    /** エンジンの出力を、ラジオスロットの送信と名前表示にする。 */
    private final class RadioOutput implements PlaybackEngine.Output {
        private final UUID radioId;
        private final Messages.Slot slot;

        RadioOutput(UUID radioId) {
            this.radioId = radioId;
            this.slot = Messages.Slot.radio(radioId);
        }

        private void sendAll(Messages message) {
            for (UUID p : host.modPlayers()) {
                host.send(p, message);
            }
        }

        private void changed() {
            PlaybackEngine e = engines.get(radioId);
            if (e != null) {
                radios.changed(radioId, e.snapshot());
            }
        }

        @Override
        public void play(Track track, long trackId, long originMs) {
            Messages.RadioTarget target = radios.target(radioId);
            if (target != null) {
                sendAll(new Messages.Play(slot, trackId, track.url(), track.title(), track.durationMs(), originMs, target));
            }
            changed();
        }

        @Override
        public void pause(long positionMs) {
            sendAll(new Messages.Pause(slot, positionMs));
            changed();
        }

        @Override
        public void resume(long originMs) {
            sendAll(new Messages.Resume(slot, originMs));
            changed();
        }

        @Override
        public void stop() {
            sendAll(new Messages.Stop(slot));
            radios.setNowPlaying(radioId, null);
            changed();
        }

        @Override
        public void sync(long originMs) {
            sendAll(new Messages.Sync(slot, originMs));
        }

        @Override
        public void trackStarted(Track track) {
            radios.setNowPlaying(radioId, track.title());
        }

        @Override
        public void ended() {
            radios.setNowPlaying(radioId, null);
            changed();
        }
    }
}
