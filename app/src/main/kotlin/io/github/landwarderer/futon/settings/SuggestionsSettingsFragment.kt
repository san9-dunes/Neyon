package io.github.landwarderer.futon.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.BasePreferenceFragment
import io.github.landwarderer.futon.settings.utils.MultiAutoCompleteTextViewPreference
import io.github.landwarderer.futon.settings.utils.TagsAutoCompleteProvider
import io.github.landwarderer.futon.suggestions.ui.SuggestionsWorker
import io.github.landwarderer.futon.explore.data.MangaSourcesRepository
import io.github.landwarderer.futon.core.model.getTitle
import androidx.preference.MultiSelectListPreference
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SuggestionsSettingsFragment : BasePreferenceFragment(R.string.suggestions),
	SharedPreferences.OnSharedPreferenceChangeListener {

	@Inject
	lateinit var tagsCompletionProvider: TagsAutoCompleteProvider

	@Inject
	lateinit var suggestionsScheduler: SuggestionsWorker.Scheduler

	@Inject
	lateinit var sourcesRepository: MangaSourcesRepository

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		settings.subscribe(this)
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_suggestions)

		findPreference<MultiAutoCompleteTextViewPreference>(AppSettings.KEY_SUGGESTIONS_EXCLUDE_TAGS)?.run {
			autoCompleteProvider = tagsCompletionProvider
			summaryProvider = MultiAutoCompleteTextViewPreference.SimpleSummaryProvider(summary)
		}

		findPreference<MultiSelectListPreference>(AppSettings.KEY_SUGGESTION_SOURCES_WHITELIST)?.run {
			lifecycleScope.launch {
				val sources = withContext(Dispatchers.IO) {
					sourcesRepository.getEnabledSources()
				}
				entries = sources.map { it.getTitle(requireContext()) }.toTypedArray()
				entryValues = sources.map { it.name }.toTypedArray()
			}
			setOnPreferenceChangeListener { _, newValue ->
				val set = newValue as Set<*>
				if (set.size > 5) {
					com.google.android.material.snackbar.Snackbar.make(
						listView,
						R.string.items_limit_exceeded,
						com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
					).show()
					false
				} else {
					true
				}
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		settings.unsubscribe(this)
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		if (settings.isSuggestionsEnabled && (key == AppSettings.KEY_SUGGESTIONS
				|| key == AppSettings.KEY_SUGGESTIONS_EXCLUDE_TAGS
				|| key == AppSettings.KEY_SUGGESTIONS_EXCLUDE_NSFW)
		) {
			updateSuggestions()
		}
	}

	private fun updateSuggestions() {
		lifecycleScope.launch(Dispatchers.IO) {
			suggestionsScheduler.startNow()
		}
	}
}
