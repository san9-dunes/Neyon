package io.github.landwarderer.neyon.main.ui

import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.view.iterator
import androidx.core.view.size
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.bookmarks.ui.AllBookmarksFragment
import io.github.landwarderer.neyon.core.nav.AppRouter
import io.github.landwarderer.neyon.core.prefs.AppSettings
import io.github.landwarderer.neyon.core.prefs.NavItem
import io.github.landwarderer.neyon.core.ui.util.RecyclerViewOwner
import io.github.landwarderer.neyon.core.ui.widgets.SlidingBottomNavigationView
import io.github.landwarderer.neyon.core.util.ext.buildBundle
import io.github.landwarderer.neyon.core.util.ext.setContentDescriptionAndTooltip
import io.github.landwarderer.neyon.core.util.ext.smoothScrollToTop
import io.github.landwarderer.neyon.databinding.NavigationRailFabBinding
import io.github.landwarderer.neyon.explore.ui.ExploreFragment
import io.github.landwarderer.neyon.favourites.ui.container.FavouritesContainerFragment
import io.github.landwarderer.neyon.history.ui.HistoryListFragment
import io.github.landwarderer.neyon.local.ui.LocalListFragment
import io.github.landwarderer.neyon.suggestions.ui.SuggestionsFragment
import io.github.landwarderer.neyon.tracker.ui.feed.FeedFragment
import io.github.landwarderer.neyon.tracker.ui.updates.UpdatesFragment
import java.util.LinkedList
import com.google.android.material.R as materialR

