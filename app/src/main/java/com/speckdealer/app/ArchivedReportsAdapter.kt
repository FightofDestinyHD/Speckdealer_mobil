package com.speckdealer.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.speckdealer.app.data.ArchivedDailyReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArchivedReportsAdapter(
	private val items: List<ArchivedDailyReport>,
	private val onClick: (ArchivedDailyReport) -> Unit
) : RecyclerView.Adapter<ArchivedReportsAdapter.ViewHolder>() {

	private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val view = LayoutInflater.from(parent.context).inflate(R.layout.item_archived_report, parent, false)
		return ViewHolder(view)
	}

	override fun getItemCount(): Int = items.size

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val item = items[position]
		holder.dateText.text = "Geschäftsdatum: ${item.businessDate}"
		holder.archivedAtText.text = "Archiviert am: ${dateTimeFormat.format(Date(item.archivedAt))}"
		holder.revenueText.text = "Brutto-Umsatz ohne Pfand: ${MoneyValueService.formatCents(item.totalRevenueCents)}"
		holder.depositText.text = "Pfand-Saldo: ${MoneyValueService.formatCents(item.depositBalanceCents)}"
		holder.itemView.setOnClickListener { onClick(item) }
	}

	class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val dateText: TextView = view.findViewById(R.id.archiveItemDateText)
		val archivedAtText: TextView = view.findViewById(R.id.archiveItemArchivedAtText)
		val revenueText: TextView = view.findViewById(R.id.archiveItemRevenueText)
		val depositText: TextView = view.findViewById(R.id.archiveItemDepositText)
	}
}
