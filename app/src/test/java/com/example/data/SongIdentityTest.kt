package com.example.data

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SongIdentityTest {
    @Test fun `preserves Japanese Chinese punctuation and field boundaries`() {
        assertNotEquals(SongIdentity.id("夜に駆ける", "歌手"), SongIdentity.id("群青", "歌手"))
        assertNotEquals(SongIdentity.id("晴天", "周杰倫"), SongIdentity.id("稻香", "周杰倫"))
        assertNotEquals(SongIdentity.id("a-b", "c"), SongIdentity.id("ab", "c"))
        assertNotEquals(SongIdentity.id("a_b", "c"), SongIdentity.id("a", "b_c"))
    }

    @Test fun `normalizes canonical unicode whitespace and case independently of locale`() {
        val previous = Locale.getDefault()
        try {
            val id = SongIdentity.id("  I CAFÉ ", "가수")
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(id, SongIdentity.id("i cafe\u0301", "가수"))
        } finally { Locale.setDefault(previous) }
    }
}
