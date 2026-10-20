package com.example.api

import org.junit.Assert.*
import org.junit.Test

class NetEaseSongReferenceTest {
    @Test fun `recognizes official desktop mobile and fragment song links`() {
        listOf("12345", " https://music.163.com/song?id=12345 ",
            "https://music.163.com/#/song?id=12345", "https://y.music.163.com/m/song?id=12345&userid=678")
            .forEach { assertEquals(it, 12345L, NetEaseSongReference.parse(it)) }
    }

    @Test fun `rejects arbitrary hosts playlists malformed ids and search text`() {
        listOf("hello world", "0", "-1", "99999999999999999999999999999",
            "https://music.163.com.evil.test/song?id=12345", "https://example.com/song?id=12345",
            "https://music.163.com/playlist?id=12345", "https://music.163.com/song?id=12wrong")
            .forEach { assertNull(it, NetEaseSongReference.parse(it)) }
    }
}
