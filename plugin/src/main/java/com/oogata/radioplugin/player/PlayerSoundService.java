package com.oogata.radioplugin.player;

import com.oogata.radioplugin.net.Codec;
import com.oogata.radioplugin.net.Messages;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * /ps の中身。個人再生（プレイヤーごと）と共有の再生（サーバーに1つ）を持ち、
 * 共有の開始・聞く・やめる・終了・置き換えと、共有中の確認を扱う。Bukkit には依存しない。
 */
public final class PlayerSoundService {

    public static final List<String> SUB_COMMANDS =
            List.of("play", "playlist", "add", "pause", "stop", "list", "skip", "previous", "popup");

    /** 共有を聞いている間に打つと確認が出る操作（list は表示だけなので含まない）。 */
    private static final Set<String> CONFIRM_REQUIRED =
            Set.of("play", "playlist", "add", "pause", "stop", "skip", "previous");

    private static final String USAGE_PLAY = "使い方: /ps play <URL> [loop]";
    private static final String USAGE_PLAYLIST = "使い方: /ps playlist <URL> [loop]";
    private static final String USAGE_ADD = "使い方: /ps add <URL>";

    public record Config(PlaybackEngine.Settings engine, long confirmTimeoutMs) {
        public static Config defaults() {
            return new Config(PlaybackEngine.Settings.defaults(), 30_000);
        }
    }

    private static final class Personal {
        PlaybackEngine engine;
        /** 共有を聞き始めたとき、再生中だった個人再生を一時停止した。やめたら再開する。 */
        boolean pausedByShare;
    }

    private static final class Shared {
        PlaybackEngine engine;
        final Set<UUID> listeners = new LinkedHashSet<>();
        UUID starter;
        /** 曲が始まったら、ほかのプレイヤーへお知らせを出す。 */
        boolean needAnnounce;
    }

    private record ScoreView(boolean shared, List<String> lines) {
    }

    private record Pending(String sub, List<String> args, long expiresAtMs) {
    }

    /** 1回の操作の対象。 */
    private record Ctx(UUID actor, boolean shared, boolean mayCreateShared) {
    }

    private final PlayerHost host;
    private final TrackResolver resolver;
    private final Clock clock;
    private final Config config;
    private final AtomicLong trackIds = new AtomicLong(System.currentTimeMillis());

    private final Map<UUID, Personal> personals = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    /** list score を表示中のプレイヤーと、見せている内容（共有か個人か・最後に出した行）。 */
    private final Map<UUID, ScoreView> scoreViews = new HashMap<>();
    private Shared shared;
    private Consumer<String> trace = line -> { };

    public PlayerSoundService(PlayerHost host, TrackResolver resolver, Clock clock, Config config) {
        this.host = host;
        this.resolver = resolver;
        this.clock = clock;
        this.config = config;
    }

    /** エンジンの診断ログの出し先。以後に作るエンジンに効く(個人再生は最初の操作で作られる)。 */
    public void setTrace(Consumer<String> trace) {
        this.trace = trace;
    }

    // ---- 外から呼ばれる入口 ----

    /**
     * コマンドを実行する。sub は小文字にしたサブコマンド（all の場合は all の後ろ。空なら /ps all だけ）。
     */
    public void command(UUID actor, boolean all, String sub, List<String> args) {
        try {
            dispatch(actor, all, sub, args);
        } finally {
            refreshScores();
        }
    }

    private void dispatch(UUID actor, boolean all, String sub, List<String> args) {
        if (all) {
            commandAll(actor, sub, args);
            return;
        }
        switch (sub) {
            case "listen" -> listen(actor);
            case "leave" -> leave(actor);
            case "confirm" -> confirm(actor);
            case "cancel" -> cancel(actor);
            case "popup" -> popup(actor, false);
            default -> {
                if (!SUB_COMMANDS.contains(sub)) {
                    host.tell(actor, "使い方: /ps <play|playlist|add|pause|stop|list|skip|previous|all|listen|leave>");
                    return;
                }
                if (!validate(actor, sub, args)) {
                    return;
                }
                if (CONFIRM_REQUIRED.contains(sub) && isListening(actor)) {
                    askConfirm(actor, sub, args);
                    return;
                }
                execute(new Ctx(actor, false, false), sub, args);
            }
        }
    }

