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

class ArchivedReportsActivity : AppCompatActivity() {

	private lateinit var archiveStorage: ArchivedDailyReportStorage

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_archived_reports)

		archiveStorage = ArchivedDailyReportStorage(this)
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
			startActivity(intent)
		}

		emptyState.visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE
	}

	override fun onResume() {
		super.onResume()
		buildArchiveList()
	}
}
