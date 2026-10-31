package com.oogata.radiomod.audio;

import com.oogata.radiomod.RadioMod;
import com.sedmelluq.lava.common.natives.SystemNativeLibraryProperties;
import com.sedmelluq.lava.common.natives.architecture.SystemType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import net.minecraftforge.fml.ModList;

/**
 * LavaPlayer のネイティブライブラリ（mp3 のデコードなど）を、mod の jar の中から取り出す。
 *
 * <p>jar-in-jar だと、LavaPlayer 自身は natives の jar（別モジュール）のファイルをクラスローダーから探せず、
 * 「Required library was not found」になる。そこで mod の jar の META-INF/jarjar にある
 * lavaplayer-natives-*.jar を直接開き、自分の OS 用のファイルだけを一時フォルダへ展開して、
 * システムプロパティ {@code lava.native.dir} で LavaPlayer に場所を教える。
 * 開発環境（jar-in-jar なし）では何もせず、LavaPlayer の既定の読み込みに任せる。
 */
final class NativeExtractor {

    private static final String PREFIX = "lava.native.";

    private NativeExtractor() {
    }

    static void prepare() {
        if (System.getProperty(PREFIX + "dir") != null || System.getProperty(PREFIX + "path") != null) {
            return; // 利用者が明示している
        }
        try {
            Path modJar = ModList.get().getModFileById(RadioMod.MOD_ID).getFile().getFilePath();
            if (!Files.isRegularFile(modJar)) {
                return;
            }
            SystemType type = SystemType.detect(new SystemNativeLibraryProperties("connector", PREFIX));
            String systemName = type.formatSystemName();
            try (ZipFile outer = new ZipFile(modJar.toFile())) {
                ZipEntry nested = findNatives(outer);
                if (nested == null) {
                    return;
                }
                Path dir = Path.of(System.getProperty("java.io.tmpdir"), "radiomod-natives",
                        nested.getName().substring(nested.getName().lastIndexOf('/') + 1).replace(".jar", ""), systemName);
                Files.createDirectories(dir);
                int count = 0;
                try (InputStream in = outer.getInputStream(nested); ZipInputStream zip = new ZipInputStream(in)) {
                    String wanted = "natives/" + systemName + "/";
                    for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                        String name = e.getName();
                        if (e.isDirectory() || !name.startsWith(wanted)) {
                            continue;
                        }
                        String file = name.substring(wanted.length());
                        if (file.isEmpty() || file.contains("/") || file.contains("\\") || file.contains("..")) {
                            continue;
                        }
                        extract(zip, dir.resolve(file));
                        count++;
                    }
                }
                if (count == 0) {
                    RadioMod.LOGGER.warn("natives の jar に {} 用のファイルがありません", systemName);
                    return;
                }
                System.setProperty(PREFIX + "dir", dir.toAbsolutePath().toString());
                RadioMod.LOGGER.info("LavaPlayer のネイティブライブラリを展開しました: {} ({} 個)", dir, count);
            }
        } catch (Throwable t) {
            RadioMod.LOGGER.warn("LavaPlayer のネイティブライブラリを展開できませんでした: {}", t.toString());
        }
    }

    private static ZipEntry findNatives(ZipFile outer) {
        Enumeration<? extends ZipEntry> entries = outer.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            String n = e.getName();
            if (n.startsWith("META-INF/jarjar/lavaplayer-natives-") && n.endsWith(".jar")) {
                return e;
            }
        }
        return null;
    }

    private static void extract(InputStream in, Path target) throws IOException {
        Path tmp = Files.createTempFile(target.getParent(), "nat", ".tmp");
        try {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException locked) {
                // 別のゲームが同じ DLL を読み込み中など。展開済みのファイルが同じ大きさならそれを使う。
                if (!Files.exists(target) || Files.size(target) != Files.size(tmp)) {
                    throw locked;
                }
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