    /** プレーヤー画面を開く。個人は自分の再生、all なら聞いている共有。 */
    public void popup(UUID actor, boolean all) {
        if (!host.hasMod(actor)) {
            host.tell(actor, "プレーヤー画面を出すには、mod が必要です。");
            return;
        }
        if (all && !isListening(actor)) {
            host.tell(actor, "共有を聞いていません。/ps listen で聞いてから開いてください。");
            return;
        }
        Messages.Slot slot = all ? Messages.Slot.SHARED_SLOT : Messages.Slot.PERSONAL_SLOT;
        PlaybackEngine e = all ? shared.engine : personalOf(actor).engine;
        host.send(actor, new Messages.Open(slot));
        host.send(actor, QueueView.of(slot, "", e));
    }

    /** プレーヤー画面からの操作(CONTROL)。個人と共有のスロットだけ扱う(ラジオは RadioPlayerService)。 */
    public void control(UUID actor, Messages.Control control) {
        Messages.Slot slot = control.slot();
        PlaybackEngine e;
        if (slot.slotType() == Messages.Slot.SHARED) {
            if (!isListening(actor)) {
                return;
            }
            e = shared.engine;
        } else if (slot.slotType() == Messages.Slot.PERSONAL) {
            e = personalOf(actor).engine;
        } else {
            return;
        }
        if (control.action() != Messages.Control.REFRESH) {
            if (slot.slotType() == Messages.Slot.PERSONAL && isListening(actor)) {
                host.tell(actor, "共有を聞いている間は、個人の再生は操作できません。");
            } else if (slot.slotType() == Messages.Slot.SHARED && control.action() == Messages.Control.STOP) {
                leave(actor); // 共有そのものは、ほかの人のために続ける
                host.send(actor, QueueView.of(slot, "", e));
                return;
            } else {
                QueueView.apply(e, control);
            }
            refreshScores();
        }
        host.send(actor, QueueView.of(slot, "", e));
    }

    public boolean isListening(UUID player) {
        return shared != null && shared.listeners.contains(player);
    }

    /**
     * mod から HELLO が届いた。個人再生と(聞いている)共有の今の状態を送り直し、
     * すでに共有が始まっていて聞いていなければ、お知らせを出す。
     */
    public void onHello(UUID player) {
        Personal p = personals.get(player);
        if (p != null) {
            sendState(player, Messages.Slot.PERSONAL_SLOT, p.engine);
        }
        if (shared != null && shared.listeners.contains(player)) {
            sendState(player, Messages.Slot.SHARED_SLOT, shared.engine);
        } else if (shared != null && shared.engine.state() != PlaybackEngine.State.STOPPED) {
            announceShareTo(player);
        }
    }

    public void onQuit(UUID player) {
        pending.remove(player);
        scoreViews.remove(player);
        personals.remove(player);
        if (shared != null && shared.listeners.remove(player) && shared.listeners.isEmpty()) {
            Shared ended = shared;
            shared = null;
            ended.engine.stop();
        }
    }

    /** mod から ERROR が届いた。 */
    public void onError(UUID player, Messages.Error error) {
        PlaybackEngine engine;
        Collection<UUID> notify;
        if (error.slot().slotType() == Messages.Slot.PERSONAL) {
            Personal p = personals.get(player);
            if (p == null) {
                return;
            }
            engine = p.engine;
            notify = List.of(player);
        } else if (error.slot().slotType() == Messages.Slot.SHARED) {
            if (!isListening(player)) {
                return;
            }
            engine = shared.engine;
            notify = new ArrayList<>(shared.listeners);
        } else {
            return;
        }
        Track failed = engine.currentTrack();
        String name = failed == null ? "" : failed.title();
        String detail = error.detail().isEmpty() ? "" : "（" + error.detail() + "）";
        switch (engine.onError(error.trackId())) {
            case SKIPPED -> tellAll(notify, "曲を取得できなかったので、スキップしました: " + name + detail);
            case STOPPED_BY_LIMIT -> tellAll(notify, "連続して取得に失敗したので、再生を止めました: " + name + detail);
            case STOPPED_AT_END -> tellAll(notify, "曲を取得できず、次の曲もないので、再生を止めました: " + name + detail);
            case IGNORED -> {
            }
        }
        refreshScores();
    }

