package net.zeevox.nearow.ui.recyclerview

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.zeevox.nearow.databinding.FragmentSessionsBinding
import net.zeevox.nearow.db.model.Session

/**
 * [ListAdapter] that can display a [Session].
 */
class SessionsListAdapter(val clickListener: (Session) -> Unit) :
    ListAdapter<Session, SessionsListAdapter.ViewHolder>(SessionDiff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(FragmentSessionsBinding.inflate(LayoutInflater.from(parent.context),
            parent, false)
        )


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item: Session = getItem(position)
        holder.contentView.text = item.toString()
        holder.shareButton.setOnClickListener { clickListener(item) }
    }

    inner class ViewHolder(binding: FragmentSessionsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val contentView: TextView = binding.content
        val shareButton: ImageButton = binding.shareButton

        override fun toString(): String {
            return super.toString() + " '" + contentView.text + "'"
        }
    }

    internal class SessionDiff : DiffUtil.ItemCallback<Session>() {
        override fun areItemsTheSame(oldItem: Session, newItem: Session): Boolean =
            oldItem === newItem

        override fun areContentsTheSame(oldItem: Session, newItem: Session): Boolean =
            oldItem.trackId == newItem.trackId
    }

}