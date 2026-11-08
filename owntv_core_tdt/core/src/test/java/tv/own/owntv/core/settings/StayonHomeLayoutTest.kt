package tv.own.owntv.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class StayonHomeLayoutTest {
    @Test fun layoutSurvivesBackupRoundTrip() {
        val layout = StayonHomeLayout(
            scale = 115,
            startup = StayonHomeTab.RADIO,
            order = StayonHomeWidget.entries.reversed(),
            hidden = setOf(StayonHomeWidget.SPORTS),
            large = setOf(StayonHomeWidget.GUIDE),
            tabs = listOf(StayonHomeTab.RADIO, StayonHomeTab.TV),
            logoSizes = mapOf(StayonBroadcaster.MEDIASET to 80, StayonBroadcaster.RTVE to 125),
        )
        assertEquals(layout, StayonHomeLayout.decode(layout.encode()))
    }

    @Test fun restoredLayoutClampsScaleAndSelectsAVisibleTab() {
        val layout = StayonHomeLayout.decode("999|APPS|GUIDE,GUIDE|unknown||TV")
        assertEquals(100, layout.logoSize(StayonBroadcaster.MEDIASET))
        assertEquals(130, layout.scale)
        assertEquals(StayonHomeTab.TV, layout.startup)
        assertEquals(StayonHomeWidget.GUIDE, layout.order.first())
        assertEquals(StayonHomeWidget.entries.size, layout.order.size)
    }
}
