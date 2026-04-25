package io.github.landwarderer.neyon.settings.sources.extension

import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.viewModels
import androidx.core.content.getSystemService
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.ui.BaseActivity
import io.github.landwarderer.neyon.core.util.ext.getDisplayMessage
import io.github.landwarderer.neyon.core.util.ext.observe
import io.github.landwarderer.neyon.core.util.ext.observeEvent
import io.github.landwarderer.neyon.databinding.ActivityExtensionDownloaderBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class ExtensionDownloaderActivity : BaseActivity<ActivityExtensionDownloaderBinding>() {

    private val viewModel by viewModels<ExtensionDownloaderViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityExtensionDownloaderBinding.inflate(layoutInflater))

        setTitle(R.string.extensions_manager)
        setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)

        val adapter = ExtensionDownloaderAdapter(
            onInstallClick = { viewModel.installExtension(it.available) },
            onCancelClick = { viewModel.cancelDownload(it.available.pkgName) },
            onUninstallClick = { viewModel.uninstallExtension(it.available.pkgName) },
            onUninstallUntrustedClick = { viewModel.uninstallExtension(it.untrusted.pkgName) },
        )

        viewBinding.errorState.buttonRetry.setOnClickListener {
            viewModel.refresh()
        }
        viewBinding.errorState.buttonSecondary.isVisible = false
        viewBinding.recyclerView.adapter = adapter

        viewModel.state.observe(this) { state ->
            viewBinding.loadingState.root.isVisible = state.isLoading && state.items.isEmpty()
            viewBinding.errorState.root.isVisible = !state.isLoading && state.items.isEmpty()
            viewBinding.errorState.textViewError.setText(
                if (state.query.isNullOrEmpty()) R.string.no_extensions_found else R.string.nothing_found
            )
            adapter.items = state.items
        }

        viewModel.intentAction.observeEvent(this) { intent ->
            runCatching {
                startActivity(intent)
            }.onFailure { error ->
                Snackbar.make(viewBinding.recyclerView, error.getDisplayMessage(resources), Snackbar.LENGTH_LONG).show()
            }
        }
        viewModel.onError.observeEvent(this) { error ->
            Snackbar.make(viewBinding.recyclerView, error.getDisplayMessage(resources), Snackbar.LENGTH_LONG).show()
        }
        viewModel.messageEvent.observeEvent(this) { messageResId ->
            Snackbar.make(viewBinding.recyclerView, messageResId, Snackbar.LENGTH_LONG).show()
        }
        addMenuProvider(
            ExtensionDownloaderMenuProvider(
                activity = this,
                viewModel = viewModel,
                onAddRepoClick = ::showAddRepoDialog,
                onManageReposClick = ::showManageReposDialog,
            )
        )
    }

    override fun onApplyWindowInsets(v: android.view.View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updatePadding(bottom = systemBars.bottom)
        return insets
    }

    private fun showAddRepoDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.repo_url)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        val horizontalPadding = resources.getDimensionPixelOffset(R.dimen.margin_normal)
        input.setPadding(horizontalPadding, input.paddingTop, horizontalPadding, input.paddingBottom)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_repo)
            .setMessage(R.string.add_repo_summary)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.add) { _, _ ->
                viewModel.addRepo(input.text?.toString().orEmpty())
            }
            .show()
        input.requestFocus()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        input.post {
            getSystemService<InputMethodManager>()?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun showManageReposDialog() {
        val repoAdapter = RepoListAdapter(
            onDeleteClick = { repo ->
                viewModel.deleteRepo(repo)
            }
        )
        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ExtensionDownloaderActivity)
            adapter = repoAdapter
        }
        val horizontalPadding = resources.getDimensionPixelOffset(R.dimen.margin_normal)
        recyclerView.setPadding(0, horizontalPadding / 2, 0, 0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.repos)
            .setView(recyclerView)
            .setPositiveButton(android.R.string.ok, null)
            .show()

        viewModel.observeRepos()
            .onEach { repos -> repoAdapter.submitList(repos) }
            .launchIn(lifecycleScope)
    }
}
