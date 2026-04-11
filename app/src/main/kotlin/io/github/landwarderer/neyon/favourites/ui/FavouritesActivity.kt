package io.github.landwarderer.neyon.favourites.ui

import android.os.Bundle
import io.github.landwarderer.neyon.core.nav.AppRouter
import io.github.landwarderer.neyon.core.ui.FragmentContainerActivity
import io.github.landwarderer.neyon.favourites.ui.list.FavouritesListFragment

class FavouritesActivity : FragmentContainerActivity(FavouritesListFragment::class.java) {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val categoryTitle = intent.getStringExtra(AppRouter.KEY_TITLE)
		if (categoryTitle != null) {
			title = categoryTitle
		}
	}
}
