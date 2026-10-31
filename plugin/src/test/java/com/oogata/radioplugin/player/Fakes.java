package com.oogata.radioplugin.player;

import com.oogata.radioplugin.net.Messages;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** 再生まわりのテスト用の偽物。 */
final class Fakes {

    private Fakes() {
    }

    static final class FakeClock implements Clock {
        long now = 1_000_000;

        @Override
        public long nowMs() {
            return now;
        }

        void advance(long ms) {
            now += ms;
        }
    }

    /** 呼ばれた順に、エンジンの出力を文字列で記録する。 */
    static final class RecordingOutput implements PlaybackEngine.Output {
        final List<String> events = new ArrayList<>();
        final List<Long> origins = new ArrayList<>();

        @Override
        public void play(Track track, long trackId, long originMs) {
            events.add("play:" + track.title());
            origins.add(originMs);
        }

        @Override
        public void pause(long positionMs) {
            events.add("pause:" + positionMs);
        }

        @Override
        public void resume(long originMs) {
            events.add("resume");
            origins.add(originMs);
        }

        @Override
        public void stop() {
            events.add("stop");
        }

        @Override
        public void sync(long originMs) {
            events.add("sync");
            origins.add(originMs);
        }

        @Override
        public void ended() {
            events.add("ended");
        }
    }

    static final class FakeResolver implements TrackResolver {
        final Map<String, Result> results = new HashMap<>();
        final List<String> requested = new ArrayList<>();

        @Override
        public void resolve(String url, Consumer<Result> callback) {
            requested.add(url);
            callback.accept(results.getOrDefault(url, Result.failure(Result.Failure.UNSUPPORTED, "")));
        }
    }

    static final class FakeHost implements PlayerHost {
        final Map<UUID, String> names = new HashMap<>();
        final Set<UUID> online = new HashSet<>();
        final Set<UUID> mods = new HashSet<>();
        final Map<UUID, List<Messages>> sent = new HashMap<>();
        final Map<UUID, List<String>> chat = new HashMap<>();
        final Map<UUID, List<List<Button>>> buttons = new HashMap<>();
        final Map<UUID, List<String>> announced = new HashMap<>();
        final Map<UUID, List<String>> scores = new HashMap<>();

        UUID addPlayer(String name, boolean mod) {
            UUID id = UUID.nameUUIDFromBytes(name.getBytes());
            names.put(id, name);
            online.add(id);
            if (mod) {
                mods.add(id);
            }
            return id;
        }

        @Override
        public String nameOf(UUID player) {
            return names.getOrDefault(player, "誰か");
        }

        @Override
        public boolean isOnline(UUID player) {
            return online.contains(player);
        }

        @Override
        public boolean hasMod(UUID player) {
            return mods.contains(player);
        }

        @Override
        public Collection<UUID> modPlayers() {
            List<UUID> result = new ArrayList<>();
            for (UUID id : online) {
                if (mods.contains(id)) {
                    result.add(id);
                }
            }
            return result;
        }

        @Override
        public void send(UUID player, Messages message) {
            if (mods.contains(player)) {
                sent.computeIfAbsent(player, k -> new ArrayList<>()).add(message);
            }
        }

        @Override
        public void tell(UUID player, String text) {
            chat.computeIfAbsent(player, k -> new ArrayList<>()).add(text);
        }

        @Override
        public void tellButtons(UUID player, String text, List<Button> list) {
            tell(player, text);
            buttons.computeIfAbsent(player, k -> new ArrayList<>()).add(list);
        }

        @Override
        public void announceTrack(UUID player, String title) {
            announced.computeIfAbsent(player, k -> new ArrayList<>()).add(title);
        }

        @Override
        public void showScore(UUID player, String title, List<String> lines) {
            scores.put(player, lines);
        }

        @Override
        public void hideScore(UUID player) {
            scores.remove(player);
        }

        List<Messages> sentTo(UUID player) {
            return sent.getOrDefault(player, List.of());
        }

        String lastChat(UUID player) {
            List<String> l = chat.getOrDefault(player, List.of());
            return l.isEmpty() ? "" : l.get(l.size() - 1);
        }

        boolean chatContains(UUID player, String part) {
            return chat.getOrDefault(player, List.of()).stream().anyMatch(s -> s.contains(part));
        }

        <T extends Messages> List<T> sentOfType(UUID player, Class<T> type) {
            List<T> result = new ArrayList<>();
            for (Messages m : sentTo(player)) {
                if (type.isInstance(m)) {
                    result.add(type.cast(m));
                }
            }
            return result;
        }
    }

    static Track song(String title, long durationMs) {
        return new Track("https://example.com/" + title + ".mp3", title, durationMs);
    }

    static Track live(String title) {
        return new Track("https://example.com/" + title, title, Track.LIVE);
    }
}
