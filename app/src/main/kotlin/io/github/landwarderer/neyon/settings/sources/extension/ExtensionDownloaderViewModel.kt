package io.github.landwarderer.neyon.settings.sources.extension

import android.util.Log
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.prefs.AppSettings
import io.github.landwarderer.neyon.core.ui.BaseViewModel
import io.github.landwarderer.neyon.core.util.ext.MutableEventFlow
import io.github.landwarderer.neyon.core.util.ext.call
import io.github.landwarderer.neyon.list.ui.model.ListModel
import io.github.landwarderer.neyon.mihon.MihonExtensionManager
import io.github.landwarderer.neyon.mihon.extensions.install.ExtensionInstallDownloadState
import io.github.landwarderer.neyon.mihon.extensions.install.ExtensionInstallService
import io.github.landwarderer.neyon.mihon.extensions.repo.ExternalExtensionRepo
import io.github.landwarderer.neyon.mihon.extensions.repo.ExternalExtensionRepoRepository
import io.github.landwarderer.neyon.mihon.extensions.repo.ExternalExtensionType
import io.github.landwarderer.neyon.mihon.extensions.repo.RepoAvailableExtension
import io.github.landwarderer.neyon.mihon.model.MihonLoadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    private val settings: AppSettings,
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
            repoRepository.seedBuiltInReposIfNeeded()
            Log.d("ExtensionDownloaderViewModel", "fetching extensions")
            catalogExtensions.value = repoRepository.getCatalogExtensions(ExternalExtensionType.MIHON)
        }
        refresh()
    }

    fun observeRepos(): Flow<List<ExternalExtensionRepo>> =
        repoRepository.observeByType(ExternalExtensionType.MIHON)

    val state: StateFlow<ExtensionDownloaderState> = combine(
        combine(catalogExtensions, extensionManager.untrustedExtensions) { catalog, untrusted -> catalog to untrusted },
        extensionManager.installedExtensions,
        installService.downloadStates,
        refreshing,
        searchQuery,
    ) { (available, untrustedList), installed, downloads, isRefreshing, query ->
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()

        // Build sets for quick lookup
        val installedPkgNames = installed.map { it.pkgName }.toSet()
        val untrustedPkgNames = untrustedList.map { it.pkgName }.toSet()

        // Filter catalog: apply search and optional NSFW exclusion
        val filtered = available
            .let { list -> if (settings.isNsfwContentDisabled) list.filter { !it.isNsfw } else list }
            .let { list ->
                if (normalizedQuery == null) list
                else list.filter { extension ->
                    extension.name.contains(normalizedQuery, ignoreCase = true) ||
                        extension.pkgName.contains(normalizedQuery, ignoreCase = true) ||
                        extension.lang.contains(normalizedQuery, ignoreCase = true) ||
                        extension.repoName.contains(normalizedQuery, ignoreCase = true) ||
                        extension.sourceNames.any { it.contains(normalizedQuery, ignoreCase = true) }
                }
            }

        // Catalog-backed items (installed, untrusted-in-catalog, or available)
        val catalogItems = filtered.map { extension ->
            val installedExtension = installed.find { it.pkgName == extension.pkgName }
            ExtensionItem(
                available = extension,
                installed = installedExtension,
                isUntrusted = extension.pkgName in untrustedPkgNames && installedExtension == null,
                downloadState = downloads[extension.pkgName],
            )
        }

        // Orphaned untrusted items: installed but NOT in any repo catalog
        val catalogPkgNames = available.map { it.pkgName }.toSet()
        val orphanedUntrusted = untrustedList
            .filter { it.pkgName !in catalogPkgNames }
            .filter { normalizedQuery == null || it.appName.contains(normalizedQuery, ignoreCase = true) || it.pkgName.contains(normalizedQuery, ignoreCase = true) }
            .map { UntrustedExtensionItem(it) }

        ExtensionDownloaderState(
            items = catalogItems + orphanedUntrusted,
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

    fun deleteRepo(repo: ExternalExtensionRepo) {
        if (repo.isBuiltIn) {
            _messageEvent.call(R.string.repo_builtin_cannot_delete)
            return
        }
        launchJob(Dispatchers.IO) {
            repoRepository.delete(repo)
            refreshCatalog(refreshRepos = false)
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
    val items: List<ListModel> = emptyList(),
    val isLoading: Boolean = false,
    val query: String? = null,
)

data class ExtensionItem(
    val available: RepoAvailableExtension,
    val installed: MihonLoadResult.Success?,
    val isUntrusted: Boolean = false,
    val downloadState: ExtensionInstallDownloadState?,
) : ListModel {
    override fun areItemsTheSame(other: ListModel): Boolean {
        return other is ExtensionItem && available.pkgName == other.available.pkgName
    }
    val isInstalled: Boolean get() = installed != null
    val hasUpdate: Boolean get() = installed != null && available.versionCode > installed.versionCode
}

data class UntrustedExtensionItem(
    val untrusted: MihonLoadResult.Untrusted,
) : ListModel {
    override fun areItemsTheSame(other: ListModel): Boolean {
        return other is UntrustedExtensionItem && untrusted.pkgName == other.untrusted.pkgName
    }
}
