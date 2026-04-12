package io.github.landwarderer.neyon.history.ui.migration

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.landwarderer.neyon.core.model.isLocal
import io.github.landwarderer.neyon.core.ui.BaseViewModel
import io.github.landwarderer.neyon.history.data.HistoryRepository
import io.github.landwarderer.neyon.alternatives.domain.AlternativesUseCase
import io.github.landwarderer.neyon.alternatives.domain.MigrateUseCase
import io.github.landwarderer.neyon.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.mapToSet
import javax.inject.Inject

data class MigrationPair(
    val oldManga: Manga,
    val newManga: Manga?,
    val isFetching: Boolean = false,
    val migrationSuccess: Boolean = false
)

@HiltViewModel
class HistoryMigrationViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val sourcesRepository: MangaSourcesRepository,
    private val alternativesUseCase: AlternativesUseCase,
    private val migrateUseCase: MigrateUseCase,
) : BaseViewModel() {

    private val _items = MutableStateFlow<List<MigrationPair>>(emptyList())
    val items: StateFlow<List<MigrationPair>> = _items.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isMigrating = MutableStateFlow(false)
    val isMigrating: StateFlow<Boolean> = _isMigrating.asStateFlow()

    private var scanJob: Job? = null
    private val mutex = Mutex()

    fun scanUnavailableHistory() {
        if (_isScanning.value) return
        scanJob?.cancel()
        scanJob = launchJob(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val enabledSources = sourcesRepository.getEnabledSources().mapToSet { it.name }
                val unavailableHistoryManga = mutableListOf<Manga>()
                var offset = 0
                val pageSize = 100
                while (true) {
                    val page = historyRepository.getList(offset = offset, limit = pageSize)
                    if (page.isEmpty()) break
                    unavailableHistoryManga += page.filter { manga ->
                        !manga.isLocal && manga.source.name !in enabledSources
                    }
                    offset += page.size
                }

                // Initial state
                val pairs = unavailableHistoryManga.map {
                    MigrationPair(oldManga = it, newManga = null, isFetching = true, migrationSuccess = false)
                }
                _items.value = pairs

                // Incrementally fetch alternatives
                pairs.forEachIndexed { index, pair ->
                    launch {
                        val bestMatch = try {
                            alternativesUseCase(pair.oldManga, throughDisabledSources = false)
                                .take(1)
                                .toList()
                                .firstOrNull()
                        } catch (e: Exception) {
                            null
                        }
                        
                        mutex.withLock {
                            val currentList = _items.value.toMutableList()
                            val currentIndex = currentList.indexOfFirst { it.oldManga.id == pair.oldManga.id }
                            if (currentIndex != -1) {
                                currentList[currentIndex] = currentList[currentIndex].copy(newManga = bestMatch, isFetching = false)
                                _items.value = currentList
                            }
                        }
                    }
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun migrateAll() {
        if (_isMigrating.value || _isScanning.value) return
        launchJob(Dispatchers.IO) {
            _isMigrating.value = true
            try {
                val targets = _items.value.filter { it.newManga != null && !it.migrationSuccess }
                
                for (target in targets) {
                    val newManga = target.newManga ?: continue
                    migrateUseCase(target.oldManga, newManga)
                    
                    mutex.withLock {
                        val currentList = _items.value.toMutableList()
                        val currentIndex = currentList.indexOfFirst { it.oldManga.id == target.oldManga.id }
                        if (currentIndex != -1) {
                            currentList[currentIndex] = currentList[currentIndex].copy(migrationSuccess = true)
                            _items.value = currentList
                        }
                    }
                }
            } finally {
                _isMigrating.value = false
                // Remove successfully migrated items from the view
                _items.value = _items.value.filter { !it.migrationSuccess }
            }
        }
    }
}
