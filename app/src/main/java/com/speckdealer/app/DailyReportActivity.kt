package com.speckdealer.app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.speckdealer.app.data.DailySalesStorage
import com.speckdealer.app.data.DepositMovementStorage

class DailyReportActivity : AppCompatActivity() {

	private lateinit var storage: DailySalesStorage
	private lateinit var depositStorage: DepositMovementStorage

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_daily_report)

		storage = DailySalesStorage(this)
		depositStorage = DepositMovementStorage(this)

		findViewById<Button>(R.id.reportCloseButton).setOnClickListener { finish() }
		findViewById<Button>(R.id.reportResetButton).setOnClickListener { confirmReset() }

		buildReport()
	}

	private fun buildReport() {
		val report = DailyReportBuilder.build(storage.loadAll(), depositStorage.loadAll())
		findViewById<TextView>(R.id.reportFinanceText).text = report.financeText
		findViewById<TextView>(R.id.reportGlassesText).text = report.glassesText
		findViewById<TextView>(R.id.reportLeergutText).text = report.leergutText
		findViewById<TextView>(R.id.reportSoftdrinksText).text = report.softdrinksText
		findViewById<TextView>(R.id.reportTellerText).text = report.tellerText
		findViewById<TextView>(R.id.reportEmployeeText).text = report.employeeText
	}

	private fun confirmReset() {
		AlertDialog.Builder(this)
			.setTitle("Tag abschließen")
			.setMessage("Alle Tagesumsätze werden zurückgesetzt. Fortfahren?")
			.setPositiveButton("Zurücksetzen") { _, _ ->
				storage.clearToday()
				depositStorage.clearToday()
				buildReport()
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}
}