    /** 一定間隔（数tickごと）に呼ぶ。曲の終わりと SYNC の再送を進める。 */
    public void tick() {
        for (Personal p : new ArrayList<>(personals.values())) {
            p.engine.tick();
        }
        if (shared != null) {
            shared.engine.tick();
        }
        refreshScores();
    }

    // ---- /ps all ----

    private void commandAll(UUID actor, String sub, List<String> args) {
        if (sub.isEmpty()) {
            shareFromPersonal(actor);
            return;
        }
        if (sub.equals("popup")) {
            popup(actor, true);
            return;
        }
        if (!SUB_COMMANDS.contains(sub)) {
            host.tell(actor, "使い方: /ps all [play|playlist|add|pause|stop|list|skip|previous|popup]");
            return;
        }
        if (!validate(actor, sub, args)) {
            return;
        }
        if (shared == null) {
            boolean starts = (sub.equals("play") && !args.isEmpty()) || sub.equals("playlist") || sub.equals("add");
            if (!starts) {
                host.tell(actor, "共有している再生がありません。");
                return;
            }
            execute(new Ctx(actor, true, true), sub, args);
            return;
        }
        if (!shared.listeners.contains(actor)) {
            host.tell(actor, "共有を聞いていないので、操作できません。/ps listen で聞くか、/ps all で自分の再生に置き換えてください。");
            return;
        }
        execute(new Ctx(actor, true, false), sub, args);
    }

    /** /ps all: 自分の今の再生を共有として始める。すでに共有があれば置き換える。 */
    private void shareFromPersonal(UUID actor) {
        if (isListening(actor)) {
            host.tell(actor, "すでに共有を聞いています。自分の再生を共有するには、先に /ps leave で聞くのをやめてください。");
            return;
        }
        Personal p = personalOf(actor);
        if (p.engine.state() == PlaybackEngine.State.STOPPED) {
            host.tell(actor, "共有できる再生がありません。先に /ps play <URL> で再生してください。");
            return;
        }
        PlaybackEngine.Snapshot snapshot = p.engine.snapshot();
        if (shared != null) {
            endShared(host.nameOf(actor) + "さんが共有を置き換えました。");
        }
        createShared(actor);
        shared.engine.restore(snapshot);
        host.tell(actor, "自分の再生を共有しました。");
    }

    // ---- 聞く・やめる ----

    private void listen(UUID actor) {
        if (shared == null) {
            host.tell(actor, "共有している再生がありません。");
            return;
        }
        if (shared.listeners.contains(actor)) {
            host.tell(actor, "すでに共有を聞いています。");
            return;
        }
        suspendPersonal(actor);
        shared.listeners.add(actor);
        sendSharedState(actor);
        host.tell(actor, "共有を聞き始めました。やめるときは /ps leave です。");
        Track current = shared.engine.currentTrack();
        if (current != null && shared.engine.state() != PlaybackEngine.State.STOPPED) {
            host.announceTrack(actor, current.title());
        }
    }

    private void leave(UUID actor) {
        if (!isListening(actor)) {
            host.tell(actor, "共有を聞いていません。");
            return;
        }
        stopListening(actor);
        host.tell(actor, "共有を聞くのをやめました。また聞くときは /ps listen です。");
    }

    /** 聞くのをやめる。共有そのものは続き、止めていた個人再生は止まった位置から再開する。 */
    private void stopListening(UUID actor) {
        shared.listeners.remove(actor);
        host.send(actor, new Messages.Stop(Messages.Slot.SHARED_SLOT));
        resumePersonal(actor);
    }

