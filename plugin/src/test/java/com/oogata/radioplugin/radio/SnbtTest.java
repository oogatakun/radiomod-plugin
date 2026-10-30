package com.oogata.radioplugin.radio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnbtTest {

    @Test
    @SuppressWarnings("unchecked")
    void コンパウンドとリストと数を読める() {
        Map<String, Object> m = (Map<String, Object>) Snbt.parse(
                "{id:\"minecraft:block_display\",t:[0.9f,0f,-1f,2,3b,4L,5.5d,1e2],ok:true,Tags:[\"a\",'b']}");
        assertEquals("minecraft:block_display", m.get("id"));
        List<Object> t = (List<Object>) m.get("t");
        assertEquals(0.9f, t.get(0));
        assertEquals(-1f, t.get(2));
        assertEquals(2, t.get(3));
        assertEquals((byte) 3, t.get(4));
        assertEquals(4L, t.get(5));
        assertEquals(5.5, t.get(6));
        assertEquals(100.0, t.get(7));
        assertEquals(Boolean.TRUE, m.get("ok"));
        assertEquals(List.of("a", "b"), m.get("Tags"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 入れ子とエスケープを読める() {
        Map<String, Object> m = (Map<String, Object>) Snbt.parse(
                "{Passengers:[{block_state:{Name:\"minecraft:x\",Properties:{axis:\"y\"}}}],s:\"a\\\"b\"}");
        List<Object> p = (List<Object>) m.get("Passengers");
        Map<String, Object> state = (Map<String, Object>) ((Map<String, Object>) p.get(0)).get("block_state");
        assertEquals("minecraft:x", state.get("Name"));
        assertEquals("a\"b", m.get("s"));
    }

    @Test
    void 壊れた入力は例外になる() {
        assertThrows(IllegalArgumentException.class, () -> Snbt.parse("{a:1"));
        assertThrows(IllegalArgumentException.class, () -> Snbt.parse("{a:1} x"));
    }
}
