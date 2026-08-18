package com.speckdealer.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.speckdealer.app.data.ArchivedDailyReportStorage
import com.speckdealer.app.data.DataModeAwareStorageFactory

class ArchivedReportsActivity : AppCompatActivity() {

	private lateinit var archiveStorage: ArchivedDailyReportStorage
	private lateinit var dataMode: String

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_archived_reports)

		dataMode = AppDataMode.resolve(intent.getStringExtra(AppDataMode.EXTRA_DATA_MODE))
		archiveStorage = DataModeAwareStorageFactory.archivedDailyReportStorage(this, dataMode)
		findViewById<Button>(R.id.archiveCloseButton).setOnClickListener { finish() }

		buildArchiveList()
	}

	private fun buildArchiveList() {
		val reports = archiveStorage.loadAll()
		val recyclerView = findViewById<RecyclerView>(R.id.archiveRecyclerView)
		val emptyState = findViewById<TextView>(R.id.archiveEmptyStateText)

		recyclerView.layoutManager = LinearLayoutManager(this)
		recyclerView.adapter = ArchivedReportsAdapter(reports) { report ->
			val intent = Intent(this, ArchivedReportDetailActivity::class.java)
			intent.putExtra(ArchivedReportDetailActivity.EXTRA_ARCHIVE_ID, report.id)
			intent.putExtra(AppDataMode.EXTRA_DATA_MODE, dataMode)
			startActivity(intent)
		}

		emptyState.visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE
	}

	override fun onResume() {
		super.onResume()
		buildArchiveList()
	}
}
