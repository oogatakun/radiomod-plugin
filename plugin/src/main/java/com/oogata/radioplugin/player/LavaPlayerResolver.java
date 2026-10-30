package com.oogata.radioplugin.player;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** LavaPlayer で URL の長さ・タイトル・プレイリストの中身を取得する。音声のデコードはしない。 */
public final class LavaPlayerResolver implements TrackResolver {

    private final AudioPlayerManager manager = new DefaultAudioPlayerManager();
    private final ExecutorService worker = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "radioplugin-resolver");
        t.setDaemon(true);
        return t;
    });
    private final Executor mainThread;
    private final Logger logger;
    private final int maxPlaylistSize;

    public LavaPlayerResolver(Executor mainThread, Logger logger, int maxPlaylistSize) {
        this.mainThread = mainThread;
        this.logger = logger;
        this.maxPlaylistSize = Math.max(1, maxPlaylistSize);
        // YouTube は youtube-source を使い、LavaPlayer 標準の古い YouTube 対応は外す。
        // ローカルファイルの source は登録しない（サーバーのファイルを読ませないため）。
        manager.registerSourceManager(new YoutubeAudioSourceManager());
        AudioSourceManagers.registerRemoteSources(manager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class);
    }

    @Override
    public void resolve(String url, Consumer<Result> callback) {
        Consumer<Result> onMain = result -> mainThread.execute(() -> callback.accept(result));
        // DNS の引き当てがブロックするので、検査も別スレッド側で行う。
        worker.execute(() -> {
            UrlPolicy.Verdict verdict = UrlPolicy.checkResolved(url);
            if (verdict != UrlPolicy.Verdict.OK) {
                onMain.accept(Result.failure(verdict == UrlPolicy.Verdict.INTERNAL
                        ? Result.Failure.FORBIDDEN : Result.Failure.UNSUPPORTED, ""));
                return;
            }
            manager.loadItem(url, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    onMain.accept(Result.single(toTrack(track.getInfo(), url)));
                }

                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    List<AudioTrack> source = playlist.getTracks();
                    if (source.isEmpty()) {
                        onMain.accept(Result.failure(Result.Failure.FAILED, "プレイリストが空です"));
                        return;
                    }
                    boolean truncated = source.size() > maxPlaylistSize;
                    List<Track> tracks = new ArrayList<>();
                    for (AudioTrack t : source.subList(0, Math.min(source.size(), maxPlaylistSize))) {
                        tracks.add(toTrack(t.getInfo(), url));
                    }
                    AudioTrack selected = playlist.getSelectedTrack();
                    if (selected != null && !playlist.isSearchResult()) {
                        // 特定の曲を指したURLは、その曲を先頭にする
                        int at = source.indexOf(selected);
                        if (at > 0 && at < tracks.size()) {
                            tracks.add(0, tracks.remove(at));
                        }
                    }
                    onMain.accept(Result.playlist(tracks, truncated));
                }

                @Override
                public void noMatches() {
                    onMain.accept(Result.failure(Result.Failure.UNSUPPORTED, ""));
                }

                @Override
                public void loadFailed(FriendlyException e) {
                    logger.log(Level.FINE, "URL の取得に失敗: " + url, e);
                    // 音声として読めないページなどは、取得の失敗ではなく非対応として扱う
                    boolean unsupported = e.getMessage() != null && e.getMessage().startsWith("Unknown file format");
                    onMain.accept(unsupported ? Result.failure(Result.Failure.UNSUPPORTED, "")
                            : Result.failure(Result.Failure.FAILED, e.getMessage()));
                }
            });
        });
    }

    @Override
    public void close() {
        worker.shutdownNow();
        manager.shutdown();
    }

    private static Track toTrack(AudioTrackInfo info, String fallbackUrl) {
        long duration = info.isStream || info.length <= 0 || info.length == Long.MAX_VALUE
                ? Track.LIVE : info.length;
        String url = info.uri != null && !info.uri.isEmpty() ? info.uri : fallbackUrl;
        String title = info.title == null || info.title.isBlank() || info.title.equals("Unknown title")
                ? url : info.title;
        return new Track(url, title, duration);
    }
}