    private void endShared(String message) {
        Shared ended = shared;
        shared = null;
        List<UUID> listeners = new ArrayList<>(ended.listeners);
        ended.engine.stop(); // STOP を聞いている全員へ送る
        ended.listeners.clear();
        for (UUID listener : listeners) {
            resumePersonal(listener);
            host.tell(listener, message);
        }
    }

    private void suspendPersonal(UUID player) {
        Personal p = personalOf(player);
        if (p.engine.state() == PlaybackEngine.State.PLAYING) {
            p.engine.pause();
            p.pausedByShare = true;
        }
    }

    private void resumePersonal(UUID player) {
        Personal p = personals.get(player);
        if (p != null && p.pausedByShare) {
            p.pausedByShare = false;
            if (p.engine.state() == PlaybackEngine.State.PAUSED) {
                p.engine.pause(); // 一時停止中なら再開になる
            }
        }
    }

    private void sendSharedState(UUID player) {
        sendState(player, Messages.Slot.SHARED_SLOT, shared.engine);
    }

    /** そのエンジンの今の状態を、1人に送る。再生中なら PLAY、一時停止中なら PLAY のあとに PAUSE。 */
    private void sendState(UUID player, Messages.Slot slot, PlaybackEngine e) {
        Track track = e.currentTrack();
        if (track == null || e.state() == PlaybackEngine.State.STOPPED) {
            return;
        }
        long origin = e.state() == PlaybackEngine.State.PAUSED
                ? clock.nowMs() + config.engine().startLeadMs() - e.positionMs() : e.originMs();
        host.send(player, playMessage(slot, track, e.trackId(), origin));
        if (e.state() == PlaybackEngine.State.PAUSED) {
            host.send(player, new Messages.Pause(slot, e.positionMs()));
        }
    }

    // ---- 確認 ----

    private void askConfirm(UUID actor, String sub, List<String> args) {
        pending.put(actor, new Pending(sub, args, clock.nowMs() + config.confirmTimeoutMs()));
        host.tellButtons(actor, "共有を聞いています。聞くのをやめて、自分の再生を実行しますか？",
                List.of(new PlayerHost.Button("［やめて実行］", "/ps confirm"),
                        new PlayerHost.Button("［キャンセル］", "/ps cancel")));
    }

    private void confirm(UUID actor) {
        Pending p = pending.remove(actor);
        if (p == null || clock.nowMs() > p.expiresAtMs()) {
            host.tell(actor, "確認待ちの操作がありません。もう一度実行してください。");
            return;
        }
        if (isListening(actor)) {
            stopListening(actor); // /ps leave と同じ。個人再生は再開してから、続けて打った操作の結果になる
        }
        execute(new Ctx(actor, false, false), p.sub(), p.args());
    }

    private void cancel(UUID actor) {
        if (pending.remove(actor) == null) {
            host.tell(actor, "確認待ちの操作がありません。");
            return;
        }
        host.tell(actor, "キャンセルしました。");
    }

    // ---- 操作の実行 ----

