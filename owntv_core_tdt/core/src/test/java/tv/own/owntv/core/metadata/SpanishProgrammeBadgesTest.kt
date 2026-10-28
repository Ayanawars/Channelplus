package tv.own.owntv.core.metadata

import org.junit.Assert.*
import org.junit.Test

class SpanishProgrammeBadgesTest {
    @Test fun `Spanish age badges accept explicit classifications but not foreign systems`() {
        assertEquals("16", SpanishProgrammeBadges.age("+16"))
        assertEquals("12", SpanishProgrammeBadges.age("España 12"))
        assertEquals("tp", SpanishProgrammeBadges.age("Todos los públicos"))
        listOf("FSK 16", "TV-14", "PG-13", "18:30", "2022", "Drama", null).forEach {
            assertNull(SpanishProgrammeBadges.age(it))
        }
    }
    @Test fun `content warnings come from explicit descriptors never a genre or synopsis`() {
        assertEquals(listOf("violencia", "lenguaje"), SpanishProgrammeBadges.descriptors(listOf("Violencia; Lenguaje", "Violencia")))
        assertTrue(SpanishProgrammeBadges.descriptors(listOf("Policíaca", "crime", "Tras un tiroteo brutal")).isEmpty())
        assertEquals(listOf("drogas-y-sustancias-toxicas", "discriminacion"), SpanishProgrammeBadges.descriptors(listOf("Drogas y sustancias tóxicas", "Discriminación")))
    }
}
