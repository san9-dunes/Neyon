package io.github.landwarderer.neyon.settings.sources.extension

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.ui.BaseListAdapter
import io.github.landwarderer.neyon.databinding.ItemExtensionBinding
import io.github.landwarderer.neyon.databinding.ItemExtensionUntrustedBinding
import io.github.landwarderer.neyon.list.ui.adapter.ListItemType
import io.github.landwarderer.neyon.list.ui.model.ListModel

class ExtensionDownloaderAdapter(
    onInstallClick: (ExtensionItem) -> Unit,
    onCancelClick: (ExtensionItem) -> Unit,
    onUninstallClick: (ExtensionItem) -> Unit,
    onUninstallUntrustedClick: (UntrustedExtensionItem) -> Unit,
) : BaseListAdapter<ListModel>() {

    init {
        addDelegate(ListItemType.EXTENSION, extensionItemAD(onInstallClick, onCancelClick, onUninstallClick))
        addDelegate(ListItemType.UNTRUSTED_EXTENSION, untrustedExtensionItemAD(onUninstallUntrustedClick))
    }
}

private fun extensionItemAD(
    onInstallClick: (ExtensionItem) -> Unit,
    onCancelClick: (ExtensionItem) -> Unit,
    onUninstallClick: (ExtensionItem) -> Unit,
) = adapterDelegateViewBinding<ExtensionItem, ListModel, ItemExtensionBinding>(
    { layoutInflater, parent -> ItemExtensionBinding.inflate(layoutInflater, parent, false) }
) {
    binding.buttonAction.setOnClickListener {
        if (item.downloadState != null) {
            onCancelClick(item)
        } else {
            onInstallClick(item)
        }
    }

    binding.buttonUninstall.setOnClickListener {
        onUninstallClick(item)
    }

    binding.root.setOnLongClickListener {
        if (item.isInstalled) {
            onUninstallClick(item)
            true
        } else {
            false
        }
    }

    bind {
        binding.textViewTitle.text = item.available.name
        binding.textViewVersion.text = item.available.versionName

        val infoText = buildString {
            append(item.available.lang.uppercase())
            append(" · ")
            append(item.available.repoName)
        }
        binding.textViewInfo.text = infoText
        binding.textViewInfo.isVisible = true

        binding.chipNsfw.isVisible = item.available.isNsfw
        binding.imageViewIcon.setImageAsync(item.available.iconUrl)

        val downloadState = item.downloadState
        if (downloadState != null) {
            binding.buttonAction.text = context.getString(android.R.string.cancel)
            binding.buttonAction.isVisible = true
            binding.buttonAction.isEnabled = true
            binding.buttonUninstall.isVisible = false
            binding.progressBar.isVisible = true
            val progress = downloadState.progressPercent
            if (progress != null) {
                binding.progressBar.isIndeterminate = false
                binding.progressBar.progress = progress
            } else {
                binding.progressBar.isIndeterminate = true
            }
        } else {
            binding.progressBar.isVisible = false

            when {
                item.isUntrusted -> {
                    // Extension installed but signing key doesn't match any repo
                    binding.buttonAction.isVisible = false
                    binding.buttonUninstall.isVisible = true
                    binding.textViewVersion.text = context.getString(
                        R.string.source_summary_pattern,
                        item.available.versionName,
                        context.getString(R.string.extension_untrusted),
                    )
                }
                item.hasUpdate -> {
                    binding.buttonAction.text = context.getString(R.string.extension_update)
                    binding.buttonAction.isVisible = true
                    binding.buttonAction.isEnabled = true
                    binding.buttonUninstall.isVisible = true
                }
                item.isInstalled -> {
                    binding.buttonAction.isVisible = false
                    binding.buttonUninstall.isVisible = true
                }
                else -> {
                    binding.buttonAction.text = context.getString(R.string.install)
                    binding.buttonAction.isVisible = true
                    binding.buttonAction.isEnabled = true
                    binding.buttonUninstall.isVisible = false
                }
            }
        }
    }
}

private fun untrustedExtensionItemAD(
    onUninstallClick: (UntrustedExtensionItem) -> Unit,
) = adapterDelegateViewBinding<UntrustedExtensionItem, ListModel, ItemExtensionUntrustedBinding>(
    { layoutInflater, parent -> ItemExtensionUntrustedBinding.inflate(layoutInflater, parent, false) }
) {
    binding.buttonUninstall.setOnClickListener { onUninstallClick(item) }

    bind {
        val u = item.untrusted
        binding.textViewTitle.text = u.appName.ifBlank { u.pkgName }
        binding.textViewVersion.text = context.getString(
            R.string.source_summary_pattern,
            u.versionName,
            context.getString(R.string.extension_untrusted),
        )
    }
}
