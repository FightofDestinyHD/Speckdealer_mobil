package com.speckdealer.app

import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.speckdealer.app.data.DailySalesStorage
import com.speckdealer.app.data.SaleRecord
import com.speckdealer.app.data.AppGraph
import com.speckdealer.app.data.ArticleEntity
import com.speckdealer.app.data.ArticleRepository
import com.speckdealer.app.data.CategoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

data class CartEntry(
    val displayName: String,
    val totalCents: Int,
    val articleName: String = "",
    val category: String = "",
    val servingType: String = "STANDARD",
    val priceCents: Int = totalCents,
    val depositCents: Int = 0,
    val isEmployee: Boolean = false
)

class SalesActivity : AppCompatActivity() {

	private lateinit var repository: ArticleRepository
	private lateinit var dailySalesStorage: DailySalesStorage
	private lateinit var tabLayout: TabLayout
	private lateinit var cartTotalText: TextView
	private lateinit var adapter: SalesArticleAdapter
	private lateinit var cartAdapter: CartAdapter
	private var observeJob: Job? = null
	private var selectedCategory: CategoryType = CategoryType.WEIN
	private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.GERMANY)
	private val cartItems = mutableListOf<CartEntry>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_sales)

		try {
			repository = AppGraph.repository(this)
			dailySalesStorage = DailySalesStorage(this)
			tabLayout = findViewById(R.id.salesTabLayout)
			cartTotalText = findViewById(R.id.cartTotalText)
			setupArticleRecyclerView()
			setupCartRecyclerView()
			setupCartButtons()
			setupTabs()
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	override fun onStart() {
		super.onStart()
		lifecycleScope.launch {
			selectCategory(CategoryType.WEIN)
		}
	}

	private fun setupArticleRecyclerView() {
		adapter = SalesArticleAdapter { article -> handleArticleSelection(article) }
		findViewById<RecyclerView>(R.id.salesArticleRecyclerView).apply {
			layoutManager = GridLayoutManager(this@SalesActivity, 3)
			adapter = this@SalesActivity.adapter
		}
	}

	private fun setupCartRecyclerView() {
		cartAdapter = CartAdapter { position -> removeFromCart(position) }
		findViewById<RecyclerView>(R.id.cartRecyclerView).apply {
			layoutManager = LinearLayoutManager(this@SalesActivity)
			adapter = cartAdapter
		}
	}

	private fun setupCartButtons() {
		findViewById<Button>(R.id.cartClearButton).setOnClickListener {
			cartItems.clear()
			updateCart()
		}
		findViewById<Button>(R.id.cartCheckoutButton).setOnClickListener {
			if (cartItems.isEmpty()) return@setOnClickListener
			showPriceAdjustmentDialog()
		}
	}

	/** Schritt 1: Gesamtpreis anzeigen und ggf. anpassen */
	private fun showPriceAdjustmentDialog() {
		val originalTotal = cartItems.sumOf { it.totalCents }
		val input = EditText(this).apply {
			setText(String.format(Locale.GERMANY, "%.2f", originalTotal / 100.0))
			inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
			selectAll()
		}
		val container = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			val pad = (16 * resources.displayMetrics.density).toInt()
			setPadding(pad, pad, pad, 0)
			addView(input)
		}
		AlertDialog.Builder(this)
			.setTitle("Gesamtbetrag anpassen")
			.setMessage("Originalpreis: ${currencyFormatter.format(originalTotal / 100.0)}\nHier kannst du den Betrag noch anpassen (z. B. Rabatt):")
			.setView(container)
			.setPositiveButton("Weiter") { _, _ ->
				val adjusted = input.text.toString().trim().replace(',', '.')
				val adjustedCents = ((adjusted.toDoubleOrNull() ?: 0.0) * 100).toInt()
				if (adjustedCents < 0) return@setPositiveButton
				showPaymentDialog(adjustedCents)
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	/** Schritt 2: Erhaltenen Betrag eingeben und Rückgeld berechnen */
	private fun showPaymentDialog(finalTotalCents: Int) {
		val input = EditText(this).apply {
			hint = "Erhaltener Betrag (z. B. 20.00)"
			inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
		}
		val container = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			val pad = (16 * resources.displayMetrics.density).toInt()
			setPadding(pad, pad, pad, 0)
			addView(input)
		}
		AlertDialog.Builder(this)
			.setTitle("Kassieren – ${currencyFormatter.format(finalTotalCents / 100.0)}")
			.setMessage("Wie viel hat der Kunde gegeben?")
			.setView(container)
			.setPositiveButton("Kassieren") { _, _ ->
				val givenStr = input.text.toString().trim().replace(',', '.')
				val givenCents = ((givenStr.toDoubleOrNull() ?: 0.0) * 100).toInt()
				val changeCents = givenCents - finalTotalCents
				// Persistieren mit finalem Preis (anteilig skalieren wenn angepasst)
				val originalTotal = cartItems.sumOf { it.totalCents }.coerceAtLeast(1)
				val records = cartItems.map { entry ->
					val adjustedPrice = (entry.priceCents.toLong() * finalTotalCents / originalTotal).toInt()
					SaleRecord(
						articleName  = entry.articleName.ifBlank { entry.displayName },
						category     = entry.category,
						servingType  = entry.servingType,
						priceCents   = adjustedPrice,
						depositCents = entry.depositCents,
						isEmployee   = entry.isEmployee
					)
				}
				lifecycleScope.launch(Dispatchers.IO) { dailySalesStorage.appendRecords(records) }
				cartItems.clear()
				updateCart()
				// Rückgeld anzeigen (landet NICHT im Tagesabschluss)
				showChangeDialog(changeCents)
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	/** Schritt 3: Rückgeld anzeigen */
	private fun showChangeDialog(changeCents: Int) {
		val changeText = if (changeCents >= 0) {
			"Rückgeld: ${currencyFormatter.format(changeCents / 100.0)}"
		} else {
			"⚠️ Betrag zu gering um ${currencyFormatter.format(-changeCents / 100.0)}"
		}
		AlertDialog.Builder(this)
			.setTitle("Kassiert ✓")
			.setMessage(changeText)
			.setPositiveButton("OK", null)
			.show()
	}

	private fun addToCart(entry: CartEntry) {
		cartItems.add(entry)
		updateCart()
	}

	private fun removeFromCart(position: Int) {
		if (position in cartItems.indices) {
			cartItems.removeAt(position)
			updateCart()
		}
	}

	private fun updateCart() {
		cartAdapter.submitList(cartItems.toList())
		val total = cartItems.sumOf { it.totalCents }
		cartTotalText.text = "Gesamt: ${currencyFormatter.format(total / 100.0)}"
	}

	private fun setupTabs() {
		try {
			val salesCategories = CategoryType.defaultOrder().filter { it != CategoryType.PFAND }
			salesCategories.forEach { category ->
				tabLayout.addTab(tabLayout.newTab().setText(category.displayName).setTag(category))
			}

			tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
				override fun onTabSelected(tab: TabLayout.Tab) {
					val category = tab.tag as? CategoryType ?: return
					selectCategory(category)
				}
				override fun onTabUnselected(tab: TabLayout.Tab) = Unit
				override fun onTabReselected(tab: TabLayout.Tab) = Unit
			})

			if (tabLayout.tabCount > 0) {
				tabLayout.getTabAt(0)?.select()
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	private fun selectCategory(categoryType: CategoryType) {
		selectedCategory = categoryType
		observeJob?.cancel()
		observeJob = lifecycleScope.launch {
			try {
				repository.observeArticlesByCategory(categoryType).collectLatest { articles ->
					adapter.submitList(articles)
				}
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}

	private fun handleArticleSelection(article: ArticleEntity) {
		if (selectedCategory == CategoryType.WEIN && article.isWein) {
			showWineServingDialog(article)
			return
		}
		if (selectedCategory == CategoryType.SOFTGETRAENKE && (article.depositApplicable || article.glassDepositOptional)) {
			showSoftdrinkDepositDialog(article)
			return
		}
		if (selectedCategory == CategoryType.SPECK || selectedCategory == CategoryType.KAESE) {
			showWeightPriceDialog(article)
			return
		}
		val depositTypeToken = detectNonWineDepositType(article)
		val applyDeposit = article.depositApplicable && depositTypeToken != null
		finalizeSelection(article, article.name, applyDeposit, depositTypeToken, article.priceCents, isEmployee = false)
	}

	private fun showWeightPriceDialog(article: ArticleEntity) {
		val input = EditText(this).apply {
			hint = "Preis eingeben (z. B. 4.80)"
			inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
		}
		val container = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			val pad = (16 * resources.displayMetrics.density).toInt()
			setPadding(pad, pad, pad, 0)
			addView(input)
		}
		AlertDialog.Builder(this)
			.setTitle("${article.name} – Preis eingeben")
			.setView(container)
			.setPositiveButton("Hinzufügen") { _, _ ->
				val priceStr = input.text.toString().trim().replace(',', '.')
				val priceCents = ((priceStr.toDoubleOrNull() ?: 0.0) * 100).toInt()
				if (priceCents <= 0) {
					AlertDialog.Builder(this)
						.setMessage("Bitte einen gültigen Preis eingeben.")
						.setPositiveButton("OK", null).show()
					return@setPositiveButton
				}
				finalizeSelection(article, article.name, false, null, priceCents, isEmployee = false)
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun showSoftdrinkDepositDialog(article: ArticleEntity) {
		AlertDialog.Builder(this)
			.setTitle(article.name)
			.setItems(arrayOf("Mit Pfand", "Ohne Pfand", "Mitarbeiter")) { _, which ->
				when (which) {
					2 -> finalizeSelection(
						article = article,
						displayName = "${article.name} (Mitarbeiter)",
						applyDeposit = false,
						depositTypeToken = null,
						customPriceCents = 0,
						isEmployee = true
					)
					else -> finalizeSelection(
						article = article,
						displayName = article.name,
						applyDeposit = which == 0,
						depositTypeToken = "glas",
						customPriceCents = article.priceCents,
						isEmployee = false
					)
				}
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun showWineServingDialog(article: ArticleEntity) {
		val options = mutableListOf<WineServingType>()
		if (article.hasBottleOption) options.add(WineServingType.BOTTLE)
		if (article.hasGlass01Option) options.add(WineServingType.GLASS_01)
		if (article.hasGlass02Option) options.add(WineServingType.GLASS_02)
		if (options.isEmpty()) options.add(WineServingType.BOTTLE)

		AlertDialog.Builder(this)
			.setTitle(article.name)
			.setItems(options.map { it.label }.toTypedArray()) { _, which ->
				val selected = options[which]
				val priceCents = when (selected) {
					WineServingType.GLASS_01 -> if (article.glass01PriceCents > 0) article.glass01PriceCents else article.priceCents
					WineServingType.GLASS_02 -> if (article.glass02PriceCents > 0) article.glass02PriceCents else article.priceCents
					WineServingType.BOTTLE  -> article.priceCents
				}
				if (selected.requiresGlassDepositChoice) {
					showGlassDepositChoiceDialog(article, selected, priceCents)
				} else {
					finalizeSelection(article, "${article.name} - ${selected.label}", false, null, priceCents,
						isEmployee = false, servingTypeStr = selected.name)
				}
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun showGlassDepositChoiceDialog(article: ArticleEntity, servingType: WineServingType, priceCents: Int) {
		AlertDialog.Builder(this)
			.setTitle("Pfand für ${servingType.label}")
			.setItems(arrayOf("Mit Pfand", "Ohne Pfand", "Mitarbeiter")) { _, which ->
				when (which) {
					2 -> finalizeSelection(
						article = article,
						displayName = "${article.name} - ${servingType.label} (Mitarbeiter)",
						applyDeposit = false,
						depositTypeToken = null,
						customPriceCents = 0,
						isEmployee = true,
						servingTypeStr = servingType.name
					)
					else -> {
						val withDeposit = which == 0
						finalizeSelection(
							article = article,
							displayName = "${article.name} - ${servingType.label}",
							applyDeposit = withDeposit,
							depositTypeToken = "glas",
							customPriceCents = priceCents,
							isEmployee = false,
							servingTypeStr = servingType.name
						)
					}
				}
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun detectNonWineDepositType(article: ArticleEntity): String? {
		val normalized = article.name.lowercase(Locale.GERMANY)
		return when {
			normalized.contains("glas")    -> "glas"
			normalized.contains("teller")  -> "teller"
			normalized.contains("flasche") && selectedCategory != CategoryType.WEIN -> "flasche"
			else -> null
		}
	}

	private fun finalizeSelection(
		article: ArticleEntity,
		displayName: String,
		applyDeposit: Boolean,
		depositTypeToken: String?,
		customPriceCents: Int = article.priceCents,
		isEmployee: Boolean = false,
		servingTypeStr: String = "STANDARD"
	) {
		lifecycleScope.launch(Dispatchers.IO) {
			try {
				val depositArticle = if (!isEmployee && applyDeposit && !depositTypeToken.isNullOrBlank()) {
					repository.getDepositArticleForType(depositTypeToken)
				} else null

				val depositCents = depositArticle?.priceCents ?: 0
				val drinkPrice   = if (isEmployee) 0 else customPriceCents
				val totalCents   = drinkPrice + depositCents

				val entryName = buildString {
					append(displayName)
					if (depositArticle != null) append(" + ${depositArticle.name}")
				}

				withContext(Dispatchers.Main) {
					addToCart(CartEntry(
						displayName  = entryName,
						totalCents   = totalCents,
						articleName  = article.name,
						category     = article.category,
						servingType  = servingTypeStr,
						priceCents   = drinkPrice,
						depositCents = depositCents,
						isEmployee   = isEmployee
					))
				}
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}

	private enum class WineServingType(val label: String, val requiresGlassDepositChoice: Boolean) {
		BOTTLE("Flasche", false),
		GLASS_01("Glas 0,1l", true),
		GLASS_02("Glas 0,2l", true)
	}
}
