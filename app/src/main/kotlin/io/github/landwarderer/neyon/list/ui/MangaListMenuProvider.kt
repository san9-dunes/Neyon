package io.github.landwarderer.neyon.list.ui

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.nav.router
import io.github.landwarderer.neyon.favourites.ui.list.FavouritesListFragment
import io.github.landwarderer.neyon.history.ui.HistoryListFragment
import io.github.landwarderer.neyon.list.ui.config.ListConfigSection
import io.github.landwarderer.neyon.suggestions.ui.SuggestionsFragment
import io.github.landwarderer.neyon.tracker.ui.updates.UpdatesFragment

class MangaListMenuProvider(
	private val fragment: Fragment,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_list, menu)
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
		R.id.action_list_mode -> {
			val section: ListConfigSection = when (fragment) {
				is HistoryListFragment -> ListConfigSection.History
				is SuggestionsFragment -> ListConfigSection.Suggestions
				is FavouritesListFragment -> ListConfigSection.Favorites(fragment.categoryId)
				is UpdatesFragment -> ListConfigSection.Updated
				else -> ListConfigSection.General
			}
			fragment.router.showListConfigSheet(section)
			true
		}

		else -> false
	}
}
