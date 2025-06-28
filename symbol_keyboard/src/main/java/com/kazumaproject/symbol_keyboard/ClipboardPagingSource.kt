package com.kazumaproject.symbol_keyboard

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.kazumaproject.core.data.clipboard.ClipboardItem

class ClipboardPagingSource(
    private val items: List<ClipboardItem>
) : PagingSource<Int, ClipboardItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ClipboardItem> {
        return try {
            val position = params.key ?: 0
            val loadSize = params.loadSize
            val endPosition = minOf(position + loadSize, items.size)
            val data = items.subList(position, endPosition)

            val prevKey = if (position == 0) null else position - loadSize
            val nextKey = if (endPosition == items.size) null else endPosition

            LoadResult.Page(
                data = data,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ClipboardItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(state.config.pageSize)
                ?: anchorPage?.nextKey?.minus(state.config.pageSize)
        }
    }
}
