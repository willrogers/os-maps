package rs.wllrg.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import rs.wllrg.R

class SearchAdapter(
    private val onResultClick: (SearchResult) -> Unit
) : RecyclerView.Adapter<SearchAdapter.ViewHolder>() {

    private val items = mutableListOf<SearchResult>()

    fun submitList(results: List<SearchResult>) {
        items.clear()
        items.addAll(results)
        notifyDataSetChanged()
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.text_name)
        private val typeText: TextView = itemView.findViewById(R.id.text_type)

        fun bind(result: SearchResult) {
            nameText.text = result.name
            typeText.text = result.type
            itemView.setOnClickListener { onResultClick(result) }
        }
    }
}
