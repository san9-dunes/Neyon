package io.github.landwarderer.neyon.settings.sources.manage

import android.content.Context
import androidx.room.InvalidationTracker
import dagger.hilt.android.ViewModelLifecycle
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.LocalizedAppContext
import io.github.landwarderer.neyon.core.db.TABLE_SOURCES
import io.github.landwarderer.neyon.core.model.getTitle
import io.github.landwarderer.neyon.core.model.isNsfw
import io.github.landwarderer.neyon.core.model.unwrap
import io.github.landwarderer.neyon.core.prefs.AppSettings
import io.github.landwarderer.neyon.core.util.ext.lifecycleScope
import io.github.landwarderer.neyon.explore.data.MangaSourcesRepository
import io.github.landwarderer.neyon.explore.data.SourcesSortOrder
import io.github.landwarderer.neyon.mihon.MihonExtensionManager
import io.github.landwarderer.neyon.mihon.model.MihonMangaSource
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.util.mapToSet
import io.github.landwarderer.neyon.settings.sources.model.SourceConfigItem
import javax.inject.Inject

@ViewModelScoped
class SourcesListProducer @Inject constructor(
	lifecycle: ViewModelLifecycle,
	@LocalizedAppContext private val context: Context,
	private val repository: MangaSourcesRepository,
	private val settings: AppSettings,
	private val mihonExtensionManager: MihonExtensionManager,
) : InvalidationTracker.Observer(TABLE_SOURCES) {

	private val scope = lifecycle.lifecycleScope
	private var query: String = ""
	val list = MutableStateFlow(emptyList<SourceConfigItem>())

	private var job = scope.launch(Dispatchers.IO) {
		list.value = buildList()
	}

	init {
		settings.observeChanges()
			.filter { it == AppSettings.KEY_TIPS_CLOSED || it == AppSettings.KEY_DISABLE_NSFW || it == AppSettings.KEY_DISABLE_SFW }
			.flowOn(Dispatchers.IO)
			.onEach { onInvalidated(emptySet()) }
			.launchIn(scope)
		mihonExtensionManager.installedExtensions
			.flowOn(Dispatchers.IO)
			.onEach { onInvalidated(emptySet()) }
			.launchIn(scope)
	}

	override fun onInvalidated(tables: Set<String>) {
		val prevJob = job
		job = scope.launch(Dispatchers.IO) {
			prevJob.cancelAndJoin()
			list.update { buildList() }
		}
	}

	fun setQuery(value: String) {
		this.query = value
		onInvalidated(emptySet())
	}

	private suspend fun buildList(): List<SourceConfigItem> {
		val allEnabled = repository.getEnabledSources()
		val enabledSources = allEnabled.filter { it.unwrap() is MangaParserSource || it.unwrap() is MihonMangaSource }
		val pinned = repository.getPinnedSources().mapToSet { it.name }
		val isNsfwDisabled = settings.isNsfwContentDisabled
		val isSfwDisabled = settings.isSfwContentDisabled
		val isReorderAvailable = settings.sourcesSortOrder == SourcesSortOrder.MANUAL
		val isDisableAvailable = !settings.isAllSourcesEnabled
		val withTip = isReorderAvailable && settings.isTipEnabled(TIP_REORDER)
		val enabledSet = enabledSources.toSet()
		if (query.isNotEmpty()) {
			return enabledSources.mapNotNull {
				if (!it.getTitle(context).contains(query, ignoreCase = true)) {
					return@mapNotNull null
				}
				val isExtension = it.unwrap() is MihonMangaSource
				SourceConfigItem.SourceItem(
					source = it,
					isEnabled = it in enabledSet,
					isDraggable = false,
					isAvailable = (!isNsfwDisabled || !it.isNsfw()) && (!isSfwDisabled || it.isNsfw()),
					isPinned = it.name in pinned,
					isDisableAvailable = if (isExtension) false else isDisableAvailable,
				)
			}.ifEmpty {
				listOf(SourceConfigItem.EmptySearchResult)
			}
		}
		val result = ArrayList<SourceConfigItem>(enabledSources.size + 1)
		if (enabledSources.isNotEmpty()) {
			if (withTip) {
				result += SourceConfigItem.Tip(
					TIP_REORDER,
					R.drawable.ic_tap_reorder,
					R.string.sources_reorder_tip,
				)
			}
			enabledSources.mapTo(result) {
				val isExtension = it.unwrap() is MihonMangaSource
				SourceConfigItem.SourceItem(
					source = it,
					isEnabled = true,
					isDraggable = if (isExtension) false else isReorderAvailable,
					isAvailable = false,
					isPinned = it.name in pinned,
					isDisableAvailable = if (isExtension) false else isDisableAvailable,
				)
			}
		}
		return result
	}

	companion object {

		const val TIP_REORDER = "src_reorder"
	}
}
