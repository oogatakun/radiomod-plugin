package com.oogata.radioplugin.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oogata.radioplugin.net.Messages;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RadioPlayerServiceTest {

    private static class FakeRadios implements RadioPlayerService.Radios {
        final Map<UUID, Messages.RadioTarget> targets = new HashMap<>();
        final Map<UUID, String> nowPlaying = new HashMap<>();
        final Map<UUID, PlaybackEngine.Snapshot> saved = new HashMap<>();

        @Override
        public Messages.RadioTarget target(UUID radioId) {
            return targets.get(radioId);
        }

        @Override
        public void setNowPlaying(UUID radioId, String title) {
            if (title == null) {
                nowPlaying.remove(radioId);
            } else {
                nowPlaying.put(radioId, title);
            }
        }

        @Override
        public void changed(UUID radioId, PlaybackEngine.Snapshot snapshot) {
            saved.put(radioId, snapshot);
        }
    }

    private Fakes.FakeClock clock;
    private Fakes.FakeHost host;
    private Fakes.FakeResolver resolver;
    private FakeRadios radios;
    private RadioPlayerService service;
    private UUID actor;
    private UUID modPlayer;
    private UUID plainPlayer;
    private UUID radioA;
    private UUID radioB;

    @BeforeEach
    void setUp() {
        clock = new Fakes.FakeClock();
        host = new Fakes.FakeHost();
        resolver = new Fakes.FakeResolver();
        radios = new FakeRadios();
        service = new RadioPlayerService(host, resolver, clock, PlaybackEngine.Settings.defaults(), radios);
        actor = host.addPlayer("操作する人", true);
        modPlayer = host.addPlayer("mod の人", true);
        plainPlayer = host.addPlayer("mod なしの人", false);
        radioA = UUID.randomUUID();
        radioB = UUID.randomUUID();
        radios.targets.put(radioA, new Messages.RadioTarget("minecraft:overworld", 1, 64.5, 2, 24f));
        radios.targets.put(radioB, new Messages.RadioTarget("minecraft:the_nether", 5, 70.5, 6, 24f));
        resolver.results.put("https://example.com/a.mp3", TrackResolver.Result.single(Fakes.song("a", 60_000)));
        resolver.results.put("https://example.com/b.mp3", TrackResolver.Result.single(Fakes.song("b", 90_000)));
        resolver.results.put("https://example.com/list",
                TrackResolver.Result.playlist(List.of(Fakes.song("l1", 10_000), Fakes.song("l2", 10_000)), false));
    }

    private void cmd(UUID radio, String sub, String... args) {
        service.command(actor, radio, "r", sub, List.of(args));
    }

    @Test
    void playはラジオのスロットでmod導入済みの全員に位置つきで送る() {
        cmd(radioA, "play", "https://example.com/a.mp3");

        for (UUID p : List.of(actor, modPlayer)) {
            List<Messages.Play> plays = host.sentOfType(p, Messages.Play.class);
            assertEquals(1, plays.size());
            Messages.Play play = plays.get(0);
            assertEquals(Messages.Slot.radio(radioA), play.slot());
            assertEquals("a", play.title());
            assertEquals(clock.nowMs() + 500, play.originMs());
            assertEquals(1.0, play.radio().x());
            assertEquals("minecraft:overworld", play.radio().dimension());
        }
        assertTrue(host.sentTo(plainPlayer).isEmpty());
        assertEquals("a", radios.nowPlaying.get(radioA));
    }

    @Test
    void ラジオごとに独立して別々の曲が鳴る() {
        cmd(radioA, "play", "https://example.com/a.mp3");
        cmd(radioB, "play", "https://example.com/b.mp3");

        List<Messages.Play> plays = host.sentOfType(modPlayer, Messages.Play.class);
        assertEquals(2, plays.size());
        assertEquals(Messages.Slot.radio(radioA), plays.get(0).slot());
        assertEquals(Messages.Slot.radio(radioB), plays.get(1).slot());
        assertEquals("a", radios.nowPlaying.get(radioA));
        assertEquals("b", radios.nowPlaying.get(radioB));

        cmd(radioA, "stop");
        assertNull(radios.nowPlaying.get(radioA));
        assertEquals("b", radios.nowPlaying.get(radioB));
        assertEquals(PlaybackEngine.State.PLAYING, service.snapshot(radioB).state());
    }

    @Test
    void 一時停止と再開_停止でSTOPを送る() {
        cmd(radioA, "play", "https://example.com/a.mp3");
        clock.advance(10_000);
        cmd(radioA, "pause");
        assertEquals(1, host.sentOfType(modPlayer, Messages.Pause.class).size());
        assertEquals(9_500, host.sentOfType(modPlayer, Messages.Pause.class).get(0).positionMs());
        cmd(radioA, "pause");
        assertEquals(1, host.sentOfType(modPlayer, Messages.Resume.class).size());
        cmd(radioA, "stop");
        assertEquals(Messages.Slot.radio(radioA), host.sentOfType(modPlayer, Messages.Stop.class).get(0).slot());
    }

    @Test
    void 曲の終わりで次へ進み_SYNCを再送する() {
        cmd(radioA, "playlist", "https://example.com/list");
        clock.advance(11_000); // 1曲目 (10秒) の終わり
        service.tick();
        List<Messages.Play> plays = host.sentOfType(modPlayer, Messages.Play.class);
        assertEquals(2, plays.size());
        assertEquals("l2", plays.get(1).title());

        cmd(radioB, "play", "https://example.com/b.mp3");
        clock.advance(31_000);
        service.tick();
        assertEquals(Messages.Slot.radio(radioB), host.sentOfType(modPlayer, Messages.Sync.class).get(0).slot());
    }

    @Test
    void mod導入後のHELLOで再生中のラジオ全部の状態が届く() {
        cmd(radioA, "play", "https://example.com/a.mp3");
        cmd(radioB, "play", "https://example.com/b.mp3");
        clock.advance(5_000);
        cmd(radioB, "pause");
        UUID late = host.addPlayer("あとから来た人", true);

        service.onHello(late);

        List<Messages> got = host.sentTo(late);
        // A の PLAY、B の PLAY と PAUSE
        assertEquals(3, got.size());
        Messages.Play a = (Messages.Play) got.get(0);
        assertEquals(Messages.Slot.radio(radioA), a.slot());
        assertEquals(service.snapshot(radioA).queue().get(0).url(), a.url());
        assertEquals(Messages.Slot.radio(radioB), ((Messages.Play) got.get(1)).slot());
        assertTrue(got.get(2) instanceof Messages.Pause);
    }

    @Test
    void 撤去するとSTOPを送り再生状態を捨てる() {
        cmd(radioA, "play", "https://example.com/a.mp3");
        service.onRadioRemoved(radioA);

        assertEquals(1, host.sentOfType(modPlayer, Messages.Stop.class).size());
        assertNull(service.snapshot(radioA));
        assertNull(radios.nowPlaying.get(radioA));
        UUID late = host.addPlayer("late", true);
        service.onHello(late);
        assertTrue(host.sentTo(late).isEmpty());
    }

    @Test
    void 移動するとMOVEを送る() {
        service.onRadioMoved(radioA, "minecraft:overworld", 3, 4, 5);
        Messages.Move move = host.sentOfType(modPlayer, Messages.Move.class).get(0);
        assertEquals(Messages.Slot.radio(radioA), move.slot());
        assertEquals(3, move.x());
        assertTrue(host.sentTo(plainPlayer).isEmpty());
    }

    @Test
    void ERRORで失敗した曲を飛ばす() {
        cmd(radioA, "playlist", "https://example.com/list");
        long trackId = host.sentOfType(modPlayer, Messages.Play.class).get(0).trackId();

        service.onError(new Messages.Error(Messages.Slot.radio(radioA), trackId, Messages.Error.FETCH_FAILED, "x"));

        assertEquals(1, service.snapshot(radioA).index());
        // 個人スロットの ERROR は無視する
        service.onError(new Messages.Error(Messages.Slot.PERSONAL_SLOT, 1, Messages.Error.OTHER, ""));
        assertEquals(1, service.snapshot(radioA).index());
    }

    @Test
    void 保存した状態から曲の頭で再開できる() {
        cmd(radioA, "playlist", "https://example.com/list", "loop");
        clock.advance(10_500);
        service.tick(); // 2曲目へ
        PlaybackEngine.Snapshot snap = radios.saved.get(radioA);
        assertNotNull(snap);
        assertEquals(1, snap.index());
        assertEquals(PlaybackEngine.Loop.PLAYLIST, snap.loop());

        RadioPlayerService fresh = new RadioPlayerService(host, resolver, clock, PlaybackEngine.Settings.defaults(),
                new FakeRadios() {
                    {
                        targets.putAll(radios.targets);
                    }
                });
        fresh.restore(radioA, new PlaybackEngine.Snapshot(snap.queue(), snap.index(), snap.loop(), snap.state(), 0));

        PlaybackEngine.Snapshot restored = fresh.snapshot(radioA);
        assertEquals(1, restored.index());
        assertEquals(PlaybackEngine.State.PLAYING, restored.state());
        assertEquals(0, restored.positionMs());
        Messages.Play last = host.sentOfType(modPlayer, Messages.Play.class).get(host.sentOfType(modPlayer, Messages.Play.class).size() - 1);
        assertEquals("l2", last.title());
    }

    @Test
    void 使い方の誤りと内部アドレスは実行者に伝えて何もしない() {
        cmd(radioA, "play", "ftp://example.com/a.mp3");
        cmd(radioA, "play", "http://localhost/a.mp3");
        cmd(radioA, "add");
        cmd(radioA, "list", "foo");
        assertTrue(host.sentTo(modPlayer).isEmpty());
        assertEquals(4, host.chat.get(actor).size());
        assertNull(service.snapshot(radioA));
    }

    @Test
    void 取得に失敗したら伝える() {
        cmd(radioA, "play", "https://example.com/unknown.mp3");
        assertTrue(host.chatContains(actor, "非対応のURLです"));
        assertTrue(host.sentTo(modPlayer).isEmpty());
    }

    @Test
    void listはキューを表示し_空なら空と言う() {
        cmd(radioA, "list");
        assertEquals("キューは空です。", host.lastChat(actor));
        cmd(radioA, "playlist", "https://example.com/list");
        List<String> before = new ArrayList<>(host.chat.get(actor));
        cmd(radioA, "list");
        assertTrue(host.chat.get(actor).size() > before.size());
        assertTrue(host.chatContains(actor, "l1"));
    }

    @Test
    void addはキューの後ろに足し_停止中なら再生を始める() {
        cmd(radioA, "add", "https://example.com/a.mp3");
        assertEquals(PlaybackEngine.State.PLAYING, service.snapshot(radioA).state());
        cmd(radioA, "add", "https://example.com/b.mp3");
        assertEquals(2, service.snapshot(radioA).queue().size());
        assertEquals(0, service.snapshot(radioA).index());
    }
}
