package io.github.landwarderer.neyon.reader.ui

import android.content.res.Configuration
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import io.github.landwarderer.neyon.core.prefs.AppSettings
import io.github.landwarderer.neyon.core.prefs.ReaderMode
import io.github.landwarderer.neyon.core.util.ext.findKeyByValue
import io.github.landwarderer.neyon.reader.ui.pager.BaseReaderFragment
import io.github.landwarderer.neyon.reader.ui.pager.doublepage.DoubleReaderFragment
import io.github.landwarderer.neyon.reader.ui.pager.doublereversed.ReversedDoubleReaderFragment
import io.github.landwarderer.neyon.reader.ui.pager.reversed.ReversedReaderFragment
import io.github.landwarderer.neyon.reader.ui.pager.standard.PagerReaderFragment
import io.github.landwarderer.neyon.reader.ui.pager.vertical.VerticalReaderFragment
import io.github.landwarderer.neyon.reader.ui.pager.webtoon.WebtoonReaderFragment
import java.util.EnumMap

class ReaderManager(
	private val fragmentManager: FragmentManager,
	private val container: FragmentContainerView,
	settings: AppSettings,
) {

	private val modeMap = EnumMap<ReaderMode, Class<out BaseReaderFragment<*>>>(ReaderMode::class.java)

	init {
		// Default to single-page mode. The correct initial state (considering landscape +
		// foldable settings) is set by applyDoubleModeAuto() in ReaderActivity.onCreate(),
		// which is called immediately after this ReaderManager is constructed.
		invalidateTypesMap(useDoublePages = false)
	}

	val currentReader: BaseReaderFragment<*>?
		get() = fragmentManager.findFragmentById(container.id) as? BaseReaderFragment<*>

	val currentMode: ReaderMode?
		get() {
			val readerClass = currentReader?.javaClass ?: return null
			return modeMap.findKeyByValue(readerClass)
		}

	fun replace(newMode: ReaderMode) {
		val readerClass = requireNotNull(modeMap[newMode])
		fragmentManager.commit {
			setReorderingAllowed(true)
			replace(container.id, readerClass, null, null)
		}
	}

	fun setDoubleReaderMode(isEnabled: Boolean) {
		val mode = currentMode
		val prevReader = currentReader?.javaClass
		invalidateTypesMap(isEnabled)
		val newReader = modeMap[mode]
		if (mode != null && newReader != prevReader) {
			replace(mode)
		}
	}

	private fun invalidateTypesMap(useDoublePages: Boolean) {
		modeMap[ReaderMode.STANDARD] = if (useDoublePages) {
			DoubleReaderFragment::class.java
		} else {
			PagerReaderFragment::class.java
		}
		modeMap[ReaderMode.REVERSED] = if (useDoublePages) {
			ReversedDoubleReaderFragment::class.java
		} else {
			ReversedReaderFragment::class.java
		}
		modeMap[ReaderMode.WEBTOON] = WebtoonReaderFragment::class.java
		modeMap[ReaderMode.VERTICAL] = VerticalReaderFragment::class.java
	}

	private fun isLandscape() = container.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}
