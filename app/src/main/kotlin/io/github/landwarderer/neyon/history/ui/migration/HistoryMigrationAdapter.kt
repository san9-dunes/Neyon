package io.github.landwarderer.neyon.history.ui.migration

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.landwarderer.neyon.databinding.ItemHistoryMigrationBinding
import org.koitharu.kotatsu.parsers.model.Manga

class HistoryMigrationAdapter(
    private val onChangeClick: (Manga) -> Unit
) : ListAdapter<MigrationPair, HistoryMigrationAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryMigrationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemHistoryMigrationBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.buttonChange.setOnClickListener {
                val item = getItem(bindingAdapterPosition)
                onChangeClick(item.oldManga)
            }
            binding.root.setOnClickListener {
                binding.buttonChange.performClick()
            }
        }

        fun bind(item: MigrationPair) {
            val context = binding.root.context
            binding.imageViewCover.setImageAsync(item.oldManga.coverUrl, item.oldManga)
            binding.textViewTitle.text = item.oldManga.title
            binding.textViewOldSource.text = "From: ${item.oldManga.source.name}"
            
            if (item.isFetching || item.newManga == null) {
                binding.progressLoading.visibility = if (item.isFetching) View.VISIBLE else View.GONE
                binding.textViewNewSource.text = if (item.isFetching) "Finding match..." else "To: Not found"
            } else {
                binding.progressLoading.visibility = View.GONE
                binding.textViewNewSource.text = "To: ${item.newManga.source.name} - ${item.newManga.title}"
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<MigrationPair>() {
        override fun areItemsTheSame(oldItem: MigrationPair, newItem: MigrationPair): Boolean {
            return oldItem.oldManga.id == newItem.oldManga.id
        }

        override fun areContentsTheSame(oldItem: MigrationPair, newItem: MigrationPair): Boolean {
            return oldItem == newItem
        }
    }
}
