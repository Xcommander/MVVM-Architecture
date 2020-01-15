package com.qingmei2.sample.ui.main.repos

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import com.jakewharton.rxbinding3.recyclerview.scrollStateChanges
import com.qingmei2.architecture.core.base.view.fragment.BaseFragment
import com.qingmei2.architecture.core.ext.jumpBrowser
import com.qingmei2.architecture.core.ext.observe
import com.qingmei2.sample.R
import com.qingmei2.sample.base.BaseApplication
import com.qingmei2.sample.common.listScrollChangeStateProcessor
import com.qingmei2.sample.utils.toast
import com.uber.autodispose.autoDisposable
import kotlinx.android.synthetic.main.fragment_repos.*
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import java.util.concurrent.TimeUnit

class ReposFragment : BaseFragment() {

    override val kodein: Kodein = Kodein.lazy {
        extend(parentKodein)
        import(reposKodeinModule)
    }

    private val mViewModel: ReposViewModel by instance()

    override val layoutId: Int = R.layout.fragment_repos

    private val mAdapter = ReposPagedAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.inflateMenu(R.menu.menu_repos_filter_type)

        mRecyclerView.adapter = mAdapter

        binds()
    }

    private fun binds() {
        // when list scrolling start or stop, then switch bottom button visible state.
        mRecyclerView.scrollStateChanges()
                .debounce(500, TimeUnit.MILLISECONDS)
                .compose(listScrollChangeStateProcessor)
                .autoDisposable(scopeProvider)
                .subscribe(::switchFabState)

        // swipe refresh event.
        mSwipeRefreshLayout.setOnRefreshListener {
            mViewModel.refreshDataSource()
        }

        // when button was clicked, scrolling list to top.
        fabTop.setOnClickListener {
            mRecyclerView.scrollToPosition(0)
        }

        // menu item clicked event.
        toolbar.setOnMenuItemClickListener {
            onMenuSelected(it)
            true
        }

        // list item clicked event.
        observe(mAdapter.getItemClickEvent(), BaseApplication.INSTANCE::jumpBrowser)

        observe(mViewModel.viewStateLiveData, this::onNewState)
    }

    private fun onNewState(state: ReposViewState) {
        if (state.throwable != null) {
            // handle throwable
            toast { "network failure." }
        }

        if (state.isLoading != mSwipeRefreshLayout.isRefreshing) {
            mSwipeRefreshLayout.isRefreshing = state.isLoading
        }

        if (state.pagedList != null) {
            mAdapter.submitList(state.pagedList)
        }
    }

    private fun onMenuSelected(menuItem: MenuItem) {
        mViewModel.onSortChanged(
                when (menuItem.itemId) {
                    R.id.menu_repos_letter -> ReposViewModel.sortByLetter
                    R.id.menu_repos_update -> ReposViewModel.sortByUpdate
                    R.id.menu_repos_created -> ReposViewModel.sortByCreated
                    else -> throw IllegalArgumentException("failure menuItem id.")
                }
        )
    }

    private fun switchFabState(show: Boolean) {
        when (show) {
            false -> ObjectAnimator.ofFloat(fabTop, "translationX", 250f, 0f)
            true -> ObjectAnimator.ofFloat(fabTop, "translationX", 0f, 250f)
        }.apply {
            duration = 300
            start()
        }
    }
}