package tv.own.owntv.features.live

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

class GroupedChannelPagingSourceTest {
    private fun ch(id: Long, name: String) = ChannelEntity(id = id, sourceId = 1, name = name, streamUrl = "https://example.test/$id")
    private class Provider(private val rows: List<ChannelEntity>) : PagingSource<Int, ChannelEntity>() {
        override fun getRefreshKey(state: PagingState<Int, ChannelEntity>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ChannelEntity> {
            val start = params.key ?: 0
            // Tiny pages deliberately separate duplicate stations.
            val end = (start + 2).coerceAtMost(rows.size)
            return LoadResult.Page(rows.subList(start, end), null, end.takeIf { it < rows.size })
        }
    }
    @Test fun duplicatesAcrossProviderPagesCollapseBeforeUiPaging() = runBlocking {
        val source = GroupedChannelPagingSource(Provider(listOf(ch(1, "La 1 SD"), ch(2, "La 2 HD"),
            ch(3, "La1 FHD"), ch(4, "La 2 FHD"))), { true }, { it })
        val first = source.load(PagingSource.LoadParams.Refresh(null, 1, false)) as PagingSource.LoadResult.Page<Int, ChannelEntity>
        assertEquals(listOf(3L), first.data.map { it.id })
        val next = source.load(PagingSource.LoadParams.Append(first.nextKey!!, 1, false)) as PagingSource.LoadResult.Page<Int, ChannelEntity>
        assertEquals(listOf(4L), next.data.map { it.id })
        assertNull(next.nextKey)
        val back = source.load(PagingSource.LoadParams.Prepend(next.prevKey!!, 1, false)) as PagingSource.LoadResult.Page<Int, ChannelEntity>
        assertEquals(first.data, back.data)
    }
    @Test fun hiddenBestQualityDoesNotRemoveVisibleAlternativeAndRoomInvalidatesWrapper() = runBlocking {
        val original = Provider(listOf(ch(1, "La 1 SD"), ch(2, "La 1 FHD")))
        val source = GroupedChannelPagingSource(original, { it.id != 2L }, { it })
        val page = source.load(PagingSource.LoadParams.Refresh(null, 80, false)) as PagingSource.LoadResult.Page<Int, ChannelEntity>
        assertEquals(listOf(1L), page.data.map { it.id })
        original.invalidate()
        assertTrue(source.invalid)
    }
    @Test fun m3uRenameUsesProviderKeyWithoutChangingStationGrouping() = runBlocking {
        val names = mapOf("1:La 1 FHD" to "Mi canal")
        val source = GroupedChannelPagingSource(Provider(listOf(ch(1, "La 1 SD"), ch(2, "La 1 FHD"))),
            { true }, { channel ->
                names[tv.own.owntv.core.customize.CustomizeKeys.channel(channel)]
                    ?.let { channel.copy(name = it) } ?: channel
            })
        val page = source.load(PagingSource.LoadParams.Refresh(null, 80, false)) as PagingSource.LoadResult.Page<Int, ChannelEntity>
        assertEquals(1, page.data.size)
        assertEquals("Mi canal", page.data.single().name)
    }
}
