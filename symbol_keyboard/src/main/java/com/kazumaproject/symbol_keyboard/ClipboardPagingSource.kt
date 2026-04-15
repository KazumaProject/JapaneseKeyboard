package com.kazumaproject.symbol_keyboard

import androidx.paging.PagingSource
import androidx.paging.PagingState

class ClipboardPagingSource(
    private val items: List<ClipboardListItem>
) : PagingSource<Int, ClipboardListItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ClipboardListItem> {
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

    override fun getRefreshKey(state: PagingState<Int, ClipboardListItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(state.config.pageSize)
                ?: anchorPage?.nextKey?.minus(state.config.pageSize)
        }
    }
}
