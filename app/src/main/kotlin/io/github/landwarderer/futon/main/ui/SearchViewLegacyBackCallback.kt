package io.github.landwarderer.futon.main.ui

import android.os.Build
import androidx.activity.OnBackPressedCallback
import com.google.android.material.search.SearchView

@Deprecated("Use predictive back on TIRAMISU+")
class SearchViewLegacyBackCallback(
	private val searchView: SearchView
) : OnBackPressedCallback(searchView.isShowing), SearchView.TransitionListener {

	override fun handleOnBackPressed() {
		searchView.hide()
	}

	override fun onStateChanged(
		searchView: SearchView,
		previousState: SearchView.TransitionState,
		newState: SearchView.TransitionState
	) {
		isEnabled = newState >= SearchView.TransitionState.SHOWING
	}
}
