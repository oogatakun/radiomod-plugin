package com.oogata.radiomod.audio;

import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.Android;
import dev.lavalink.youtube.clients.Ios;
import dev.lavalink.youtube.clients.MWeb;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.skeleton.Client;

/**
 * YouTube の音声を、ログインなしで取るためのクライアントの組み合わせ。
 * 先頭から順に試され、情報が取れたものが使われる。
 * <ul>
 *   <li>IOS・ANDROID: 実測でログインなしの再生まで通った。先に試す。</li>
 *   <li>WEB・MWEB: 情報は取れるが、再生時に署名の解読で失敗することがあるため、最後の予備とする。</li>
 *   <li>ANDROID_VR・ANDROID_MUSIC・WEBEMBEDDED・TV・MUSIC: ログインを求められるなどで使えなかったため入れない。</li>
 * </ul>
 * YouTube 側の仕様変更で、通るクライアントは変わりうる。
 */
public final class YoutubeClients {

    private YoutubeClients() {
    }

    public static Client[] create() {
        return new Client[]{new Ios(), new Android(), new Web(), new MWeb()};
    }

    public static YoutubeAudioSourceManager createSource() {
        return new YoutubeAudioSourceManager(true, create());
    }
}
