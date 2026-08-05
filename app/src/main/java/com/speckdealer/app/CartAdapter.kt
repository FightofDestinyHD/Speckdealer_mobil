package com.speckdealer.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

class CartAdapter(
	private val onRemoveClicked: (Int) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

	private val items = mutableListOf<CartEntry>()
	private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.GERMANY)

	fun submitList(newItems: List<CartEntry>) {
		items.clear()
		items.addAll(newItems)
		notifyDataSetChanged()
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
		val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cart_entry, parent, false)
		return CartViewHolder(view)
	}

	override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
		holder.bind(items[position], position)
	}

	override fun getItemCount(): Int = items.size

	inner class CartViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
		private val nameText: TextView = itemView.findViewById(R.id.cartEntryName)
		private val priceText: TextView = itemView.findViewById(R.id.cartEntryPrice)
		private val removeButton: Button = itemView.findViewById(R.id.cartEntryRemoveButton)

		fun bind(entry: CartEntry, position: Int) {
			nameText.text = entry.displayName
			priceText.text = currencyFormatter.format(entry.totalCents / 100.0)
			removeButton.setOnClickListener { onRemoveClicked(position) }
		}
	}
}
