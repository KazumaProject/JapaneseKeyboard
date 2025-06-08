package com.kazumaproject.symbol_keyboard

import androidx.paging.PagingSource
import androidx.paging.PagingState

class SymbolPagingSource(
    private val symbols: List<String>
) : PagingSource<Int, String>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> {
        return try {
            val position = params.key ?: 0
            val loadSize = params.loadSize
            val endPosition = minOf(position + loadSize, symbols.size)
            val data = symbols.subList(position, endPosition)

            val prevKey = if (position == 0) null else position - loadSize
            val nextKey = if (endPosition == symbols.size) null else endPosition

            LoadResult.Page(
                data = data,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, String>): Int? {
        // Start from the closest page to the anchor position
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(state.config.pageSize)
                ?: anchorPage?.nextKey?.minus(state.config.pageSize)
        }
    }
}
