package io.github.landwarderer.neyon.history.ui.migration

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.neyon.R
import io.github.landwarderer.neyon.core.nav.router
import io.github.landwarderer.neyon.core.ui.BaseActivity
import kotlinx.coroutines.launch
import io.github.landwarderer.neyon.databinding.ActivityHistoryMigrationBinding

@AndroidEntryPoint
class HistoryMigrationActivity : BaseActivity<ActivityHistoryMigrationBinding>() {

    private val viewModel by viewModels<HistoryMigrationViewModel>()
    private lateinit var adapter: HistoryMigrationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityHistoryMigrationBinding.inflate(layoutInflater))

        viewBinding.toolbar.setNavigationOnClickListener { finish() }

        adapter = HistoryMigrationAdapter { oldManga ->
            router.openAlternatives(oldManga)
        }

        viewBinding.recyclerView.layoutManager = LinearLayoutManager(this)
        viewBinding.recyclerView.adapter = adapter

        viewBinding.fabMigrateAll.setOnClickListener {
            viewModel.migrateAll()
            Snackbar.make(viewBinding.root, R.string.migration_completed, Snackbar.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            viewModel.items.collect { items ->
                adapter.submitList(items)
                updateEmptyState()
            }
        }

        lifecycleScope.launch {
            viewModel.isScanning.collect { isScanning ->
                viewBinding.progressBar.visibility = if (isScanning && viewModel.items.value.isEmpty()) View.VISIBLE else View.GONE
                updateEmptyState()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Automatically check constraints or rescan when returning from single migration
        viewModel.scanUnavailableHistory()
    }

    private fun updateEmptyState() {
        if (!viewModel.isScanning.value && viewModel.items.value.isEmpty()) {
            viewBinding.emptyView.visibility = View.VISIBLE
            viewBinding.recyclerView.visibility = View.GONE
            viewBinding.fabMigrateAll.hide()
        } else {
            viewBinding.emptyView.visibility = View.GONE
            viewBinding.recyclerView.visibility = View.VISIBLE
            if (viewModel.items.value.isNotEmpty()) {
                viewBinding.fabMigrateAll.show()
            } else {
                viewBinding.fabMigrateAll.hide()
            }
        }
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        viewBinding.toolbar.updatePadding(top = bars.top)
        viewBinding.recyclerView.updatePadding(bottom = bars.bottom + 88)
        viewBinding.fabMigrateAll.updatePadding(bottom = bars.bottom)
        return insets
    }
}
