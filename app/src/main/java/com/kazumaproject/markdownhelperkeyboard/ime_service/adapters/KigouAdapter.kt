package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R

class KigouAdapter :RecyclerView.Adapter<KigouAdapter.KigouViewHolder>(){

    inner class KigouViewHolder(itemView: View): RecyclerView.ViewHolder(itemView)

    private val diffCallback = object : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem.hashCode() == newItem.hashCode()
        }
    }

    private var onItemClickListener: ((String) -> Unit)? = null

    fun setOnItemClickListener(onItemClick: (String) -> Unit) {
        this.onItemClickListener = onItemClick
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    var kigouList: List<String>
    get() = differ.currentList
    set(value) = differ.submitList(value)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KigouViewHolder {
        return KigouViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.kaomoji_item,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return kigouList.size
    }

    override fun onBindViewHolder(holder: KigouViewHolder, position: Int) {
        val kigou = kigouList[position]
        holder.itemView.apply {
            val text = findViewById<MaterialTextView>(R.id.kaomoji_textview)
            text.text = kigou

            setOnClickListener {
                onItemClickListener?.let { click ->
                    click(kigou)
                }
            }
        }
    }
}