    private boolean validate(UUID actor, String sub, List<String> args) {
        switch (sub) {
            case "play" -> {
                return args.isEmpty() || parseUrl(actor, args, true, USAGE_PLAY) != null;
            }
            case "playlist" -> {
                return parseUrl(actor, args, true, USAGE_PLAYLIST) != null;
            }
            case "add" -> {
                return parseUrl(actor, args, false, USAGE_ADD) != null;
            }
            case "list" -> {
                if (args.size() > 1 || (args.size() == 1 && !args.get(0).equalsIgnoreCase("score"))) {
                    host.tell(actor, "使い方: /ps list [score]");
                    return false;
                }
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    private record UrlArgs(String url, boolean loop) {
    }

    /** URL と loop の引数を読む。条件に合わなければ使い方などを表示して null。 */
    private UrlArgs parseUrl(UUID actor, List<String> args, boolean allowLoop, String usage) {
        boolean loop = args.size() == 2 && allowLoop && args.get(1).equalsIgnoreCase("loop");
        if (args.isEmpty() || args.size() > (allowLoop ? 2 : 1) || (args.size() == 2 && !loop)) {
            host.tell(actor, usage);
            return null;
        }
        switch (UrlPolicy.check(args.get(0))) {
            case INVALID -> {
                host.tell(actor, usage + "（URL は http または https のみ）");
                return null;
            }
            case INTERNAL -> {
                host.tell(actor, "内部アドレスの URL は使えません。");
                return null;
            }
            default -> {
                return new UrlArgs(args.get(0), loop);
            }
        }
    }

    private void execute(Ctx ctx, String sub, List<String> args) {
        UUID actor = ctx.actor();
        switch (sub) {
            case "play" -> {
                if (args.isEmpty()) {
                    PlaybackEngine e = acquire(ctx, false);
                    if (e != null) {
                        reportStart(actor, e.playCurrent() == PlaybackEngine.Result.OK ? e.currentTrack() : null);
                    }
                    return;
                }
                UrlArgs a = parseUrl(actor, args, true, USAGE_PLAY);
                if (a != null) {
                    load(ctx, a.url(), result -> {
                        PlaybackEngine e = acquire(ctx, true);
                        if (e == null) {
                            return;
                        }
                        Track track = result.tracks().get(0);
                        e.playSingle(track, a.loop());
                        reportStart(actor, track, a.loop() && !track.live() ? "（単曲ループ）" : "");
                    });
                }
            }
            case "playlist" -> {
                UrlArgs a = parseUrl(actor, args, true, USAGE_PLAYLIST);
                if (a != null) {
                    load(ctx, a.url(), result -> {
                        PlaybackEngine e = acquire(ctx, true);
                        if (e == null) {
                            return;
                        }
                        e.playPlaylist(result.tracks(), a.loop());
                        host.tell(actor, result.tracks().size() + "曲を再生します: " + result.tracks().get(0).title()
                                + (a.loop() ? "（プレイリスト全体をループ）" : "") + truncatedNote(result));
                    });
                }
            }
            case "add" -> {
                UrlArgs a = parseUrl(actor, args, false, USAGE_ADD);
                if (a != null) {
                    load(ctx, a.url(), result -> {
                        PlaybackEngine e = acquire(ctx, true);
                        if (e == null) {
                            return;
                        }
                        boolean wasStopped = e.state() == PlaybackEngine.State.STOPPED;
                        e.add(result.tracks());
                        host.tell(actor, result.tracks().size() + "曲をキューに追加しました"
                                + (wasStopped ? "。再生を始めます" : "") + truncatedNote(result));
                    });
                }
            }
            case "pause" -> {
                PlaybackEngine e = acquire(ctx, false);
                if (e == null) {
                    return;
                }
                if (e.pause() == PlaybackEngine.Result.NOT_PLAYING) {
                    host.tell(actor, "再生していません。");
                } else {
                    host.tell(actor, e.state() == PlaybackEngine.State.PAUSED ? "一時停止しました。" : "再開しました。");
                }
            }
            case "stop" -> {
                PlaybackEngine e = acquire(ctx, false);
                if (e == null) {
                    return;
                }
                if (ctx.shared()) {
                    endShared("共有を終了しました。");
                } else {
                    e.stop();
                    host.tell(actor, "再生を止めて、キューを空にしました。");
                }
            }
            case "skip" -> {
                PlaybackEngine e = acquire(ctx, false);
                if (e != null) {
                    afterMove(actor, e, e.skip());
                }
            }
            case "previous" -> {
                PlaybackEngine e = acquire(ctx, false);
                if (e != null) {
                    afterMove(actor, e, e.previous());
                }
            }
            case "list" -> {
                PlaybackEngine e = acquire(ctx, false);
                if (e == null) {
                    return;
                }
                if (!args.isEmpty()) {
                    toggleScore(actor, ctx.shared(), e);
                } else if (e.queue().isEmpty()) {
                    host.tell(actor, "キューは空です。");
                } else {
                    for (String line : QueueFormatter.chatLines(e)) {
                        host.tell(actor, line);
                    }
                }
            }
            default -> {
            }
        }
    }

    private void afterMove(UUID actor, PlaybackEngine e, PlaybackEngine.Result result) {
        switch (result) {
            case EMPTY -> host.tell(actor, "再生できる曲がありません。");
            case LIVE -> host.tell(actor, "ライブ配信では使えません。");
            default -> {
                if (e.state() == PlaybackEngine.State.STOPPED) {
                    host.tell(actor, "最後の曲だったので、停止しました。");
                } else {
                    reportStart(actor, e.currentTrack());
                }
            }
        }
    }

    private void reportStart(UUID actor, Track track) {
        reportStart(actor, track, "");
    }

    private void reportStart(UUID actor, Track track, String note) {
        host.tell(actor, track == null ? "再生できる曲がありません。" : "再生します: " + track.title() + note);
    }

    private static String truncatedNote(TrackResolver.Result result) {
        return result.truncated() ? "（上限のため、先頭の" + result.tracks().size() + "曲だけです）" : "";
    }

    private void toggleScore(UUID actor, boolean sharedView, PlaybackEngine e) {
        if (scoreViews.remove(actor) != null) {
            host.hideScore(actor);
            return;
        }
        if (e.queue().isEmpty()) {
            host.tell(actor, "キューが空です。");
            return;
        }
        List<String> lines = scoreLines(e);
        scoreViews.put(actor, new ScoreView(sharedView, lines));
        host.showScore(actor, "キュー", lines);
    }

    private static List<String> scoreLines(PlaybackEngine e) {
        return QueueFormatter.scoreLines(e.queue(), e.index(), e.state() != PlaybackEngine.State.STOPPED);
    }

    /** キューや再生中の曲が変わっていたら、表示中のプレイヤーのサイドバーを作り直す。空になったら消す。 */
    private void refreshScores() {
        for (Map.Entry<UUID, ScoreView> entry : new ArrayList<>(scoreViews.entrySet())) {
            UUID viewer = entry.getKey();
            ScoreView view = entry.getValue();
            PlaybackEngine e = null;
            if (view.shared()) {
                e = shared == null ? null : shared.engine;
            } else if (personals.containsKey(viewer)) {
                e = personals.get(viewer).engine;
            }
            if (e == null || e.queue().isEmpty()) {
                scoreViews.remove(viewer);
                host.hideScore(viewer);
                continue;
            }
            List<String> lines = scoreLines(e);
            if (!lines.equals(view.lines())) {
                scoreViews.put(viewer, new ScoreView(view.shared(), lines));
                host.showScore(viewer, "キュー", lines);
            }
        }
    }

    /** URL を取得し、成功したら onSuccess を呼ぶ。失敗はここで実行者に伝える。 */
    private void load(Ctx ctx, String url, Consumer<TrackResolver.Result> onSuccess) {
        UUID actor = ctx.actor();
        host.tell(actor, "取得しています…");
        resolver.resolve(url, result -> {
            if (!host.isOnline(actor)) {
                return;
            }
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

    /** 対象のエンジンを得る。得られないときは実行者に伝えて null。 */
    private PlaybackEngine acquire(Ctx ctx, boolean mayCreate) {
        UUID actor = ctx.actor();
        if (!ctx.shared()) {
            return personalOf(actor).engine;
        }
        if (shared == null) {
            if (ctx.mayCreateShared() && mayCreate) {
                createShared(actor);
                return shared.engine;
            }
            host.tell(actor, "共有している再生がありません。");
            return null;
        }
        if (!shared.listeners.contains(actor)) {
            host.tell(actor, "共有を聞いていないので、操作できません。");
            return null;
        }
        return shared.engine;
    }

    // ---- 生成・お知らせ ----

    private Personal personalOf(UUID player) {
        return personals.computeIfAbsent(player, id -> {
            Personal p = new Personal();
            p.engine = new PlaybackEngine(clock, config.engine(), trackIds::incrementAndGet,
                    new SlotOutput(Messages.Slot.PERSONAL_SLOT, () -> List.of(id), new Hooks() {
                        @Override
                        public void started(Track track) {
                            host.announceTrack(id, track.title());
                        }

                        @Override
                        public void ended() {
                            host.tell(id, "最後の曲が終わったので、停止しました。");
                        }
                    }));
            p.engine.setTrace("個人:" + host.nameOf(id), trace);
            return p;
        });
    }

    private void createShared(UUID starter) {
        Shared s = new Shared();
        s.starter = starter;
        s.needAnnounce = true;
        s.engine = new PlaybackEngine(clock, config.engine(), trackIds::incrementAndGet,
                new SlotOutput(Messages.Slot.SHARED_SLOT, () -> s.listeners, new Hooks() {
                    @Override
                    public void started(Track track) {
                        for (UUID listener : new ArrayList<>(s.listeners)) {
                            host.announceTrack(listener, track.title());
                        }
                        if (s.needAnnounce) {
                            s.needAnnounce = false;
                            announceShare(s, track);
                        }
                    }

                    @Override
                    public void ended() {
                        tellAll(s.listeners, "共有の最後の曲が終わったので、停止しました。");
                    }
                }));
        s.engine.setTrace("共有", trace);
        suspendPersonal(starter);
        s.listeners.add(starter);
        shared = s;
    }

    private void announceShare(Shared s, Track track) {
        for (UUID player : host.modPlayers()) {
            if (!s.listeners.contains(player)) {
                announceShareTo(player, s, track);
            }
        }
    }

    private void announceShareTo(UUID player) {
        announceShareTo(player, shared, shared.engine.currentTrack());
    }

    private void announceShareTo(UUID player, Shared s, Track track) {
        String title = track == null ? "" : track.title();
        host.tellButtons(player, host.nameOf(s.starter) + "さんが音楽を共有しています: " + title,
                List.of(new PlayerHost.Button("［聞く］", "/ps listen"),
                        new PlayerHost.Button("［聞かない］", "/ps leave")));
    }

    private void tellAll(Collection<UUID> players, String text) {
        for (UUID p : new ArrayList<>(players)) {
            host.tell(p, text);
        }
    }

    private static Messages.Play playMessage(Messages.Slot slot, Track track, long trackId, long originMs) {
        return new Messages.Play(slot, trackId, track.url(), track.title(), track.durationMs(), originMs, null);
    }

    // ---- エンジンの出力を mod への送信にする ----

    private interface Hooks {
        void started(Track track);

        void ended();
    }

    private final class SlotOutput implements PlaybackEngine.Output {
        private final Messages.Slot slot;
        private final Supplier<Collection<UUID>> recipients;
        private final Hooks hooks;

        SlotOutput(Messages.Slot slot, Supplier<Collection<UUID>> recipients, Hooks hooks) {
            this.slot = slot;
            this.recipients = recipients;
            this.hooks = hooks;
        }

        private void sendAll(Messages message) {
            for (UUID id : new ArrayList<>(recipients.get())) {
                host.send(id, message);
            }
        }

        @Override
        public void play(Track track, long trackId, long originMs) {
            sendAll(playMessage(slot, track, trackId, originMs));
        }

        @Override
        public void pause(long positionMs) {
            sendAll(new Messages.Pause(slot, positionMs));
        }

        @Override
        public void resume(long originMs) {
            sendAll(new Messages.Resume(slot, originMs));
        }

        @Override
        public void stop() {
            sendAll(new Messages.Stop(slot));
        }

        @Override
        public void sync(long originMs) {
            sendAll(new Messages.Sync(slot, originMs));
        }

        @Override
        public void trackStarted(Track track) {
            hooks.started(track);
        }

        @Override
        public void ended() {
            hooks.ended();
        }
    }
}
