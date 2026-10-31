package com.oogata.radiomod.audio;

import com.oogata.radiomod.RadioMod;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;

/**
 * LavaPlayer の設定。URL から音声を取得して、PCM（16bit・ステレオ・リトルエンディアン）にデコードする。
 * 対応: mp3・aac・ogg・wav などの直リンク、Icecast/Shoutcast、YouTube、SoundCloud、Bandcamp。
 */
public final class Decoder {

    public static final int SAMPLE_RATE = 48000;
    public static final int CHANNELS = 2;
    /** LavaPlayer が1フレームに入れるサンプル数（20ms）。 */
    public static final int FRAME_SAMPLES = 960;
    public static final Pcm16AudioDataFormat FORMAT =
            new Pcm16AudioDataFormat(CHANNELS, SAMPLE_RATE, FRAME_SAMPLES, false);

    private static AudioPlayerManager manager;

    private Decoder() {
    }

    private static synchronized AudioPlayerManager manager() {
        if (manager == null) {
            NativeExtractor.prepare();
            DefaultAudioPlayerManager m = new DefaultAudioPlayerManager();
            m.getConfiguration().setOutputFormat(FORMAT);
            try {
                m.registerSourceManager(YoutubeClients.createSource());
            } catch (Throwable t) {
                // YouTube が使えなくても、ほかの音源は鳴らせるようにする。
                RadioMod.LOGGER.warn("YouTube の音源を初期化できませんでした: {}", t.toString());
            }
            m.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
            m.registerSourceManager(new BandcampAudioSourceManager());
            m.registerSourceManager(new HttpAudioSourceManager(MediaContainerRegistry.DEFAULT_REGISTRY));
            manager = m;
        }
        return manager;
    }

    public static AudioPlayerManager loader() {
        return manager();
    }

    public static AudioPlayer createPlayer() {
        return manager().createPlayer();
    }
}
