package io.github.landwarderer.neyon.settings.sources.extension

import android.app.Activity
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.main.ui.owners.AppBarOwner

class ExtensionDownloaderMenuProvider(
	private val activity: Activity,
	private val viewModel: ExtensionDownloaderViewModel,
	private val onAddRepoClick: () -> Unit,
	private val onManageReposClick: () -> Unit,
) : MenuProvider,
	MenuItem.OnActionExpandListener,
	SearchView.OnQueryTextListener {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_extensions, menu)
		val searchMenuItem = menu.findItem(R.id.action_search)
		searchMenuItem.setOnActionExpandListener(this)
		val searchView = searchMenuItem.actionView as SearchView
		searchView.setOnQueryTextListener(this)
		searchView.setIconifiedByDefault(false)
		searchView.queryHint = searchMenuItem.title
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
		R.id.action_add_repo -> {
			onAddRepoClick()
			true
		}

		R.id.action_manage_repos -> {
			onManageReposClick()
			true
		}

		R.id.action_refresh -> {
			viewModel.refresh()
			true
		}

		else -> false
	}

	override fun onMenuItemActionExpand(item: MenuItem): Boolean {
		(activity as? AppBarOwner)?.appBar?.setExpanded(true, true)
		viewModel.performSearch((item.actionView as SearchView).query?.toString())
		return true
	}

	override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
		(item.actionView as SearchView).setQuery("", false)
		viewModel.performSearch(null)
		return true
	}

	override fun onQueryTextSubmit(query: String?): Boolean = false

	override fun onQueryTextChange(newText: String?): Boolean {
		viewModel.performSearch(newText)
		return true
	}
}
