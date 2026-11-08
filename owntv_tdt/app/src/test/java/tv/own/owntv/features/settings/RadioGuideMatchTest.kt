package tv.own.owntv.features.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioGuideMatchTest {
    private val station = StayonRadioStation("1", "Rádio Nacional", "https://example.invalid/radio", null, guideId = "rne")
    private val programme = RadioGuideProgramme(10, 20, "News")

    @Test fun matchesIdFirstAndFillsScheduleFromNextFeed() {
        val first = RadioGuideFeed("a", "First", "", 0,
            listOf(RadioGuideChannel("RNE", "Other name", "https://example.invalid/logo", emptyList())))
        val second = RadioGuideFeed("b", "Second", "", 0,
            listOf(RadioGuideChannel("other", "Radio-Nacional", null, listOf(programme))))
        assertEquals(programme to "https://example.invalid/logo", radioGuideMatch(listOf(first, second), station, 15))
        assertNull(radioGuideMatch(listOf(first, second), station, 20).first)
    }

    @Test fun ambiguousNameDoesNotAssignAProgramme() {
        val channels = listOf("a", "b").map { RadioGuideChannel(it, "Radio Nacional", null, listOf(programme)) }
        val feed = RadioGuideFeed("a", "First", "", 0, channels)
        assertNull(radioGuideMatch(listOf(feed), station, 15).first)
    }
}
