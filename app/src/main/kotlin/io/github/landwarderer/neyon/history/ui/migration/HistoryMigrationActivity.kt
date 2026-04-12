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
import io.github.landwarderer.neyon.core.util.ext.launchWhenStarted
import io.github.landwarderer.neyon.databinding.ActivityHistoryMigrationBinding

@AndroidEntryPoint
class HistoryMigrationActivity : BaseActivity<ActivityHistoryMigrationBinding>() {

    private val viewModel by viewModels<HistoryMigrationViewModel>()
    private lateinit var adapter: HistoryMigrationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityHistoryMigrationBinding.inflate(layoutInflater))

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = HistoryMigrationAdapter { oldManga ->
            router.openAlternatives(oldManga)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.fabMigrateAll.setOnClickListener {
            viewModel.migrateAll()
            Snackbar.make(binding.root, R.string.migration_completed, Snackbar.LENGTH_SHORT).show()
        }

        viewScope.launchWhenStarted {
            viewModel.items.collect { items ->
                adapter.submitList(items)
                updateEmptyState()
            }
        }

        viewScope.launchWhenStarted {
            viewModel.isScanning.collect { isScanning ->
                binding.progressBar.visibility = if (isScanning && viewModel.items.value.isEmpty()) View.VISIBLE else View.GONE
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
            binding.emptyView.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.fabMigrateAll.hide()
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            if (viewModel.items.value.isNotEmpty()) {
                binding.fabMigrateAll.show()
            } else {
                binding.fabMigrateAll.hide()
            }
        }
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        binding.toolbar.updatePadding(top = bars.top)
        binding.recyclerView.updatePadding(bottom = bars.bottom + 88)
        binding.fabMigrateAll.updatePadding(bottom = bars.bottom)
        return insets
    }
}
