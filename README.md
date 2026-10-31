# radiomod / radioplugin

Minecraft 1.20.1 で、URL の音楽をサーバー全体、または設置したラジオごとに、時刻をそろえて再生する。
Forge の mod(クライアント)が音を鳴らし、Spigot の plugin(サーバー)が再生の状態とコマンドを担当する。

- 仕様: [docs/spec.md](docs/spec.md)
- 通信: [docs/protocol.md](docs/protocol.md)

mod を入れていないプレイヤーも、音が聞こえないだけで、サーバーには普通に参加できる。

## 必要なもの

| | 内容 |
| --- | --- |
| サーバー | Spigot(または Paper)1.20.1 / Java 17 |
| クライアント | Minecraft 1.20.1 / Forge 47.x / Java 17 |

## 導入

1. `plugin/target/radioplugin-0.1.0.jar` をサーバーの `plugins/` に入れる。
2. `mod/build/libs/radiomod-0.1.0.jar` を、音を聞く各プレイヤーの `mods/` に入れる(サーバーには入れない)。
3. サーバーを起動する。`plugins/RadioPlugin/config.yml` ができる。
4. ラジオの見た目に BDEngine のモデルを使うときは、`create.mcfunction` を `plugins/RadioPlugin/models/` に置く(例: `models/luxuryboombox.mcfunction`)。

## ビルド

Windows では、リポジトリ直下の `build.bat` を使う(Java 17 が必要)。

```
.\build.bat                  plugin と mod の両方をビルド(テストも実行)
.\build.bat plugin           plugin だけ
.\build.bat mod              mod だけ
.\build.bat deploy           ビルドして、build.bat の先頭に書いた配置先へ jar をコピー
.\build.bat skiptests        テストを飛ばす
```

配置先と JAVA_HOME は `build.bat` の先頭で変える。手でビルドするなら、plugin は `plugin/` で `mvn clean package`、mod は `mod/` で `gradlew build`。

## コマンド

| コマンド | 内容 |
| --- | --- |
| `/ps play <URL> [loop]` | 自分だけに再生する |
| `/ps playlist <URL> [loop]` | プレイリストを再生する |
| `/ps add <URL>` | キューに足す |
| `/ps pause` `stop` `skip` `previous` | 一時停止/再開・停止・次・前 |
| `/ps list [score]` | キューを表示する(score はスコアボード) |
| `/ps all [サブコマンド]` | 再生を共有する / 共有を操作する |
| `/ps listen` `/ps leave` | 共有を聞く / やめる |
| `/rd wand` `create` `delete` `move` `tp` `radiolist` | ラジオの設置と管理 |
| `/rp <ラジオ名> <play|playlist|add|pause|stop|list|skip|previous|keep>` | ラジオごとの再生。`keep` で再起動後も残す |

対応する URL: mp3・aac・ogg・wav などの直リンク、インターネットラジオ(Icecast・Shoutcast、ライブ扱い)、YouTube、SoundCloud、Bandcamp。

## 権限

| ノード | 対象 | 既定 |
| --- | --- | --- |
| `radioplugin.ps` | `/ps`(all・listen・leave 以外) | 全員 |
| `radioplugin.ps.all` | `/ps all` と `/ps all <サブコマンド>` | 全員 |
| `radioplugin.ps.listen` | `/ps listen` `/ps leave` | 全員 |
| `radioplugin.rd.manage` | `/rd` の wand・create・delete・move | op |
| `radioplugin.rd.tp` | `/rd tp` | op |
| `radioplugin.rd.list` | `/rd radiolist` | 全員 |
| `radioplugin.rp` | `/rp` の全サブコマンド(keep を含む) | op |

## 設定

plugin の `config.yml`:

| キー | 意味 | 初期値 |
| --- | --- | --- |
| `radio-range` | ラジオの音が聞こえる範囲(ブロック) | 24 |
| `sync-interval-seconds` | 基準時刻を再通知する間隔(秒) | 30 |
| `start-lead-ms` | 再生開始を先にずらす時間(ms) | 500 |
| `announce` | 曲の通知先(`chat` / `actionbar` / `none`) | `actionbar` |
| `max-playlist-size` | プレイリスト展開の上限曲数 | 100 |
| `max-consecutive-errors` | 連続で失敗したら停止する回数 | 3 |

古い `config.yml` が残っている場合は、キーが足りなくても初期値で動く。新しい項目を見たいときは、`config.yml` を消して再起動する。

mod の設定(`config/radiomod-client.toml`、または Mod の設定画面):音楽とラジオの音量、ラジオ音のオン・オフ、同期の幅(補正を始める 40ms / やめる 10ms / 速度の上限 ±2%)、出力機器の遅れの手動補正(ms)。音量は「音とサウンド設定」のスライダー(音楽・ラジオ)でも変えられる。

## 注意

- 音声を取りに行くのは各プレイヤーの mod なので、再生する URL の配信元には、プレイヤーの IP アドレスが見える。
- YouTube は規約に触れる可能性があり、YouTube 側の変更で突然動かなくなることがある。主な音源は直リンクとインターネットラジオと考える。
- 配信や録画に音が入る場合は、権利を確認できる音源を使う。
- Bluetooth ヘッドホンなど、出力の遅れが大きい機器は、手動の補正値で合わせる。ライブ配信は位置の同期ができない。
- plugin が取得する URL は `http` / `https` のみ。`localhost` や内部ネットワークのアドレスは拒否する。
