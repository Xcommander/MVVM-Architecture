package com.qingmei2.sample.ui.main.repos

import androidx.annotation.WorkerThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.paging.PagedList
import com.qingmei2.architecture.core.base.viewmodel.BaseViewModel
import com.qingmei2.architecture.core.ext.paging.PagingRequestHelper
import com.qingmei2.architecture.core.ext.paging.toLiveDataPagedList
import com.qingmei2.architecture.core.ext.postNext
import com.qingmei2.sample.base.Results
import com.qingmei2.sample.entity.Repo
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@SuppressWarnings("checkResult")
class ReposViewModel(
        private val repository: ReposRepository
) : BaseViewModel() {

    private val _viewStateLiveData: MutableLiveData<ReposViewState> = MutableLiveData(ReposViewState.initial())
    val viewStateLiveData: LiveData<ReposViewState> = _viewStateLiveData

    private val mBoundaryCallback = RepoBoundaryCallback { result, pageIndex ->
        // Paging 预加载分页的结果，不需要对Error或者Refresh进行展示
        // 这会给用户一种无限加载列表的效果
        viewModelScope.launch {
            if (result is Results.Success) {
                when (pageIndex == 1) {
                    true -> repository.clearAndInsertNewData(result.data)
                    false -> repository.insertNewPageData(result.data)
                }
            }
        }
    }

    init {
        initPagedList()
    }

    private fun initPagedList() {
        // TODO leak memory.
        viewModelScope.launch {
            repository
                    .fetchRepoDataSourceFactory()
                    .toLiveDataPagedList(boundaryCallback = mBoundaryCallback)
                    .observeForever { pagedList ->
                        _viewStateLiveData.postNext { state ->
                            state.copy(isLoading = false, throwable = null, pagedList = pagedList)
                        }
                    }
        }
    }

    fun onSortChanged(sort: String) {
        if (sort != fetchCurrentSort())
            _viewStateLiveData.postNext { last ->
                // 'isLoading = true' will trigger refresh action.
                last.copy(isLoading = true, throwable = null, pagedList = null, nextPageData = null, sort = sort)
            }
    }

    private fun fetchCurrentSort(): String {
        // sort is always exist by BehaviorSubject.createDefault(initValue).
        return _viewStateLiveData.value?.sort ?: sortByCreated
    }

    fun refreshDataSource() {
        viewModelScope.launch {
            when (val result = repository.fetchRepoByPage(fetchCurrentSort(), 1)) {
                is Results.Success -> {
                    repository.clearAndInsertNewData(result.data)
                    _viewStateLiveData.postNext { last ->
                        last.copy(isLoading = false, throwable = null, pagedList = null, nextPageData = result.data)
                    }
                }
                is Results.Failure -> {
                    _viewStateLiveData.postNext { last ->
                        last.copy(isLoading = false, throwable = result.error, pagedList = null, nextPageData = null)
                    }
                }
            }
        }
    }

    companion object {

        const val sortByCreated: String = "created"

        const val sortByUpdate: String = "updated"

        const val sortByLetter: String = "full_name"

        fun instance(fragment: Fragment, repo: ReposRepository): ReposViewModel =
                ViewModelProvider(fragment, ReposViewModelFactory(repo)).get(ReposViewModel::class.java)
    }

    inner class RepoBoundaryCallback(
            @WorkerThread private val handleResponse: (Results<List<Repo>>, Int) -> Unit
    ) : PagedList.BoundaryCallback<Repo>() {

        private val mExecutor = Executors.newSingleThreadExecutor()
        private val mHelper = PagingRequestHelper(mExecutor)

        override fun onZeroItemsLoaded() {
            mHelper.runIfNotRunning(PagingRequestHelper.RequestType.INITIAL) { callback ->
                viewModelScope.launch {
                    val result = repository.fetchRepoByPage(fetchCurrentSort(), 1)
                    handleResponse(result, 1)
                    callback.recordSuccess()
                }
            }
        }

        override fun onItemAtEndLoaded(itemAtEnd: Repo) {
            mHelper.runIfNotRunning(PagingRequestHelper.RequestType.AFTER) { callback ->
                val currentPageIndex = (itemAtEnd.indexInSortResponse / 30) + 1
                val nextPageIndex = currentPageIndex + 1
                viewModelScope.launch {
                    val result = repository.fetchRepoByPage(fetchCurrentSort(), nextPageIndex)
                    handleResponse(result, nextPageIndex)
                    callback.recordSuccess()
                }
            }
        }
    }
}

class ReposViewModelFactory(
        private val repo: ReposRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ReposViewModel(repo) as T
    }
}