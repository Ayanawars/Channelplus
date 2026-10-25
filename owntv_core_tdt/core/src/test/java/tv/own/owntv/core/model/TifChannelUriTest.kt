package tv.own.owntv.core.model

import org.junit.Assert.*
import org.junit.Test

class TifChannelUriTest {
    @Test fun `TCL hardware channel survives URI encoding`() {
        val channel = TifChannelUri("com.tcl.tvinput/.TDTVInputService/HW1413742848", 42)
        assertEquals(channel, TifChannelUri.parse(channel.encode()))
    }

    @Test fun `reserved characters cannot become extra query fields`() {
        val channel = TifChannelUri("test/.Input/HW1 &id=999+ España", 7)
        assertEquals(channel, TifChannelUri.parse(channel.encode()))
    }

    @Test fun `malformed and network addresses are rejected`() {
        listOf(
            "https://example.org/channel?input=a%2Fb&id=1",
            "owntv-tif://channel?input=a%2Fb&id=-1",
            "owntv-tif://channel?input=a%2Fb&id=9223372036854775808",
            "owntv-tif://channel?input=a%2Fb&id=1&id=2",
            "owntv-tif://channel?input=a%0Ab%2Fc&id=1",
            "owntv-tif://channel?input=invalid&id=1",
            "owntv-tif://channel?input=a%2Fb&id=1#fragment",
            "owntv-tif://channel?input=%ZZ&id=1",
        ).forEach { assertNull(it, TifChannelUri.parse(it)) }
    }
}
