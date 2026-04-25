package io.github.landwarderer.neyon.settings.sources.extension

import android.util.Log
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.ui.BaseViewModel
import io.github.landwarderer.neyon.core.util.ext.MutableEventFlow
import io.github.landwarderer.neyon.core.util.ext.call
import io.github.landwarderer.neyon.list.ui.model.ListModel
import io.github.landwarderer.neyon.mihon.MihonExtensionManager
import io.github.landwarderer.neyon.mihon.extensions.install.ExtensionInstallDownloadState
import io.github.landwarderer.neyon.mihon.extensions.install.ExtensionInstallService
import io.github.landwarderer.neyon.mihon.extensions.repo.ExternalExtensionRepoRepository
import io.github.landwarderer.neyon.mihon.extensions.repo.ExternalExtensionType
import io.github.landwarderer.neyon.mihon.extensions.repo.RepoAvailableExtension
import io.github.landwarderer.neyon.mihon.model.MihonLoadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ExtensionDownloaderViewModel @Inject constructor(
    private val repoRepository: ExternalExtensionRepoRepository,
    private val extensionManager: MihonExtensionManager,
    private val installService: ExtensionInstallService,
) : BaseViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val catalogExtensions = MutableStateFlow<List<RepoAvailableExtension>>(emptyList())
    private val searchQuery = MutableStateFlow<String?>(null)

    private val _intentAction = MutableEventFlow<android.content.Intent>()
    val intentAction = _intentAction
    private val _messageEvent = MutableEventFlow<Int>()
    val messageEvent = _messageEvent

    init {
        launchJob(Dispatchers.IO) {
            Log.d("ExtensionDownloaderViewModel", "fetching extensions")
            catalogExtensions.value = repoRepository.getCatalogExtensions(ExternalExtensionType.MIHON)
        }
        refresh()
    }

    val state: StateFlow<ExtensionDownloaderState> = combine(
        catalogExtensions,
        extensionManager.installedExtensions,
        installService.downloadStates,
        refreshing,
        searchQuery,
    ) { available, installed, downloads, isRefreshing, query ->
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
        val filtered = if (normalizedQuery == null) {
            available
        } else {
            available.filter { extension ->
                extension.name.contains(normalizedQuery, ignoreCase = true) ||
                    extension.pkgName.contains(normalizedQuery, ignoreCase = true) ||
                    extension.lang.contains(normalizedQuery, ignoreCase = true) ||
                    extension.repoName.contains(normalizedQuery, ignoreCase = true) ||
                    extension.sourceNames.any { it.contains(normalizedQuery, ignoreCase = true) }
            }
        }
        val items = filtered.map { extension ->
            val installedExtension = installed.find { it.pkgName == extension.pkgName }
            ExtensionItem(
                available = extension,
                installed = installedExtension,
                downloadState = downloads[extension.pkgName]
            )
        }
        ExtensionDownloaderState(
            items = items,
            isLoading = isRefreshing,
            query = normalizedQuery,
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, ExtensionDownloaderState())

    fun refresh() {
        launchJob(Dispatchers.IO) {
            refreshCatalog(refreshRepos = true)
        }
    }

    fun addRepo(indexUrl: String) {
        launchJob(Dispatchers.IO) {
            val url = indexUrl.trim()
            if (url.isEmpty()) {
                _messageEvent.call(R.string.repo_url_required)
                return@launchJob
            }
            when (val result = repoRepository.addRepo(ExternalExtensionType.MIHON, url)) {
                is ExternalExtensionRepoRepository.AddRepoResult.Success -> {
                    _messageEvent.call(R.string.repo_added)
                    refreshCatalog(refreshRepos = false)
                    extensionManager.loadExtensions()
                }

                is ExternalExtensionRepoRepository.AddRepoResult.DuplicateFingerprint,
                ExternalExtensionRepoRepository.AddRepoResult.RepoAlreadyExists,
                    -> _messageEvent.call(R.string.repo_already_exists)

                ExternalExtensionRepoRepository.AddRepoResult.InvalidUrl -> _messageEvent.call(R.string.invalid_repo_url)
                is ExternalExtensionRepoRepository.AddRepoResult.FetchFailed -> errorEvent.call(result.error)
            }
        }
    }

    private suspend fun refreshCatalog(refreshRepos: Boolean) {
        refreshing.value = true
        try {
            if (refreshRepos) {
                repoRepository.refresh(ExternalExtensionType.MIHON)
            }
            catalogExtensions.value = repoRepository.getCatalogExtensions(ExternalExtensionType.MIHON)
        } finally {
            refreshing.value = false
        }
    }

    fun performSearch(query: String?) {
        searchQuery.value = query?.trim()
    }

    fun installExtension(extension: RepoAvailableExtension) {
        launchJob {
            installService.getInstallPermissionIntent()?.let { intent ->
                _intentAction.call(intent)
                return@launchJob
            }
            val intent = installService.createInstallIntent(extension)
            if (intent != null) {
                _intentAction.call(intent)
            }
        }
    }

    fun uninstallExtension(pkgName: String) {
        val intent = installService.getUninstallIntent(pkgName)
        _intentAction.call(intent)
    }

    fun cancelDownload(pkgName: String) {
        installService.cancelDownload(pkgName)
    }
}

data class ExtensionDownloaderState(
    val items: List<ExtensionItem> = emptyList(),
    val isLoading: Boolean = false,
    val query: String? = null,
)

data class ExtensionItem(
    val available: RepoAvailableExtension,
    val installed: MihonLoadResult.Success?,
    val downloadState: ExtensionInstallDownloadState?,
) : ListModel {
    override fun areItemsTheSame(other: ListModel): Boolean {
        return other is ExtensionItem && available.pkgName == other.available.pkgName
    }
    val isInstalled: Boolean get() = installed != null
    val hasUpdate: Boolean get() = installed != null && available.versionCode > installed.versionCode
}