class MainNavigationDelegate(
	private val navBar: NavigationBarView,
	private val viewPager: ViewPager2,
	private val fragmentManager: FragmentManager,
	private val lifecycle: Lifecycle,
	private val settings: AppSettings,
) : OnBackPressedCallback(false),
	NavigationBarView.OnItemSelectedListener,
	NavigationBarView.OnItemReselectedListener, View.OnClickListener {

	private val listeners = LinkedList<OnFragmentChangedListener>()
	val navRailHeader = (navBar as? NavigationRailView)?.headerView?.let {
		NavigationRailFabBinding.bind(it)
	}

	private var currentNavItems = emptyList<NavItem>()

	private val pagerAdapter = object : FragmentStateAdapter(fragmentManager, lifecycle) {
		override fun getItemCount() = currentNavItems.size

		override fun createFragment(position: Int): Fragment {
			val itemId = currentNavItems[position].id
			val fragmentClass = getFragmentClass(itemId)
			val fragment = instantiateFragment(fragmentClass)
			val args = buildBundle(1) {
				putBoolean(AppRouter.KEY_IS_BOTTOMTAB, true)
			}
			fragment.arguments = args
			return fragment
		}

		override fun getItemId(position: Int): Long = currentNavItems[position].id.toLong()
		override fun containsItem(itemId: Long): Boolean = currentNavItems.any { it.id.toLong() == itemId }
	}

	val primaryFragment: Fragment?
		get() {
			val position = viewPager.currentItem
			if (currentNavItems.isEmpty() || position !in currentNavItems.indices) return null
			val itemId = currentNavItems[position].id
			return fragmentManager.findFragmentByTag("f$itemId")
		}

	init {
		viewPager.adapter = pagerAdapter
		viewPager.offscreenPageLimit = MAX_ITEM_COUNT
		viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
			override fun onPageSelected(position: Int) {
				if (currentNavItems.isNotEmpty() && position in currentNavItems.indices) {
					val itemId = currentNavItems[position].id
					if (navBar.selectedItemId != itemId) {
						navBar.selectedItemId = itemId
					}
					val fragment = primaryFragment
					if (fragment != null) {
						onFragmentChanged(fragment, fromUser = true)
					}
				}
			}
		})

		navBar.setOnItemSelectedListener(this)
		navBar.setOnItemReselectedListener(this)
		navRailHeader?.run {
			root.updateLayoutParams<FrameLayout.LayoutParams> {
				gravity = Gravity.TOP or Gravity.CENTER
			}
			val horizontalPadding = (navBar as NavigationRailView).itemActiveIndicatorMarginHorizontal
			root.setPadding(horizontalPadding, 0, horizontalPadding, 0)
			buttonExpand.setOnClickListener(this@MainNavigationDelegate)
			buttonExpand.setContentDescriptionAndTooltip(R.string.expand)
			railFab.isExtended = false
			railFab.isAnimationEnabled = false
		}
	}

	override fun onNavigationItemSelected(item: MenuItem): Boolean {
		val index = currentNavItems.indexOfFirst { it.id == item.itemId }
		if (index != -1) {
			if (viewPager.currentItem != index) {
				viewPager.setCurrentItem(index, true)
			} else {
				onNavigationItemReselected()
			}
			return true
		}
		return false
	}

	override fun onNavigationItemReselected(item: MenuItem) {
		onNavigationItemReselected()
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_expand -> {
				if (navBar is NavigationRailView) {
					setNavbarIsExpanded(!navBar.isExpanded)
				}
			}
		}
	}

	override fun handleOnBackPressed() {
		val firstVisibleItem = currentNavItems.firstOrNull() ?: return
		if (navBar.selectedItemId != firstVisibleItem.id) {
			navBar.selectedItemId = firstVisibleItem.id
		}
	}

	fun onCreate(lifecycleOwner: LifecycleOwner, savedInstanceState: Bundle?) {
		currentNavItems = settings.mainNavItems.filter { it.isAvailable(settings) }.take(MAX_ITEM_COUNT)
		if (navBar.menu.isEmpty()) {
			createMenu(settings.mainNavItems, navBar.menu)
		}
		observeSettings(lifecycleOwner)
		
		if (savedInstanceState == null) {
			val firstItem = currentNavItems.firstOrNull()
			if (firstItem != null && navBar.selectedItemId != firstItem.id) {
				navBar.selectedItemId = firstItem.id
			}
		}
		syncSelectedItem()
	}

	fun observeTitle() = callbackFlow {
		val listener = OnFragmentChangedListener { f, _ ->
			trySendBlocking(getItemId(f))
		}
		addOnFragmentChangedListener(listener)
		awaitClose { removeOnFragmentChangedListener(listener) }
	}.map {
		navBar.menu.findItem(it)?.title
	}

	fun setCounter(item: NavItem, counter: Int) {
		setCounter(item.id, counter)
	}

	fun syncSelectedItem() {
		val fragment = primaryFragment ?: return
		onFragmentChanged(fragment, fromUser = false)
		val itemId = getItemId(fragment)
		if (navBar.selectedItemId != itemId) {
			navBar.selectedItemId = itemId
		}
	}

	private fun setCounter(@IdRes id: Int, counter: Int) {
		if (counter == 0) {
			navBar.getBadge(id)?.isVisible = false
		} else {
			val badge = navBar.getOrCreateBadge(id)
			if (counter < 0) {
				badge.clearNumber()
			} else {
				badge.number = counter
			}
			badge.isVisible = true
		}
	}

	fun setItemVisibility(@IdRes itemId: Int, isVisible: Boolean) {
		val item = navBar.menu.findItem(itemId) ?: return
		item.isVisible = isVisible
		if (item.isChecked && !isVisible) {
			val firstItem = currentNavItems.firstOrNull() ?: return
			navBar.selectedItemId = firstItem.id
		}
	}

	fun addOnFragmentChangedListener(listener: OnFragmentChangedListener) {
		listeners.add(listener)
	}

	fun removeOnFragmentChangedListener(listener: OnFragmentChangedListener) {
		listeners.remove(listener)
	}

	private fun getFragmentClass(@IdRes itemId: Int): Class<out Fragment> = when (itemId) {
		R.id.nav_history -> HistoryListFragment::class.java
		R.id.nav_favorites -> FavouritesContainerFragment::class.java
		R.id.nav_explore -> ExploreFragment::class.java
		R.id.nav_feed -> FeedFragment::class.java
		R.id.nav_local -> LocalListFragment::class.java
		R.id.nav_suggestions -> SuggestionsFragment::class.java
		R.id.nav_bookmarks -> AllBookmarksFragment::class.java
		R.id.nav_updated -> UpdatesFragment::class.java
		else -> ExploreFragment::class.java
	}

	private fun getItemId(fragment: Fragment) = when (fragment) {
		is HistoryListFragment -> R.id.nav_history
		is FavouritesContainerFragment -> R.id.nav_favorites
		is ExploreFragment -> R.id.nav_explore
		is FeedFragment -> R.id.nav_feed
		is LocalListFragment -> R.id.nav_local
		is SuggestionsFragment -> R.id.nav_suggestions
		is AllBookmarksFragment -> R.id.nav_bookmarks
		is UpdatesFragment -> R.id.nav_updated
		else -> 0
	}

	private fun onNavigationItemReselected() {
		val recyclerView = (primaryFragment as? RecyclerViewOwner)?.recyclerView ?: return
		recyclerView.smoothScrollToTop()
	}

	private fun onFragmentChanged(fragment: Fragment, fromUser: Boolean) {
		val firstVisibleItem = currentNavItems.firstOrNull()
		isEnabled = getItemId(fragment) != firstVisibleItem?.id
		listeners.forEach { it.onFragmentChanged(fragment, fromUser) }
	}

	private fun createMenu(items: List<NavItem>, menu: Menu) {
		for (item in items) {
			menu.add(Menu.NONE, item.id, Menu.NONE, item.title)
				.setIcon(item.icon)
			if (menu.size >= MAX_ITEM_COUNT) {
				break
			}
		}
	}

	private fun instantiateFragment(fragmentClass: Class<out Fragment>): Fragment {
		val classLoader = navBar.context.classLoader
		return fragmentManager.fragmentFactory.instantiate(classLoader, fragmentClass.name)
	}

	private fun observeSettings(lifecycleOwner: LifecycleOwner) {
		settings.observe(AppSettings.KEY_TRACKER_ENABLED, AppSettings.KEY_SUGGESTIONS, AppSettings.KEY_NAV_LABELS)
			.onEach {
				val oldItems = currentNavItems
				currentNavItems = settings.mainNavItems.filter { item -> item.isAvailable(settings) }.take(MAX_ITEM_COUNT)
				
				setItemVisibility(R.id.nav_suggestions, settings.isSuggestionsEnabled)
				setItemVisibility(R.id.nav_feed, settings.isTrackerEnabled)
				setNavbarIsLabeled(settings.isNavLabelsVisible)
				
				if (oldItems != currentNavItems) {
					pagerAdapter.notifyDataSetChanged()
					if (viewPager.currentItem >= currentNavItems.size) {
						viewPager.setCurrentItem(currentNavItems.size - 1, false)
					}
				}
			}.launchIn(lifecycleOwner.lifecycleScope)
	}

	private fun setNavbarIsLabeled(value: Boolean) {
		if (navBar is SlidingBottomNavigationView) {
			navBar.minimumHeight = navBar.resources.getDimensionPixelSize(
				if (value) {
					materialR.dimen.m3_bottom_nav_min_height
				} else {
					R.dimen.nav_bar_height_compact
				},
			)
		}
		navRailHeader?.buttonExpand?.isVisible = value
		if (!value) {
			setNavbarIsExpanded(false)
		}
		navBar.labelVisibilityMode = if (value) {
			NavigationBarView.LABEL_VISIBILITY_LABELED
		} else {
			NavigationBarView.LABEL_VISIBILITY_UNLABELED
		}
	}

	private fun setNavbarIsExpanded(value: Boolean) {
		if (navBar !is NavigationRailView) {
			return
		}
		if (value) {
			navBar.expand()
			navRailHeader?.run {
				root.updateLayoutParams<FrameLayout.LayoutParams> {
					gravity = Gravity.TOP or Gravity.START
				}
				railFab.extend()
				buttonExpand.setImageResource(R.drawable.ic_drawer_menu_open)
				buttonExpand.setContentDescriptionAndTooltip(R.string.collapse)
				val horizontalPadding = navBar.itemActiveIndicatorExpandedMarginHorizontal
				root.setPadding(horizontalPadding, 0, horizontalPadding, 0)
			}
		} else {
			navBar.collapse()
			navRailHeader?.run {
				root.updateLayoutParams<FrameLayout.LayoutParams> {
					gravity = Gravity.TOP or Gravity.CENTER
				}
				railFab.shrink()
				buttonExpand.setImageResource(R.drawable.ic_drawer_menu)
				buttonExpand.setContentDescriptionAndTooltip(R.string.expand)
				val horizontalPadding = navBar.itemActiveIndicatorMarginHorizontal
				root.setPadding(horizontalPadding, 0, horizontalPadding, 0)
			}
		}
	}

	fun interface OnFragmentChangedListener {
		fun onFragmentChanged(fragment: Fragment, fromUser: Boolean)
	}

	companion object {
		const val MAX_ITEM_COUNT = 6
	}
}
