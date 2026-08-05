package com.speckdealer.app

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.speckdealer.app.data.ArticleEntity
import java.io.File
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
		private val glass01PriceText: TextView = itemView.findViewById(R.id.salesArticleGlass01Price)
		private val glass02PriceText: TextView = itemView.findViewById(R.id.salesArticleGlass02Price)
		private val metaText: TextView = itemView.findViewById(R.id.salesArticleMeta)

		fun bind(item: ArticleEntity) {
			nameText.text = item.name

			// Hauptpreis: Flasche oder Standardpreis
			val bottleLabel = if (item.isWein && item.hasBottleOption) "Flasche: " else ""
			priceText.text = "$bottleLabel${currencyFormatter.format(item.priceCents / 100.0)}"

			// Glas 0,1l — nur anzeigen wenn Wein + Option aktiv + Preis gesetzt
			if (item.isWein && item.hasGlass01Option && item.glass01PriceCents > 0) {
				glass01PriceText.text = "Glas 0,1l: ${currencyFormatter.format(item.glass01PriceCents / 100.0)}"
				glass01PriceText.visibility = View.VISIBLE
			} else {
				glass01PriceText.visibility = View.GONE
			}

			// Glas 0,2l — nur anzeigen wenn Wein + Option aktiv + Preis gesetzt
			if (item.isWein && item.hasGlass02Option && item.glass02PriceCents > 0) {
				glass02PriceText.text = "Glas 0,2l: ${currencyFormatter.format(item.glass02PriceCents / 100.0)}"
				glass02PriceText.visibility = View.VISIBLE
			} else {
				glass02PriceText.visibility = View.GONE
			}

			metaText.text = buildMetaText(item)

			if (!item.imageUri.isNullOrBlank()) {
				try {
					if (item.imageUri.startsWith("/")) {
						// Interner Dateipfad → direkt per BitmapFactory laden (kein URI-Berechtigungsproblem)
						val file = File(item.imageUri)
						if (file.exists()) {
							val bitmap = BitmapFactory.decodeFile(file.absolutePath)
							if (bitmap != null) {
								imageView.setImageBitmap(bitmap)
							} else {
								imageView.setImageResource(android.R.drawable.ic_menu_gallery)
							}
						} else {
							Log.w("SalesArticleAdapter", "Bilddatei nicht gefunden: ${item.imageUri}")
							imageView.setImageResource(android.R.drawable.ic_menu_gallery)
						}
					} else {
						// Alte content://-URI → per setImageURI, SecurityException abfangen
						imageView.setImageURI(Uri.parse(item.imageUri))
					}
				} catch (e: Exception) {
					Log.w("SalesArticleAdapter", "Bild konnte nicht geladen werden: ${item.imageUri}", e)
					imageView.setImageResource(android.R.drawable.ic_menu_gallery)
				}
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
