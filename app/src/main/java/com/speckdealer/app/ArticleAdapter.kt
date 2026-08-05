package com.speckdealer.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.speckdealer.app.data.ArticleEntity
import java.text.NumberFormat
import java.util.Locale

class ArticleAdapter(
	private val onEditClicked: (ArticleEntity) -> Unit,
	private val onDeleteClicked: (ArticleEntity) -> Unit
) : RecyclerView.Adapter<ArticleAdapter.ArticleViewHolder>() {

	private val items = mutableListOf<ArticleEntity>()
	private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.GERMANY)

	fun submitList(newItems: List<ArticleEntity>) {
		items.clear()
		items.addAll(newItems)
		notifyDataSetChanged()
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder {
		val view = LayoutInflater.from(parent.context).inflate(R.layout.item_article, parent, false)
		return ArticleViewHolder(view)
	}

	override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) {
		holder.bind(items[position])
	}

	override fun getItemCount(): Int = items.size

	inner class ArticleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
		private val nameText: TextView = itemView.findViewById(R.id.articleNameText)
		private val metaText: TextView = itemView.findViewById(R.id.articleMetaText)
		private val editButton: Button = itemView.findViewById(R.id.editArticleButton)
		private val deleteButton: Button = itemView.findViewById(R.id.deleteArticleButton)

		fun bind(item: ArticleEntity) {
			nameText.text = item.name
			val isSnack = item.category == com.speckdealer.app.data.CategoryType.SNACKS.name
			metaText.text = when {
				isSnack && item.hasLargeOption && item.hasSmallOption ->
					"Groß: ${currencyFormatter.format(item.largePriceCents / 100.0)}  Klein: ${currencyFormatter.format(item.smallPriceCents / 100.0)}"
				isSnack && item.hasLargeOption ->
					"Groß: ${currencyFormatter.format(item.largePriceCents / 100.0)}"
				isSnack && item.hasSmallOption ->
					"Klein: ${currencyFormatter.format(item.smallPriceCents / 100.0)}"
				else ->
					currencyFormatter.format(item.priceCents / 100.0)
			}
			editButton.setOnClickListener { onEditClicked(item) }
			deleteButton.setOnClickListener { onDeleteClicked(item) }
		}
	}
}
