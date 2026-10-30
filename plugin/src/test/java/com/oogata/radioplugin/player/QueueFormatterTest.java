package com.oogata.radioplugin.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueueFormatterTest {

    private static List<Track> queue(int n) {
        List<Track> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add(new Track("https://example.com/" + i, "曲" + i, 60_000));
        }
        return list;
    }

    @Test
    void 長さの表示() {
        assertEquals("0:05", QueueFormatter.duration(5_000));
        assertEquals("3:45", QueueFormatter.duration(225_000));
        assertEquals("1:02:03", QueueFormatter.duration(3_723_000));
        assertEquals("LIVE", QueueFormatter.duration(-1));
    }

    @Test
    void 十五行以内なら全部出して_再生中に印を付ける() {
        List<String> lines = QueueFormatter.scoreLines(queue(3), 1, true);
        assertEquals(List.of(" 1 曲1", "▶2 曲2", " 3 曲3"), lines);
    }

    @Test
    void 十五行を超えたら再生中の曲の前後を中心に出す() {
        List<String> lines = QueueFormatter.scoreLines(queue(40), 20, true);
        assertEquals(15, lines.size());
        assertEquals(" 14 曲14", lines.get(0));
        assertEquals("▶21 曲21", lines.get(7));
        assertEquals(" 28 曲28", lines.get(14));
    }

    @Test
    void 先頭と末尾では窓が端で止まる() {
        List<String> head = QueueFormatter.scoreLines(queue(40), 0, true);
        assertEquals("▶1 曲1", head.get(0));
        assertEquals(15, head.size());
        List<String> tail = QueueFormatter.scoreLines(queue(40), 39, true);
        assertEquals(" 26 曲26", tail.get(0));
        assertEquals("▶40 曲40", tail.get(14));
    }

    @Test
    void 長いタイトルは切り詰める() {
        List<Track> q = List.of(new Track("https://example.com/x", "あ".repeat(60), 1000));
        String line = QueueFormatter.scoreLines(q, 0, false).get(0);
        assertTrue(line.endsWith("…"));
        assertTrue(line.length() <= 30);
    }

    @Test
    void チャットの表示は番号とタイトルと長さを並べる() {
        PlaybackEngine engine = new PlaybackEngine(Clock.SYSTEM, PlaybackEngine.Settings.defaults(),
                new java.util.concurrent.atomic.AtomicLong()::incrementAndGet, new Fakes.RecordingOutput());
        engine.playPlaylist(List.of(new Track("https://e/1", "曲1", 65_000),
                new Track("https://e/2", "ラジオ", Track.LIVE)), false);
        List<String> lines = QueueFormatter.chatLines(engine);
        assertEquals("キュー（2曲） 状態: 再生中 / loop: なし", lines.get(0));
        assertEquals("▶ 1. 曲1 [1:05]", lines.get(1));
        assertEquals("  2. ラジオ [LIVE]", lines.get(2));
    }
}
