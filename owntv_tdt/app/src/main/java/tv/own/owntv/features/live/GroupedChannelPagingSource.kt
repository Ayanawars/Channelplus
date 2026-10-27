package tv.own.owntv.features.live

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.ChannelVariants

/** Group before slicing pages: equal stations can be hundreds of provider rows apart.
 * The original Room source owns invalidation. No rows or user memberships are rewritten. */
internal class GroupedChannelPagingSource(
    private val original: PagingSource<Int, ChannelEntity>,
    private val visible: (ChannelEntity) -> Boolean,
    private val rename: (ChannelEntity) -> ChannelEntity,
) : PagingSource<Int, ChannelEntity>() {
    private val mutex = Mutex()
    private var snapshot: List<ChannelEntity>? = null
    private val onOriginalInvalidated: () -> Unit = { invalidate() }
    init {
        original.registerInvalidatedCallback(onOriginalInvalidated)
        registerInvalidatedCallback { original.unregisterInvalidatedCallback(onOriginalInvalidated) }
    }
    override fun getRefreshKey(state: PagingState<Int, ChannelEntity>): Int? =
        state.anchorPosition?.let { position -> state.closestPageToPosition(position)?.prevKey ?: 0 }
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ChannelEntity> = try {
        val rows = mutex.withLock {
            snapshot ?: run {
                val all = ArrayList<ChannelEntity>()
                var request: LoadParams<Int> = LoadParams.Refresh(null, 512, false)
                val visited = HashSet<Int>()
                while (true) {
                    when (val page = original.load(request)) {
                        is LoadResult.Page -> {
                            all.addAll(page.data.filter(visible))
                            val next = page.nextKey ?: break
                            check(visited.add(next))
                            request = LoadParams.Append(next, 512, false)
                        }
                        is LoadResult.Error -> throw page.throwable
                        is LoadResult.Invalid -> return LoadResult.Invalid()
                    }
                }
                // M3U customization keys use the provider name. Resolve those keys before the
                // grouped display title strips quality suffixes, while grouping by original identity.
                val byId = all.associateBy { it.id }
                ChannelVariants.collapse(all).map { grouped ->
                    val originalRow = byId.getValue(grouped.id)
                    val customized = rename(originalRow)
                    if (customized.name != originalRow.name) grouped.copy(name = customized.name) else grouped
                }.also { snapshot = it }
            }
        }
        if (invalid) LoadResult.Invalid() else {
            val requested = (params.key ?: 0).coerceIn(0, rows.size)
            val start = if (params is LoadParams.Prepend) (requested - params.loadSize).coerceAtLeast(0) else requested
            val end = if (params is LoadParams.Prepend) requested else (start + params.loadSize).coerceAtMost(rows.size)
            LoadResult.Page(rows.subList(start, end), if (start > 0) start else null,
                if (end < rows.size) end else null, itemsBefore = start, itemsAfter = rows.size - end)
        }
    } catch (e: CancellationException) { throw e }
      catch (e: Exception) { LoadResult.Error(e) }
}
