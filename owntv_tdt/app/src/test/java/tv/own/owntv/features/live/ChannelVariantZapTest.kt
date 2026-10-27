package tv.own.owntv.features.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.ChannelVariants

class ChannelVariantZapTest {
    @Test fun changingSourceKeepsZapOnTheSameLogicalStation() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        try {
            val first = ChannelEntity(id = 1, sourceId = 1, name = "La 1 FHD", streamUrl = "https://example.test/1")
            val otherVersion = first.copy(id = 2, name = "La 1 HD")
            val next = first.copy(id = 3, name = "La 2 HD")
            val zap = LiveZapList(scope, { otherVersion.id }, { emptyList() }, { emptyList() },
                { emptyList() }, { null }, { ChannelVariants.key(otherVersion) })
            zap.armFromBrowse(listOf(first, otherVersion, next), null, null, null)
            assertEquals(2, zap.channels.value.size)
            assertEquals(next.id, zap.next(1)?.id)
            assertEquals(next.id, zap.next(-1)?.id)
        } finally { scope.cancel() }
    }
}
