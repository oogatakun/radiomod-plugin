package com.oogata.radioplugin.radio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ModelFileTest {

    private static final List<String> SAMPLE = List.of(
            "# comment",
            "",
            "summon block_display ~-0.5 ~-0.5 ~-0.5 {Tags:[\"m\",\"m_root\"]}",
            "execute as @e[tag=m_root,limit=1] at @s run summon block_display ~ ~ ~ {Passengers:["
                    + "{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:stripped_dark_oak_wood\",Properties:{axis:\"y\"}},"
                    + "transformation:[2f,0f,0f,0.5f,0f,1f,0f,0.25f,0f,0f,1f,0f,0f,0f,0f,1f],Tags:[\"m_0\"]},"
                    + "{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:iron_block\"},"
                    + "transformation:[1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f],Tags:[\"m_1\"]}],Tags:[\"m\",\"m_p0\"]}",
            "ride @e[tag=m_p0,limit=1] mount @e[tag=m_root,limit=1]");

    @Test
    void コマンドは上から順に取り出し_コメントと空行は除く() {
        ModelFile f = ModelFile.parse("m", SAMPLE);
        assertEquals(3, f.commands().size());
        assertTrue(f.commands().get(0).startsWith("summon block_display"));
        assertTrue(f.commands().get(2).startsWith("ride "));
    }

    @Test
    void 目印タグはルートのタグから決まる() {
        assertEquals("m", ModelFile.parse("file", SAMPLE).tag());
        assertEquals("file", ModelFile.parse("file", List.of("say hi")).tag());
    }

    @Test
    void パーツの設計をsummonの中身から取り出せる() {
        List<PartSpec> parts = ModelFile.parse("m", SAMPLE).parts();
        assertEquals(2, parts.size());
        assertEquals("minecraft:stripped_dark_oak_wood[axis=y]", parts.get(0).block());
        assertEquals("minecraft:iron_block", parts.get(1).block());
        assertEquals(List.of("m_0"), parts.get(0).tags());
        // 行優先の並びなので、4列目 (0.5, 0.25, 0) が平行移動、対角が拡大
        Vector3f p = parts.get(0).matrix().transformPosition(new Vector3f(1, 1, 1));
        assertEquals(2.5f, p.x, 1e-6);
        assertEquals(1.25f, p.y, 1e-6);
        assertEquals(1.0f, p.z, 1e-6);
    }

    @Test
    void 同梱のluxuryboomboxを読める() throws IOException {
        Path path = Path.of("models", "luxuryboombox.mcfunction");
        if (!Files.exists(path)) {
            return;
        }
        ModelFile f = ModelFile.load(path);
        assertEquals("luxuryboombox", f.tag());
        // ルートの summon、パーツのまとまり5つの summon、ride 5つ
        assertEquals(11, f.commands().size());
        assertEquals(604, f.parts().size());
        assertTrue(f.parts().stream().allMatch(p -> p.tags().contains("luxuryboombox")
                || p.tags().stream().anyMatch(t -> t.startsWith("luxuryboombox_"))));
    }
}
