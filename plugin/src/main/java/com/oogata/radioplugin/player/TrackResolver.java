package com.oogata.radioplugin.player;

import java.util.List;
import java.util.function.Consumer;

/**
 * URL から曲の情報（長さ・タイトル・プレイリストの中身）を取得する。
 * 実装は LavaPlayer、テストでは偽物に差し替える。結果は必ずメインスレッドで callback に渡す。
 */
public interface TrackResolver {

    void resolve(String url, Consumer<Result> callback);

    default void close() {
    }

    final class Result {

        public enum Failure {
            /** 取得できた（失敗なし） */
            NONE,
            /** 内部アドレスなど、取得してはいけない URL */
            FORBIDDEN,
            /** 対応していない URL */
            UNSUPPORTED,
            /** 取得に失敗した（接続できない、読めないなど） */
            FAILED
        }

        private final List<Track> tracks;
        private final boolean playlist;
        private final boolean truncated;
        private final Failure failure;
        private final String detail;

        private Result(List<Track> tracks, boolean playlist, boolean truncated, Failure failure, String detail) {
            this.tracks = tracks;
            this.playlist = playlist;
            this.truncated = truncated;
            this.failure = failure;
            this.detail = detail;
        }

        public static Result single(Track track) {
            return new Result(List.of(track), false, false, Failure.NONE, "");
        }

        public static Result playlist(List<Track> tracks, boolean truncated) {
            return new Result(List.copyOf(tracks), true, truncated, Failure.NONE, "");
        }

        public static Result failure(Failure failure, String detail) {
            return new Result(List.of(), false, false, failure, detail == null ? "" : detail);
        }

        public boolean ok() {
            return failure == Failure.NONE;
        }

        public List<Track> tracks() {
            return tracks;
        }

        /** URL がプレイリストだった（中身が1曲でも true）。 */
        public boolean isPlaylist() {
            return playlist;
        }

        /** プレイリストが上限曲数で切られた。 */
        public boolean truncated() {
            return truncated;
        }

        public Failure failure() {
            return failure;
        }

        public String detail() {
            return detail;
        }
    }
}
