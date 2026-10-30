package com.oogata.radiomod.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 手元のHTTPサーバーから WAV を取得して、PCM にデコードできることを確かめる（ネット接続は使わない）。 */
class DecoderTest {

    private static HttpServer server;
    private static String url;

    /** 2秒の 440Hz ステレオ 16bit 44.1kHz の WAV。 */
    private static byte[] wav() {
        int rate = 44100;
        int seconds = 2;
        int frames = rate * seconds;
        ByteBuffer b = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + frames * 4).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 2).putInt(rate)
                .putInt(rate * 4).putShort((short) 4).putShort((short) 16);
        b.put("data".getBytes()).putInt(frames * 4);
        for (int i = 0; i < frames; i++) {
            short v = (short) (Math.sin(2 * Math.PI * 440 * i / rate) * 8000);
            b.putShort(v).putShort(v);
        }
        return b.array();
    }

    @BeforeAll
    static void start() throws Exception {
        byte[] body = wav();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tone.wav", ex -> {
            ex.getResponseHeaders().add("Content-Type", "audio/wav");
            ex.getResponseHeaders().add("Accept-Ranges", "bytes");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/tone.wav";
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    private static AudioTrack load(String u) throws Exception {
        CompletableFuture<AudioTrack> f = new CompletableFuture<>();
        Decoder.loader().loadItem(u, new AudioLoadResultHandler() {
            public void trackLoaded(AudioTrack t) {
                f.complete(t);
            }

            public void playlistLoaded(AudioPlaylist p) {
                f.completeExceptionally(new IllegalStateException("playlist"));
            }

            public void noMatches() {
                f.completeExceptionally(new IllegalStateException("noMatches"));
            }

            public void loadFailed(FriendlyException e) {
                f.completeExceptionally(e);
            }
        });
        return f.get(15, TimeUnit.SECONDS);
    }

    private static ByteArrayOutputStream drain(AudioPlayer player, int maxMillis, long[] firstTimecode) throws Exception {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        long deadline = System.currentTimeMillis() + maxMillis;
        int idle = 0;
        while (System.currentTimeMillis() < deadline && idle < 200) {
            AudioFrame frame = player.provide();
            if (frame == null) {
                idle++;
                Thread.sleep(10);
                continue;
            }
            idle = 0;
            if (firstTimecode[0] < 0) {
                firstTimecode[0] = frame.getTimecode();
            }
            pcm.write(frame.getData());
        }
        return pcm;
    }

    @Test
    void decodesWavToPcm() throws Exception {
        AudioTrack track = load(url);
        assertNotNull(track);
        assertTrue(track.isSeekable());
        assertEquals(2000, track.getDuration(), 50);
        AudioPlayer player = Decoder.createPlayer();
        player.playTrack(track);
        long[] first = {-1};
        ByteArrayOutputStream pcm = drain(player, 15000, first);
        player.destroy();
        // 48kHz・ステレオ・16bit = 1秒で 192000 バイト。2秒ぶん。
        assertEquals(2 * 48000 * 4, pcm.size(), 48000 * 4 * 0.1);
        assertEquals(0, first[0]);
        byte[] data = pcm.toByteArray();
        boolean nonSilent = false;
        for (int i = 0; i < data.length; i += 2) {
            if (data[i] != 0 || data[i + 1] != 0) {
                nonSilent = true;
                break;
            }
        }
        assertTrue(nonSilent, "無音ではない");
    }

    @Test
    void seekStartsFromRequestedPosition() throws Exception {
        AudioTrack track = load(url);
        track.setPosition(1000);
        AudioPlayer player = Decoder.createPlayer();
        player.playTrack(track);
        long[] first = {-1};
        ByteArrayOutputStream pcm = drain(player, 15000, first);
        player.destroy();
        assertEquals(1000, first[0], 60);
        assertEquals(1 * 48000 * 4, pcm.size(), 48000 * 4 * 0.1);
    }
}
