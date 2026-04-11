package io.github.landwarderer.futon.suggestions.domain

import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.list.domain.ListFilterOption
import io.github.landwarderer.futon.list.domain.MangaListQuickFilter
import io.github.landwarderer.futon.history.data.HistoryRepository
import io.github.landwarderer.futon.core.model.distinctById
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.MangaSource
import javax.inject.Inject

class SuggestionsListQuickFilter @Inject constructor(
        private val settings: AppSettings,
        private val suggestionRepository: SuggestionRepository,
        private val historyRepository: HistoryRepository,
) : MangaListQuickFilter(settings) {

        override suspend fun getAvailableFilterOptions(): List<ListFilterOption> = buildList(10) {
                val tagsBlacklist = TagsBlacklist(settings.suggestionsTagsBlacklist, 0.4f)
                val pinnedTags = settings.suggestionsPinnedTags

                pinnedTags.forEach { title ->
                        add(ListFilterOption.Tag(MangaTag("📌 $title", title, MangaSource(""))))
                }

                val randomTags = historyRepository.getList(0, 50).distinctById()
                        .flatMap { it.manga.tags }
                        .filterNot { tag -> tag in tagsBlacklist || tag.title in pinnedTags }
                        .map { it.title }
                        .distinct()
                        .shuffled()
                        .take(5)

                randomTags.forEach { title ->
                        add(ListFilterOption.Tag(MangaTag(title, title, MangaSource(""))))
                }

                if (!settings.isNsfwContentDisabled && !settings.isSuggestionsExcludeNsfw) {
                        add(ListFilterOption.Macro.NSFW)
                        add(ListFilterOption.SFW)
                }
                suggestionRepository.getTopSources(3).mapTo(this) {
                        ListFilterOption.Source(it)
                }
        }
}
