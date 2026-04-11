package io.github.landwarderer.neyon.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextWatcher
import android.text.Editable
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.model.getTitle
import io.github.landwarderer.neyon.core.prefs.AppSettings
import io.github.landwarderer.neyon.core.ui.BasePreferenceFragment
import io.github.landwarderer.neyon.explore.data.MangaSourcesRepository
import io.github.landwarderer.neyon.settings.utils.MultiAutoCompleteTextViewPreference
import io.github.landwarderer.neyon.settings.utils.TagsAutoCompleteProvider
import io.github.landwarderer.neyon.suggestions.ui.SuggestionsWorker
import org.koitharu.kotatsu.parsers.model.MangaSource
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

		// Replace MultiSelectListPreference with a searchable custom dialog
		findPreference<Preference>(AppSettings.KEY_SUGGESTION_SOURCES_WHITELIST)?.run {
			updateSourceSummary(this)
			setOnPreferenceClickListener {
				showSearchableSourcePicker()
				true
			}
		}
	}

	private fun updateSourceSummary(pref: Preference) {
		val count = settings.suggestionSourcesWhitelist.size
		pref.summary = if (count == 0) {
			getString(R.string.suggestions_manage_sources_summary)
		} else {
			requireContext().resources.getQuantityString(
				R.plurals.sources_selected,
				count,
				count,
			)
		}
	}

	private fun showSearchableSourcePicker() {
		lifecycleScope.launch {
			val allSources = withContext(Dispatchers.IO) { sourcesRepository.getEnabledSources() }
			val currentSelection = settings.suggestionSourcesWhitelist.toMutableSet()
			// Parallel arrays for display
			val sourceNames = allSources.map { it.name }
			val sourceTitles = allSources.map { it.getTitle(requireContext()) }
			var filteredIndices = sourceTitles.indices.toList()

			// Build checklist with search bar programmatically
			val context = requireContext()
			val container = LinearLayout(context).apply {
				orientation = LinearLayout.VERTICAL
				val dp8 = (8 * resources.displayMetrics.density).toInt()
				setPadding(dp8 * 2, dp8, dp8 * 2, 0)
			}

			val searchField = EditText(context).apply {
				hint = getString(R.string.search)
				isSingleLine = true
				val dp8 = (8 * resources.displayMetrics.density).toInt()
				setPadding(0, dp8, 0, dp8)
			}
			container.addView(searchField)

			val recyclerView = androidx.recyclerview.widget.RecyclerView(context).apply {
				layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
			}
			container.addView(recyclerView, LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT,
				1f
			))

			val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
				override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
					val checkBox = CheckBox(context).apply {
						layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(
							android.view.ViewGroup.LayoutParams.MATCH_PARENT,
							android.view.ViewGroup.LayoutParams.WRAP_CONTENT
						)
						val dp4 = (4 * resources.displayMetrics.density).toInt()
						setPadding(0, dp4, 0, dp4)
					}
					return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(checkBox) {}
				}

				override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
					val actualIndex = filteredIndices[position]
					val checkBox = holder.itemView as CheckBox
					
					checkBox.setOnCheckedChangeListener(null)
					checkBox.text = sourceTitles[actualIndex]
					checkBox.isChecked = currentSelection.contains(sourceNames[actualIndex])
					
					checkBox.setOnCheckedChangeListener { _, isChecked ->
						if (isChecked) {
							currentSelection.add(sourceNames[actualIndex])
						} else {
							currentSelection.remove(sourceNames[actualIndex])
						}
					}
				}

				override fun getItemCount(): Int = filteredIndices.size
			}
			recyclerView.adapter = adapter

			// Live search filtering with debounce
			var searchJob: kotlinx.coroutines.Job? = null
			searchField.addTextChangedListener(object : TextWatcher {
				override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
				override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
				override fun afterTextChanged(editable: Editable) {
					searchJob?.cancel()
					searchJob = lifecycleScope.launch {
						kotlinx.coroutines.delay(300) // Debounce time
						val query = editable.toString().lowercase()
						filteredIndices = if (query.isEmpty()) {
							sourceTitles.indices.toList()
						} else {
							sourceTitles.indices.filter { sourceTitles[it].lowercase().contains(query) }
						}
						adapter.notifyDataSetChanged()
					}
				}
			})

			AlertDialog.Builder(context)
				.setTitle(R.string.suggestions_manage_sources)
				.setView(container)
				.setPositiveButton(android.R.string.ok) { _, _ ->
					settings.suggestionSourcesWhitelist = currentSelection.toSet()
					findPreference<Preference>(AppSettings.KEY_SUGGESTION_SOURCES_WHITELIST)?.let {
						updateSourceSummary(it)
					}
				}
				.setNegativeButton(android.R.string.cancel, null)
				.show()
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
