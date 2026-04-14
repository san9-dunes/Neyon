package io.github.landwarderer.neyon.suggestions.domain

import io.github.landwarderer.neyon.core.prefs.AppSettings
import io.github.landwarderer.neyon.list.domain.ListFilterOption
import io.github.landwarderer.neyon.list.domain.MangaListQuickFilter
import io.github.landwarderer.neyon.history.data.HistoryRepository
import io.github.landwarderer.neyon.core.model.distinctById
import io.github.landwarderer.neyon.core.model.UnknownMangaSource
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
                        add(ListFilterOption.Tag(MangaTag("📌 $title", title, UnknownMangaSource), isRemovable = true))
                }

                val randomTags = historyRepository.getList(0, 50).distinctById()
                        .flatMap { it.tags }
                        .filterNot { tag -> tag in tagsBlacklist || tag.title in pinnedTags }
                        .map { it.title }
                        .distinct()
                        .shuffled()
                        .take(5)

                randomTags.forEach { title ->
                        add(ListFilterOption.Tag(MangaTag(title, title, UnknownMangaSource)))
                }

                if (!settings.isNsfwContentDisabled && !settings.isSuggestionsExcludeNsfw) {
                        add(ListFilterOption.Macro.NSFW)
                        add(ListFilterOption.SFW)
                }
                suggestionRepository.getTopSources(3).mapTo(this) {
                        ListFilterOption.Source(it)
                }
        }

        override fun removeFilterOption(option: ListFilterOption) {
                super.removeFilterOption(option)
                if (option is ListFilterOption.Tag && option.isRemovable) {
                        val currentPinned = settings.suggestionsPinnedTags.toMutableSet()
                        val unpinnedTitle = option.tag.title.removePrefix("📌 ")
                        if (currentPinned.remove(unpinnedTitle)) {
                                settings.suggestionsPinnedTags = currentPinned
                        }
                }
        }
}
