package io.github.landwarderer.neyon.reader.ui.pager

import android.os.Bundle
import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import io.github.landwarderer.neyon.core.prefs.ReaderAnimation
import io.github.landwarderer.neyon.core.ui.BaseFragment
import io.github.landwarderer.neyon.core.ui.widgets.ZoomControl
import io.github.landwarderer.neyon.core.util.ext.isAnimationsEnabled
import io.github.landwarderer.neyon.reader.ui.ReaderState
import io.github.landwarderer.neyon.reader.ui.ReaderViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class BaseReaderFragment<B : ViewBinding> : BaseFragment<B>(), ZoomControl.ZoomControlListener {

	protected val viewModel by activityViewModels<ReaderViewModel>()

	protected var readerAdapter: BaseReaderAdapter<*>? = null
		private set

	override fun onViewBindingCreated(binding: B, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		readerAdapter = onCreateAdapter()

		// Use collectLatest so that if content emits again while onPagesChanged is still
		// running (e.g. a chapter boundary load completes mid-diff), the in-flight call is
		// cancelled before the new one starts — preventing adapter state corruption.
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				viewModel.content.collectLatest { content ->
					val currentState = viewModel.getCurrentState()
					val pendingState = when {
						// If content.state is null and we have pages, use getCurrentState
						content.state == null
								&& content.pages.isNotEmpty()
								&& readerAdapter?.hasItems != true -> currentState

						// use currentState only if it matches the current pages (to avoid the error message)
						readerAdapter?.hasItems != true
								&& content.state != currentState
								&& currentState != null
								&& content.pages.any { page -> page.chapterId == currentState.chapterId } -> currentState

						// Otherwise, use content.state (normal flow, mode switch, chapter change)
						else -> content.state
					}
					onPagesChanged(content.pages, pendingState)
				}
			}
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onPause() {
		super.onPause()
		viewModel.saveCurrentState(getCurrentState())
	}

	override fun onDestroyView() {
		viewModel.saveCurrentState(getCurrentState())
		readerAdapter = null
		super.onDestroyView()
	}

	protected fun requireAdapter() = checkNotNull(readerAdapter) {
		"Adapter was not created or already destroyed"
	}

	protected fun isAnimationEnabled(): Boolean {
		return context?.isAnimationsEnabled == true && viewModel.pageAnimation.value != ReaderAnimation.NONE
	}

	abstract fun switchPageBy(delta: Int)

	abstract fun switchPageTo(position: Int, smooth: Boolean)

	open fun scrollBy(delta: Int, smooth: Boolean): Boolean = false

	abstract fun getCurrentState(): ReaderState?

	protected abstract fun onCreateAdapter(): BaseReaderAdapter<*>

	protected abstract suspend fun onPagesChanged(pages: List<ReaderPage>, pendingState: ReaderState?)
}
