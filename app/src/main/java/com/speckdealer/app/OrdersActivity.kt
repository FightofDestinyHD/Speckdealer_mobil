package com.speckdealer.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import com.speckdealer.app.data.DataModeAwareStorageFactory
import com.speckdealer.app.data.LocalOrderSyncManager
import com.speckdealer.app.data.LocalOrderSyncRegistry
import com.speckdealer.app.data.OrderRecord
import com.speckdealer.app.data.OrderStatus
import com.speckdealer.app.data.OrderStorage
import java.text.NumberFormat
import java.util.Locale

class OrdersActivity : AppCompatActivity() {

	private lateinit var storage: OrderStorage
	private lateinit var adapter: OrderAdapter
	private lateinit var emptyText: TextView
	private lateinit var syncStatusText: TextView
	private var syncManager: LocalOrderSyncManager? = null
	private lateinit var dataMode: String

	override fun onCreate(savedInstanceState: Bundle?) {
	super.onCreate(savedInstanceState)
	setContentView(R.layout.activity_orders)

	dataMode = AppDataMode.resolve(intent.getStringExtra(AppDataMode.EXTRA_DATA_MODE))
	storage = DataModeAwareStorageFactory.orderStorage(this, dataMode)
	emptyText = findViewById(R.id.ordersEmptyText)
	syncStatusText = findViewById(R.id.ordersSyncStatusText)
	syncManager = runCatching { LocalOrderSyncRegistry.get(this, dataMode) }.getOrNull()

	adapter = OrderAdapter(
		orders = storage.loadAll().filter { it.status != OrderStatus.COMPLETED.name && it.status != OrderStatus.CANCELLED.name }.toMutableList(),
		onDone = { order ->
			val completed = order.withStatus(OrderStatus.COMPLETED)
			storage.addAll(listOf(completed))
			adapter.remove(order.id)
			updateEmptyState()
			lifecycleScope.launch {
				syncManager?.syncNow()
			}
		}
	)

		findViewById<RecyclerView>(R.id.ordersRecyclerView).apply {
			layoutManager = LinearLayoutManager(this@OrdersActivity)
			adapter = this@OrdersActivity.adapter
		}

		findViewById<Button>(R.id.ordersCloseButton).setOnClickListener { finish() }

		updateEmptyState()
	}

	override fun onResume() {
		super.onResume()
		adapter.replaceAll(storage.loadAll().filter { it.status != OrderStatus.COMPLETED.name && it.status != OrderStatus.CANCELLED.name })
		updateEmptyState()
		refreshSyncStatus()
	}

	override fun onDestroy() {
		super.onDestroy()
	}

	private fun updateEmptyState() {
		emptyText.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
	}

	private fun refreshSyncStatus() {
		val state = syncManager?.state()?.value
		syncStatusText.text = when (state?.status) {
			com.speckdealer.app.data.OrderSyncStatus.SYNCHRONIZED -> "Synchronisiert"
			com.speckdealer.app.data.OrderSyncStatus.SYNCING -> "Wird synchronisiert"
			com.speckdealer.app.data.OrderSyncStatus.OFFLINE -> "Offline"
			com.speckdealer.app.data.OrderSyncStatus.ERROR -> "Fehler"
			null -> "Offline"
		}
	}
}

// ── Adapter ──────────────────────────────────────────────────────────────────

class OrderAdapter(
	private val orders: MutableList<OrderRecord>,
	private val onDone: (OrderRecord) -> Unit
) : RecyclerView.Adapter<OrderAdapter.VH>() {

	private val fmt = NumberFormat.getCurrencyInstance(Locale.GERMANY)

	inner class VH(view: View) : RecyclerView.ViewHolder(view) {
		val title: TextView = view.findViewById(R.id.orderItemTitle)
		val details: TextView = view.findViewById(R.id.orderItemDetails)
		val doneButton: Button = view.findViewById(R.id.orderItemDoneButton)
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
		VH(LayoutInflater.from(parent.context).inflate(R.layout.item_order, parent, false))

	override fun onBindViewHolder(holder: VH, position: Int) {
		val order = orders[position]
		val sizeLabel = if (order.sizeName.isNotBlank()) " (${order.sizeName})" else ""
		val empLabel = if (order.isEmployee) " – Mitarbeiter" else ""
		val total = order.priceCents + order.depositCents
		holder.title.text = "${order.articleName}$sizeLabel$empLabel  ${fmt.format(total / 100.0)}"

		val detailsText = order.buildDetailsText()
		if (detailsText.isNotEmpty()) {
			holder.details.text = detailsText
			holder.details.visibility = View.VISIBLE
		} else {
			holder.details.visibility = View.GONE
		}

		holder.doneButton.setOnClickListener { onDone(order) }
	}

	override fun getItemCount() = orders.size

	fun remove(id: String) {
		val idx = orders.indexOfFirst { it.id == id }
		if (idx >= 0) { orders.removeAt(idx); notifyItemRemoved(idx) }
	}

	fun replaceAll(newOrders: List<OrderRecord>) {
		orders.clear()
		orders.addAll(newOrders)
		notifyDataSetChanged()
	}
}
