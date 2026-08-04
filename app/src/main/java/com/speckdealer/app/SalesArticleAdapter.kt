package com.speckdealer.app

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.speckdealer.app.data.ArticleEntity
import java.text.NumberFormat
import java.util.Locale

class SalesArticleAdapter(
	private val onArticleClicked: (ArticleEntity) -> Unit
) : RecyclerView.Adapter<SalesArticleAdapter.SalesArticleViewHolder>() {

	private val items = mutableListOf<ArticleEntity>()
	private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.GERMANY)

	fun submitList(newItems: List<ArticleEntity>) {
		items.clear()
		items.addAll(newItems)
		notifyDataSetChanged()
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SalesArticleViewHolder {
		val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sales_article, parent, false)
		return SalesArticleViewHolder(view)
	}

	override fun onBindViewHolder(holder: SalesArticleViewHolder, position: Int) {
		holder.bind(items[position])
	}

	override fun getItemCount(): Int = items.size

	inner class SalesArticleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
		private val imageView: ImageView = itemView.findViewById(R.id.salesArticleImage)
		private val nameText: TextView = itemView.findViewById(R.id.salesArticleName)
		private val priceText: TextView = itemView.findViewById(R.id.salesArticlePrice)
		private val metaText: TextView = itemView.findViewById(R.id.salesArticleMeta)

		fun bind(item: ArticleEntity) {
			nameText.text = item.name
			priceText.text = currencyFormatter.format(item.priceCents / 100.0)
			metaText.text = buildMetaText(item)

			if (!item.imageUri.isNullOrBlank()) {
				imageView.setImageURI(Uri.parse(item.imageUri))
			} else {
				imageView.setImageResource(android.R.drawable.ic_menu_gallery)
			}

			itemView.setOnClickListener { onArticleClicked(item) }
		}

		private fun buildMetaText(item: ArticleEntity): String {
			return when {
				item.isWein -> "Wein"
				item.depositApplicable -> "Pfandfähig"
				else -> "Ohne Pfand"
			}
		}
	}
}
