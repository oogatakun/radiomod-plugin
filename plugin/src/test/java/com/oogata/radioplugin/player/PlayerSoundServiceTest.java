package com.oogata.radioplugin.player;

import static com.oogata.radioplugin.player.Fakes.live;
import static com.oogata.radioplugin.player.Fakes.song;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oogata.radioplugin.net.Messages;
import com.oogata.radioplugin.player.Fakes.FakeClock;
import com.oogata.radioplugin.player.Fakes.FakeHost;
import com.oogata.radioplugin.player.Fakes.FakeResolver;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerSoundServiceTest {

    private static final String A_URL = "https://example.com/a.mp3";
    private static final String B_URL = "https://example.com/b.mp3";
    private static final String LIST_URL = "https://example.com/list";
    private static final String LIVE_URL = "https://example.com/radio";

    private FakeClock clock;
    private FakeHost host;
    private FakeResolver resolver;
    private PlayerSoundService ps;
    private UUID alice;
    private UUID bob;
    private UUID carol; // mod 未導入

    @BeforeEach
    void setUp() {
        clock = new FakeClock();
        host = new FakeHost();
        resolver = new FakeResolver();
        ps = new PlayerSoundService(host, resolver, clock, PlayerSoundService.Config.defaults());
        alice = host.addPlayer("alice", true);
        bob = host.addPlayer("bob", true);
        carol = host.addPlayer("carol", false);

        resolver.results.put(A_URL, TrackResolver.Result.single(new Track(A_URL, "曲A", 60_000)));
        resolver.results.put(B_URL, TrackResolver.Result.single(new Track(B_URL, "曲B", 60_000)));
        resolver.results.put(LIST_URL, TrackResolver.Result.playlist(
                List.of(song("l1", 10_000), song("l2", 10_000), song("l3", 10_000)), false));
        resolver.results.put(LIVE_URL, TrackResolver.Result.single(new Track(LIVE_URL, "ラジオ", Track.LIVE)));
    }

    private void cmd(UUID who, String sub, String... args) {
        ps.command(who, false, sub, List.of(args));
    }

    private void all(UUID who, String sub, String... args) {
        ps.command(who, true, sub, List.of(args));
    }

    private Messages.Play lastPlay(UUID who, byte slotType) {
        List<Messages.Play> plays = host.sentOfType(who, Messages.Play.class).stream()
                .filter(p -> p.slot().slotType() == slotType).toList();
        return plays.get(plays.size() - 1);
    }

    // ---- 個人再生 ----

    @Test
    void 個人のplayは自分にだけPLAYを送る() {
        cmd(alice, "play", A_URL);
        Messages.Play play = lastPlay(alice, Messages.Slot.PERSONAL);
        assertEquals("曲A", play.title());
        assertEquals(clock.nowMs() + 500, play.originMs());
        assertEquals(List.of(), host.sentTo(bob));
        assertTrue(host.chatContains(alice, "再生します: 曲A"));
    }

    @Test
    void 二人が別々の曲を同時に流せる() {
        cmd(alice, "play", A_URL);
        cmd(bob, "play", B_URL);
        assertEquals("曲A", lastPlay(alice, Messages.Slot.PERSONAL).title());
        assertEquals("曲B", lastPlay(bob, Messages.Slot.PERSONAL).title());
    }

    @Test
    void mod未導入でも日本語の返事が出る() {
        cmd(carol, "play", A_URL);
        assertTrue(host.chatContains(carol, "再生します: 曲A"));
        cmd(carol, "pause");
        assertEquals("一時停止しました。", host.lastChat(carol));
        cmd(carol, "pause");
        assertEquals("再開しました。", host.lastChat(carol));
        cmd(carol, "list");
        assertTrue(host.chatContains(carol, "曲A"));
        cmd(carol, "stop");
        assertEquals(List.of(), host.sentTo(carol));
    }

    @Test
    void URLがhttp以外なら使い方を出し_取得しない() {
        cmd(alice, "play", "ftp://example.com/a.mp3");
        assertTrue(host.lastChat(alice).startsWith("使い方: /ps play"));
        cmd(alice, "play", "http://127.0.0.1/a.mp3");
        assertEquals("内部アドレスの URL は使えません。", host.lastChat(alice));
        assertEquals(List.of(), resolver.requested);
    }

    @Test
    void 対応していないURLは非対応と表示する() {
        cmd(alice, "play", "https://example.com/unknown");
        assertEquals("非対応のURLです。", host.lastChat(alice));
    }

    @Test
    void 取得に失敗したら理由を表示する() {
        resolver.results.put("https://example.com/bad",
                TrackResolver.Result.failure(TrackResolver.Result.Failure.FAILED, "接続できません"));
        cmd(alice, "play", "https://example.com/bad");
        assertEquals("取得に失敗しました: 接続できません", host.lastChat(alice));
    }

    @Test
    void 空のplayは再生できる曲がありませんと表示する() {
        cmd(alice, "play");
        assertEquals("再生できる曲がありません。", host.lastChat(alice));
    }

    @Test
    void playlistは順番に流れ_最後の曲のあとで止まる() {
        cmd(alice, "playlist", LIST_URL);
        assertEquals("l1", lastPlay(alice, Messages.Slot.PERSONAL).title());
        clock.advance(500 + 10_000);
        ps.tick();
        assertEquals("l2", lastPlay(alice, Messages.Slot.PERSONAL).title());
        clock.advance(500 + 10_000);
        ps.tick();
        clock.advance(500 + 10_000);
        ps.tick();
        assertEquals(1, host.sentOfType(alice, Messages.Stop.class).size());
        assertTrue(host.chatContains(alice, "最後の曲が終わったので、停止しました。"));
    }

    @Test
    void addはキューの後ろに足す() {
        cmd(alice, "play", A_URL);
        cmd(alice, "add", B_URL);
        assertEquals("1曲をキューに追加しました", host.lastChat(alice));
        cmd(alice, "list");
        assertTrue(host.chatContains(alice, "▶ 1. 曲A"));
        assertTrue(host.chatContains(alice, "  2. 曲B"));
    }

    @Test
    void ライブはLIVEと表示されskipできない() {
        cmd(alice, "play", LIVE_URL);
        cmd(alice, "list");
        assertTrue(host.chatContains(alice, "[LIVE]"));
        cmd(alice, "skip");
        assertEquals("ライブ配信では使えません。", host.lastChat(alice));
    }

    @Test
    void list_scoreは1回目で表示_2回目で消える() {
        cmd(alice, "play", A_URL);
        cmd(alice, "list", "score");
        assertEquals(List.of("▶1 曲A"), host.scores.get(alice));
        cmd(alice, "list", "score");
        assertFalse(host.scores.containsKey(alice));
    }

    @Test
    void ERRORを受けたら曲を飛ばす() {
        cmd(alice, "playlist", LIST_URL);
        Messages.Play first = lastPlay(alice, Messages.Slot.PERSONAL);
        ps.onError(alice, new Messages.Error(Messages.Slot.PERSONAL_SLOT, first.trackId(),
                Messages.Error.FETCH_FAILED, "404"));
        assertEquals("l2", lastPlay(alice, Messages.Slot.PERSONAL).title());
        assertTrue(host.chatContains(alice, "スキップしました: l1（404）"));
    }

    // ---- 共有 ----

    @Test
    void all_は自分の再生を共有にし_ほかのmod導入者へボタン付きのお知らせを出す() {
        cmd(alice, "play", A_URL);
        all(alice, "");
        assertTrue(ps.isListening(alice));
        assertEquals("曲A", lastPlay(alice, Messages.Slot.SHARED).title());
        // 個人再生は一時停止
        assertEquals(1, host.sentOfType(alice, Messages.Pause.class).size());
        assertEquals(Messages.Slot.PERSONAL, host.sentOfType(alice, Messages.Pause.class).get(0).slot().slotType());

        assertEquals("aliceさんが音楽を共有しています: 曲A", host.lastChat(bob));
        List<PlayerHost.Button> buttons = host.buttons.get(bob).get(0);
        assertEquals("/ps listen", buttons.get(0).command());
        assertEquals("/ps leave", buttons.get(1).command());
        assertFalse(host.chatContains(carol, "共有しています")); // mod 未導入には出さない
        assertFalse(host.chatContains(alice, "共有しています")); // 始めた本人にも出さない
    }

    @Test
    void 再生がないときのall_は共有しない() {
        all(alice, "");
        assertFalse(ps.isListening(alice));
        assertTrue(host.lastChat(alice).startsWith("共有できる再生がありません"));
    }

    @Test
    void 共有がないときのall_playは新しく共有を始める() {
        all(alice, "play", A_URL);
        assertTrue(ps.isListening(alice));
        assertEquals("曲A", lastPlay(alice, Messages.Slot.SHARED).title());
        assertTrue(host.chatContains(bob, "aliceさんが音楽を共有しています: 曲A"));
    }

    @Test
    void 取得に失敗したall_playでは共有が作られない() {
        all(alice, "play", "https://example.com/unknown");
        assertFalse(ps.isListening(alice));
        listenFails();
    }

    private void listenFails() {
        cmd(bob, "listen");
        assertEquals("共有している再生がありません。", host.lastChat(bob));
    }

    @Test
    void 共有がないときのall_skipなどは共有がありませんと表示する() {
        all(alice, "skip");
        assertEquals("共有している再生がありません。", host.lastChat(alice));
        all(alice, "pause");
        assertEquals("共有している再生がありません。", host.lastChat(alice));
        all(alice, "play");
        assertEquals("共有している再生がありません。", host.lastChat(alice));
    }

    @Test
    void listenすると今の位置から鳴り_個人再生は一時停止する() {
        cmd(alice, "play", A_URL);
        all(alice, "");
        cmd(bob, "play", B_URL);
        clock.advance(5_000);
        cmd(bob, "pause"); // 共有を聞く前なので確認なしで個人に効く
        Messages.Play shared = lastPlay(alice, Messages.Slot.SHARED);

        cmd(bob, "listen");
        assertTrue(ps.isListening(bob));
        Messages.Play heard = lastPlay(bob, Messages.Slot.SHARED);
        assertEquals(shared.originMs(), heard.originMs()); // 同じ基準時刻 = 同じ位置
        assertEquals(shared.trackId(), heard.trackId());
        // bob の個人再生はすでに一時停止中なので、新たに PAUSE は送らない
    }

    @Test
    void listenで再生中の個人再生は一時停止し_leaveで止まった位置から再開する() {
        cmd(alice, "play", A_URL);
        all(alice, "");
        cmd(bob, "play", B_URL);
        clock.advance(10_500);
        cmd(bob, "listen");
        List<Messages.Pause> pauses = host.sentOfType(bob, Messages.Pause.class);
        assertEquals(1, pauses.size());
        assertEquals(Messages.Slot.PERSONAL, pauses.get(0).slot().slotType());
        assertEquals(10_000, pauses.get(0).positionMs());

        clock.advance(20_000);
        cmd(bob, "leave");
        assertFalse(ps.isListening(bob));
        assertEquals(1, host.sentOfType(bob, Messages.Stop.class).size());
        assertEquals(Messages.Slot.SHARED, host.sentOfType(bob, Messages.Stop.class).get(0).slot().slotType());
        Messages.Resume resume = host.sentOfType(bob, Messages.Resume.class).get(0);
        assertEquals(clock.nowMs() + 500 - 10_000, resume.originMs());
        assertEquals(Messages.Slot.PERSONAL, resume.slot().slotType());
    }

    @Test
    void leaveしても共有は続き_またlistenできる() {
        cmd(alice, "play", A_URL);
        all(alice, "");
        cmd(bob, "listen");
        cmd(bob, "leave");
        cmd(bob, "listen");
        assertTrue(ps.isListening(bob));
        assertTrue(ps.isListening(alice));
    }

    @Test
    void 聞いていない人のleaveとlistenの二重実行は案内を出す() {
        cmd(bob, "leave");
        assertEquals("共有を聞いていません。", host.lastChat(bob));
        cmd(alice, "play", A_URL);
        all(alice, "");
        cmd(bob, "listen");
        cmd(bob, "listen");
        assertEquals("すでに共有を聞いています。", host.lastChat(bob));
    }

    @Test
    void 聞いている人はall_で共有を操作でき_全員に反映される() {
        cmd(alice, "playlist", LIST_URL);
        all(alice, "");
        cmd(bob, "listen");
        int aliceBefore = host.sentOfType(alice, Messages.Play.class).size();
        all(bob, "skip");
        assertEquals("l2", lastPlay(alice, Messages.Slot.SHARED).title());
        assertEquals("l2", lastPlay(bob, Messages.Slot.SHARED).title());
        assertEquals(aliceBefore + 1, host.sentOfType(alice, Messages.Play.class).size());
    }

    @Test
    void 聞いていない人は共有を操作できない() {
        cmd(alice, "play", A_URL);
        all(alice, "");
        all(bob, "skip");
        assertTrue(host.lastChat(bob).startsWith("共有を聞いていないので、操作できません。"));
        all(bob, "play", B_URL);
        assertEquals("曲A", lastPlay(alice, Messages.Slot.SHARED).title()); // 置き換わっていない
    }

    @Test
    void all_stopは共有を終え_聞いていた人に知らせて個人再生を再開する() {
        cmd(alice, "play", A_URL);
        all(alice, "");
        cmd(bob, "play", B_URL);
        cmd(bob, "listen");
        all(alice, "stop");
        assertFalse(ps.isListening(alice));
        assertFalse(ps.isListening(bob));
        assertEquals("共有を終了しました。", host.lastChat(bob));
        assertEquals(1, host.sentOfType(bob, Messages.Resume.class).size());
        // 次の共有はまた許可が要る
        listenFails();
    }

    @Test
    void 別の人がall_で置き換えると_聞いていた人は解除される() {
        cmd(alice, "play", A_URL);
        all(alice, "");
        cmd(bob, "play", B_URL);
        all(bob, ""); // alice は聞いている側。bob は聞いていないので置き換えられる
        assertTrue(ps.isListening(bob));
        assertFalse(ps.isListening(alice));
        assertTrue(host.chatContains(alice, "bobさんが共有を置き換えました。"));
        assertEquals("曲B", lastPlay(bob, Messages.Slot.SHARED).title());
        assertTrue(host.chatContains(alice, "bobさんが音楽を共有しています: 曲B")); // 新しい共有のお知らせ
    }

    @Test
    void 最後の聞き手が退出すると共有は終わる() {
        cmd(alice, "play", A_URL);
        all(alice, "");
        ps.onQuit(alice);
        listenFails();
    }

    @Test
    void 共有が始まったあとに参加した人にもお知らせを出す() {
        cmd(alice, "play", A_URL);
        all(alice, "");
        UUID dave = host.addPlayer("dave", true);
        ps.onHello(dave);
        assertEquals("aliceさんが音楽を共有しています: 曲A", host.lastChat(dave));
        ps.onHello(alice); // 聞いている人には出さない
        assertFalse(host.chatContains(alice, "共有しています"));
    }

    @Test
    void 共有を聞いているあいだ_自分の操作は確認になる() {
        cmd(alice, "play", A_URL);
        all(alice, "");
        cmd(bob, "listen");
        cmd(bob, "play", B_URL);
        assertEquals("共有を聞いています。聞くのをやめて、自分の再生を実行しますか？", host.lastChat(bob));
        List<List<PlayerHost.Button>> all = host.buttons.get(bob);
        List<PlayerHost.Button> buttons = all.get(all.size() - 1);
        assertEquals("/ps confirm", buttons.get(0).command());
        assertEquals("/ps cancel", buttons.get(1).command());
        assertTrue(ps.isListening(bob)); // まだ何も変わっていない
    }

    @Test
    void confirmは聞くのをやめて_打った操作を個人再生に行う() {
        cmd(alice, "play", A_URL);
        all(alice, "");
        cmd(bob, "listen");
        cmd(bob, "play", B_URL);
        cmd(bob, "confirm");
        assertFalse(ps.isListening(bob));
        assertEquals("曲B", lastPlay(bob, Messages.Slot.PERSONAL).title());
        assertTrue(ps.isListening(alice)); // 共有はほかの人のために続く
        assertEquals("曲A", lastPlay(alice, Messages.Slot.SHARED).title());
    }

    @Test
    void cancelは何も変えない() {
        cmd(alice, "play", A_URL);
        all(alice, "");
        cmd(bob, "listen");
        cmd(bob, "stop");
        cmd(bob, "cancel");
        assertEquals("キャンセルしました。", host.lastChat(bob));
        assertTrue(ps.isListening(bob));
        cmd(bob, "confirm");
        assertEquals("確認待ちの操作がありません。もう一度実行してください。", host.lastChat(bob));
    }

    @Test
    void 確認は30秒で無効になる() {
        cmd(alice, "play", A_URL);
        all(alice, "");
        cmd(bob, "listen");
        cmd(bob, "skip");
        clock.advance(30_001);
        cmd(bob, "confirm");
        assertEquals("確認待ちの操作がありません。もう一度実行してください。", host.lastChat(bob));
        assertTrue(ps.isListening(bob));
    }

    @Test
    void listとlist_scoreは聞いているあいだも確認なしで実行する() {
        cmd(alice, "play", A_URL);
        all(alice, "");
        cmd(bob, "listen");
        int buttonsBefore = host.buttons.get(bob).size();
        cmd(bob, "list");
        assertEquals("キューは空です。", host.lastChat(bob)); // bob 自身の個人キュー
        assertEquals(buttonsBefore, host.buttons.get(bob).size()); // 確認のボタンは出ない
        all(bob, "list", "score");
        assertTrue(host.scores.containsKey(bob));
    }

    @Test
    void キューが空のlist_scoreは空と返し_切り替え状態を変えない() {
        cmd(alice, "list", "score");
        assertEquals("キューが空です。", host.lastChat(alice));
        assertFalse(host.scores.containsKey(alice));
        cmd(alice, "play", A_URL);
        cmd(alice, "list", "score"); // 空の実行が状態を反転させていなければ、ここで表示される
        assertTrue(host.scores.containsKey(alice));
    }

    @Test
    void 表示中のサイドバーはキューや曲の変化に合わせて更新される() {
        cmd(alice, "playlist", LIST_URL);
        cmd(alice, "list", "score");
        assertEquals(List.of("▶1 l1", " 2 l2", " 3 l3"), host.scores.get(alice));
        cmd(alice, "skip");
        assertEquals(List.of(" 1 l1", "▶2 l2", " 3 l3"), host.scores.get(alice));
        cmd(alice, "add", A_URL);
        assertEquals(4, host.scores.get(alice).size());
        clock.advance(500 + 10_000); // 曲が自動で進む
        ps.tick();
        assertEquals("▶3 l3", host.scores.get(alice).get(2));
        cmd(alice, "stop");
        assertFalse(host.scores.containsKey(alice)); // 空になったら消える
        cmd(alice, "play", A_URL);
        assertFalse(host.scores.containsKey(alice)); // 表示中の状態も解除されている
        cmd(alice, "list", "score");
        assertTrue(host.scores.containsKey(alice));
    }

    @Test
    void 共有キューの表示も更新され_共有が終わると消える() {
        cmd(alice, "playlist", LIST_URL);
        all(alice, "");
        cmd(bob, "listen");
        all(bob, "list", "score");
        assertEquals("▶1 l1", host.scores.get(bob).get(0));
        all(alice, "skip");
        assertEquals("▶2 l2", host.scores.get(bob).get(1));
        all(alice, "stop");
        assertFalse(host.scores.containsKey(bob));
    }

    @Test
    void 確認から続けたpauseは_個人再生の再開より操作の結果を優先する() {
        cmd(bob, "play", B_URL);
        cmd(alice, "play", A_URL);
        all(alice, "");
        cmd(bob, "listen"); // bob の個人再生は一時停止
        cmd(bob, "pause");
        cmd(bob, "confirm");
        assertFalse(ps.isListening(bob));
        List<Messages> sent = host.sentTo(bob);
        // 最後は一時停止になっている（再開 → 一時停止の順）
        assertTrue(sent.get(sent.size() - 1) instanceof Messages.Pause);
        assertEquals("一時停止しました。", host.lastChat(bob));
    }

    @Test
    void 共有を聞いている人のall_は先にleaveを案内する() {
        cmd(alice, "play", A_URL);
        all(alice, "");
        all(alice, "");
        assertTrue(host.lastChat(alice).startsWith("すでに共有を聞いています"));
    }

    @Test
    void 共有のERRORは聞いている誰からでも受け付け_1回だけ飛ばす() {
        cmd(alice, "playlist", LIST_URL);
        all(alice, "");
        cmd(bob, "listen");
        Messages.Play play = lastPlay(alice, Messages.Slot.SHARED);
        Messages.Error error = new Messages.Error(Messages.Slot.SHARED_SLOT, play.trackId(),
                Messages.Error.DECODE_FAILED, "");
        ps.onError(alice, error);
        ps.onError(bob, error);
        assertEquals("l2", lastPlay(alice, Messages.Slot.SHARED).title());
        assertEquals(2, host.sentOfType(bob, Messages.Play.class).stream()
                .filter(p -> p.slot().slotType() == Messages.Slot.SHARED).count());
    }

    @Test
    void 共有のSYNCは聞いている全員へ設定の間隔で送る() {
        cmd(alice, "play", A_URL);
        all(alice, "");
        cmd(bob, "listen");
        clock.advance(30_000);
        ps.tick();
        assertEquals(1, host.sentOfType(alice, Messages.Sync.class).size());
        assertEquals(1, host.sentOfType(bob, Messages.Sync.class).size());
    }

    @Test
    void live_ヘルパーはライブ扱い() {
        assertTrue(live("x").live());
    }
}
