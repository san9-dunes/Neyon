package io.github.landwarderer.neyon.settings.sources.extension

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.landwarderer.neyon.databinding.ItemRepoBinding
import io.github.landwarderer.neyon.mihon.extensions.repo.ExternalExtensionRepo

class RepoListAdapter(
    private val onDeleteClick: (ExternalExtensionRepo) -> Unit,
) : ListAdapter<ExternalExtensionRepo, RepoListAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRepoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRepoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.buttonDelete.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) onDeleteClick(getItem(pos))
            }
        }

        fun bind(repo: ExternalExtensionRepo) {
            binding.textViewRepoName.text = repo.displayName
            binding.textViewRepoUrl.text = repo.baseUrl

            val error = repo.lastError
            binding.textViewRepoError.isVisible = !error.isNullOrEmpty()
            binding.textViewRepoError.text = error

            binding.buttonDelete.isVisible = !repo.isBuiltIn
            binding.iconLock.isVisible = repo.isBuiltIn
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ExternalExtensionRepo>() {
            override fun areItemsTheSame(old: ExternalExtensionRepo, new: ExternalExtensionRepo) =
                old.baseUrl == new.baseUrl && old.type == new.type

            override fun areContentsTheSame(old: ExternalExtensionRepo, new: ExternalExtensionRepo) =
                old == new
        }
    }
}
