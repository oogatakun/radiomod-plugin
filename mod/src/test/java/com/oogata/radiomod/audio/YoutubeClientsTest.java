package com.oogata.radiomod.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lavalink.youtube.clients.skeleton.Client;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class YoutubeClientsTest {

    @Test
    void クライアントはIOS_ANDROID_WEB_MWEBの順に並ぶ() {
        List<String> ids = Arrays.stream(YoutubeClients.create()).map(Client::getIdentifier).toList();
        assertEquals(List.of("IOS", "ANDROID", "WEB", "MWEB"), ids);
    }

    @Test
    void ログインが必要なクライアントは含まない() {
        List<String> ids = Arrays.stream(YoutubeClients.create()).map(Client::getIdentifier).toList();
        assertTrue(!ids.contains("TV") && !ids.contains("ANDROID_VR") && !ids.contains("MUSIC"));
    }

    @Test
    void 音源を作れる() {
        assertEquals(4, YoutubeClients.createSource().getClients().length);
    }
}
