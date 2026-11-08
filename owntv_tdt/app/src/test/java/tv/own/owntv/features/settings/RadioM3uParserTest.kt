package tv.own.owntv.features.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Station metadata is imported only from explicitly added radio M3U sources. */
class RadioM3uParserTest {
    @Test fun `parses radio EXTINF names and optional artwork`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="https://radio.example/logo.png" group-title="Radio",Radio Uno
            https://radio.example/uno.mp3
            #EXTINF:-1,Radio Dos
            https://radio.example/dos.aac
        """.trimIndent()
        val result = StayonRadioLibrary.parseM3u(m3u)
        assertEquals(2, result.size)
        assertEquals("Radio Uno", result[0].name)
        assertEquals("https://radio.example/uno.mp3", result[0].url)
        assertEquals("https://radio.example/logo.png", result[0].logo)
        assertEquals("Radio Dos", result[1].name)
        assertNull(result[1].logo)
    }

    @Test fun `ignores directives invalid URLs and duplicates`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Sample
            file:///storage/audio.mp3
            #EXTINF:-1,Station
            https://radio.example/live
            #EXTINF:-1,Duplicate
            https://radio.example/live
        """.trimIndent()
        val result = StayonRadioLibrary.parseM3u(m3u)
        assertEquals(1, result.size)
        assertEquals("Station", result[0].name)
    }
}
