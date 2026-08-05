package com.speckdealer.app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
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

data class CartEntry(val displayName: String, val totalCents: Int)

class SalesActivity : AppCompatActivity() {

	private lateinit var repository: ArticleRepository
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
			val total = currencyFormatter.format(cartItems.sumOf { it.totalCents } / 100.0)
			AlertDialog.Builder(this)
				.setTitle("Kassieren")
				.setMessage("Gesamtbetrag: $total\n\nKasse abschließen und Warenkorb leeren?")
				.setPositiveButton("Kassieren") { _, _ ->
					cartItems.clear()
					updateCart()
				}
				.setNegativeButton("Abbrechen", null)
				.show()
		}
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
		val depositTypeToken = detectNonWineDepositType(article)
		val applyDeposit = article.depositApplicable && depositTypeToken != null
		finalizeSelection(article, article.name, applyDeposit, depositTypeToken, article.priceCents)
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
					finalizeSelection(article, "${article.name} - ${selected.label}", false, null, priceCents)
				}
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun showGlassDepositChoiceDialog(article: ArticleEntity, servingType: WineServingType, priceCents: Int) {
		AlertDialog.Builder(this)
			.setTitle("Pfand für ${servingType.label}")
			.setItems(arrayOf("Mit Pfand", "Ohne Pfand")) { _, which ->
				val withDeposit = which == 0
				finalizeSelection(
					article = article,
					displayName = "${article.name} - ${servingType.label}",
					applyDeposit = withDeposit,
					depositTypeToken = "glas",
					customPriceCents = priceCents
				)
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
		customPriceCents: Int = article.priceCents
	) {
		lifecycleScope.launch(Dispatchers.IO) {
			try {
				val depositArticle = if (applyDeposit && !depositTypeToken.isNullOrBlank()) {
					repository.getDepositArticleForType(depositTypeToken)
				} else null

				val depositCents = depositArticle?.priceCents ?: 0
				val totalCents = customPriceCents + depositCents

				val entryName = if (depositArticle != null) {
					"$displayName + ${depositArticle.name}"
				} else {
					displayName
				}

				withContext(Dispatchers.Main) {
					addToCart(CartEntry(entryName, totalCents))
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
