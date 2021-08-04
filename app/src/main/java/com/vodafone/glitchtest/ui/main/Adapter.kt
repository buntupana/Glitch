package com.vodafone.glitchtest.ui.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vodafone.glitchtest.databinding.ItemTextBinding

class Adapter : ListAdapter<String, Adapter.Holder>(SettDiffCallback<String>()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {

        return Holder(ItemTextBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind()
    }

    inner class Holder(private val binding: ItemTextBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.textView2.text = getItem(bindingAdapterPosition)
        }
    }

}

class SettDiffCallback<ITEM> : DiffUtil.ItemCallback<ITEM>() {
    override fun areItemsTheSame(oldItem: ITEM, newItem: ITEM): Boolean {
        return oldItem.hashCode() == newItem.hashCode()
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: ITEM, newItem: ITEM): Boolean {
        return oldItem == newItem
    }
}