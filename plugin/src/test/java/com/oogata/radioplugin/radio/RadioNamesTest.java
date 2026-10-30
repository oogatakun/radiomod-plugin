package com.oogata.radioplugin.radio;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RadioNamesTest {

    @Test
    void 英数字と日本語と記号は使える() {
        for (String ok : new String[] {"radio1", "Radio_A-2", "ラジオ", "らじお", "音楽室", "ハウス々", "ロビー"}) {
            assertNull(RadioNames.validate(ok), ok);
        }
    }

    @Test
    void 空と空白は使えない() {
        assertNotNull(RadioNames.validate(null));
        assertNotNull(RadioNames.validate(""));
        assertNotNull(RadioNames.validate("a b"));
        assertNotNull(RadioNames.validate("a　b"));
        assertNotNull(RadioNames.validate(" "));
    }

    @Test
    void 許可していない記号は使えない() {
        for (String ng : new String[] {"a.b", "a/b", "a:b", "§a", "a!", "😀"}) {
            assertNotNull(RadioNames.validate(ng), ng);
        }
    }
}
