package com.speckdealer.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.speckdealer.app.data.DiscoveredSyncDevice

class DiscoveredDeviceAdapter(
	private val onConnect: (DiscoveredSyncDevice) -> Unit,
	private val onRemovePairing: (DiscoveredSyncDevice) -> Unit
) : RecyclerView.Adapter<DiscoveredDeviceAdapter.VH>() {

	private val devices = mutableListOf<DiscoveredSyncDevice>()

	inner class VH(view: View) : RecyclerView.ViewHolder(view) {
		val name: TextView = view.findViewById(R.id.discoveredDeviceName)
		val info: TextView = view.findViewById(R.id.discoveredDeviceInfo)
		val status: TextView = view.findViewById(R.id.discoveredDeviceStatus)
		val connect: Button = view.findViewById(R.id.discoveredDeviceConnectButton)
		val remove: Button = view.findViewById(R.id.discoveredDeviceRemoveButton)
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
		val view = LayoutInflater.from(parent.context).inflate(R.layout.item_discovered_device, parent, false)
		return VH(view)
	}

	override fun onBindViewHolder(holder: VH, position: Int) {
		val device = devices[position]
		holder.name.text = device.displayName
		holder.info.text = "${device.host}:${device.port}"
		holder.status.text = if (device.isPaired) "Gekoppelt" else "Unbekannt"
		holder.connect.setOnClickListener { onConnect(device) }
		holder.remove.visibility = if (device.isPaired) View.VISIBLE else View.GONE
		holder.remove.setOnClickListener { onRemovePairing(device) }
	}

	override fun getItemCount(): Int = devices.size

	fun submit(newDevices: List<DiscoveredSyncDevice>) {
		devices.clear()
		devices.addAll(newDevices)
		notifyDataSetChanged()
	}
}
