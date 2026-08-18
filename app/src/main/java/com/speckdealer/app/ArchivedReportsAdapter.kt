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
		holder.revenueText.text = "Gesamtumsatz: ${MoneyValueService.formatCents(item.totalRevenueCents)}"
		holder.beverageVatText.text = "Getränke MwSt. 19 %: ${MoneyValueService.formatCents(item.beverageVatCents)}"
		holder.foodVatText.text = "Speisen MwSt. 7 %: ${MoneyValueService.formatCents(item.foodVatCents)}"
		holder.depositReceivedText.text = "Pfand erhalten: ${MoneyValueService.formatCents(item.depositReceivedCents)}"
		holder.depositReturnedText.text = "Pfand zurückgegeben: ${MoneyValueService.formatCents(item.depositReturnedCents)}"
		holder.depositText.text = "Pfand-Saldo: ${MoneyValueService.formatCents(item.depositBalanceCents)}"
		holder.itemView.setOnClickListener { onClick(item) }
	}

	class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val dateText: TextView = view.findViewById(R.id.archiveItemDateText)
		val archivedAtText: TextView = view.findViewById(R.id.archiveItemArchivedAtText)
		val revenueText: TextView = view.findViewById(R.id.archiveItemRevenueText)
		val beverageVatText: TextView = view.findViewById(R.id.archiveItemBeverageVatText)
		val foodVatText: TextView = view.findViewById(R.id.archiveItemFoodVatText)
		val depositReceivedText: TextView = view.findViewById(R.id.archiveItemDepositReceivedText)
		val depositReturnedText: TextView = view.findViewById(R.id.archiveItemDepositReturnedText)
		val depositText: TextView = view.findViewById(R.id.archiveItemDepositText)
	}
}
