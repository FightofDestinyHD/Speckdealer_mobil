package com.speckdealer.app

import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
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
import com.google.android.material.snackbar.Snackbar
import com.speckdealer.app.data.DailySalesStorage
import com.speckdealer.app.data.OrderStorage
import com.speckdealer.app.data.CheckoutJournalStorage
import com.speckdealer.app.data.LocalOrderSyncRegistry
import com.speckdealer.app.data.AppGraph
import com.speckdealer.app.data.ArticleEntity
import com.speckdealer.app.data.ArticleRepository
import com.speckdealer.app.data.CategoryType
import com.speckdealer.app.data.DataModeAwareStorageFactory
import com.speckdealer.app.data.DepositMovement
import com.speckdealer.app.data.DepositMovementStorage
import com.speckdealer.app.data.DepositMovementType
import com.speckdealer.app.data.GlobalDepositSettings
import com.speckdealer.app.data.GlobalSettingsStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

data class CartEntry(
    val displayName: String,
    val totalCents: Int,
    val articleName: String = "",
    val category: String = "",
    val servingType: String = "STANDARD",
    val priceCents: Int = totalCents,
    val depositCents: Int = 0,
    val isEmployee: Boolean = false,
    val createBottleHelperRecord: Boolean = false,
    val explicitOfferTaxCategory: TaxCategory? = null,
    val orderDraft: OrderDraftPayload? = null
)

class SalesActivity : AppCompatActivity() {

	private lateinit var repository: ArticleRepository
	private lateinit var checkoutService: CheckoutService
	private lateinit var globalSettingsStorage: GlobalSettingsStorage
	private var globalDepositSettings: GlobalDepositSettings = GlobalDepositSettings(0, 0, 0)
	private lateinit var cartTotalText: TextView
	private lateinit var categoryButtons: Map<CategoryType, Button>
	private lateinit var adapter: SalesArticleAdapter
	private lateinit var cartAdapter: CartAdapter
	private lateinit var checkoutJournalStorage: CheckoutJournalStorage
	private lateinit var depositMovementStorage: DepositMovementStorage
	private var observeJob: Job? = null
	private var selectedCategory: CategoryType = CategoryType.WEIN
	private lateinit var dataMode: String
	private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.GERMANY)
	private val cartItems = mutableListOf<CartEntry>()
	private lateinit var cartClearButton: Button
	private lateinit var cartCheckoutButton: Button
	private var operationState: UiOperationState = UiOperationState.Idle
	private var currentCheckoutTransactionId: String? = null
	@Volatile
	private var checkoutInProgress = false

		override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_sales)

		updateOperationState(UiOperationState.Loading)
		runCatching {
			val requestedDataMode = intent.getStringExtra(AppDataMode.EXTRA_DATA_MODE)
			if (requestedDataMode == AppDataMode.MODE_DEV && !DevModeConfig.isSessionActive()) {
				Snackbar.make(findViewById(android.R.id.content), "Dev-Modus ist nicht aktiv.", Snackbar.LENGTH_LONG).show()
				finish()
				return@runCatching
			}
			dataMode = if (requestedDataMode == AppDataMode.MODE_DEV) {
				AppDataMode.MODE_DEV
			} else {
				AppDataMode.resolve(requestedDataMode)
			}
			repository = AppGraph.repository(this, dataMode)
			globalSettingsStorage = DataModeAwareStorageFactory.globalSettingsStorage(this, dataMode)
			globalDepositSettings = globalSettingsStorage.loadDepositSettings()
			val dailySalesStorage = DataModeAwareStorageFactory.dailySalesStorage(this, dataMode)
			val orderStorage = DataModeAwareStorageFactory.orderStorage(this, dataMode)
			checkoutJournalStorage = DataModeAwareStorageFactory.checkoutJournalStorage(this, dataMode)
			depositMovementStorage = DataModeAwareStorageFactory.depositMovementStorage(this, dataMode)
			val syncManager = LocalOrderSyncRegistry.get(this, dataMode)
			checkoutService = CheckoutService(
				dailySalesStorage = dailySalesStorage,
				orderStorage = orderStorage,
				journalStorage = checkoutJournalStorage,
				onOrdersPersisted = {
					if (it.isNotEmpty()) {
						syncManager.syncNow()
					}
				}
			)
			cartTotalText = findViewById(R.id.cartTotalText)
			setupCategoryButtons()
			setupArticleRecyclerView()
			setupCartRecyclerView()
			setupCartButtons()
		}.onSuccess {
			updateOperationState(UiOperationState.Idle)
		}.onFailure {
			updateOperationState(UiOperationState.Error("Verkaufsansicht konnte nicht geladen werden"))
		}
	}

	override fun onStart() {
		super.onStart()
		globalDepositSettings = globalSettingsStorage.loadDepositSettings()
		selectCategory(CategoryType.WEIN)
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
		cartClearButton = findViewById(R.id.cartClearButton)
		cartCheckoutButton = findViewById(R.id.cartCheckoutButton)
		cartClearButton.setOnClickListener {
			if (checkoutInProgress) return@setOnClickListener
			cartItems.clear()
			currentCheckoutTransactionId = null
			updateCart()
		}
		cartCheckoutButton.setOnClickListener {
			if (cartItems.isEmpty() || checkoutInProgress) return@setOnClickListener
			showPriceAdjustmentDialog()
		}
	}

	private enum class CheckoutFlowType {
		NORMAL_SALE,
		PURE_DEPOSIT_RETURN,
		MIXED
	}

	private data class CheckoutFlowContext(
		val flowType: CheckoutFlowType,
		val cartTotalCents: Long,
		val salesTotalCents: Long,
		val depositReturnTotalCents: Long,
		val payoutCents: Long,
		val netDueCents: Long
	)

	/** Schritt 1: Gesamtpreis anzeigen und ggf. anpassen */
	private fun showPriceAdjustmentDialog() {
		val context = buildCheckoutFlowContext()
		if (context.flowType == CheckoutFlowType.PURE_DEPOSIT_RETURN) {
			showPaymentDialog(context)
			return
		}

		val input = EditText(this).apply {
			setText(String.format(Locale.GERMANY, "%.2f", context.netDueCents / 100.0))
			inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
			selectAll()
		}
		val container = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			val pad = (16 * resources.displayMetrics.density).toInt()
			setPadding(pad, pad, pad, 0)
			addView(input)
		}
		val infoText = when (context.flowType) {
			CheckoutFlowType.MIXED -> "Verkauf: ${currencyFormatter.format(context.salesTotalCents / 100.0)}\nPfandrückgabe: ${currencyFormatter.format(context.depositReturnTotalCents / 100.0)}\nNetto fällig: ${currencyFormatter.format(context.netDueCents / 100.0)}"
			else -> "Originalpreis: ${currencyFormatter.format(context.netDueCents / 100.0)}\nHier kannst du den Betrag noch anpassen (z. B. Rabatt):"
		}
		AlertDialog.Builder(this)
			.setTitle("Gesamtbetrag anpassen")
			.setMessage(infoText)
			.setView(container)
			.setPositiveButton("Weiter") { _, _ ->
				val parsed = MoneyValueService.parseAmountToCents(input.text.toString(), allowZero = true)
				if (!parsed.isValid) {
					showAmountValidationError(parsed.error)
					return@setPositiveButton
				}
				val adjustedCents = parsed.cents ?: 0L
				showPaymentDialog(context.copy(netDueCents = adjustedCents, payoutCents = 0L))
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun buildCheckoutFlowContext(): CheckoutFlowContext {
		val cartTotal = cartItems.sumOf { it.totalCents.toLong() }
		val salesTotal = cartItems.filter { it.category != CategoryType.PFAND.storageValue }.sumOf { it.totalCents.toLong() }
		val depositReturnAbs = cartItems.filter { it.category == CategoryType.PFAND.storageValue && it.totalCents < 0 }.sumOf { -it.totalCents.toLong() }
		val onlyDepositReturns = cartItems.isNotEmpty() && cartItems.all { it.category == CategoryType.PFAND.storageValue && it.totalCents < 0 }
		val flowType = when {
			onlyDepositReturns -> CheckoutFlowType.PURE_DEPOSIT_RETURN
			depositReturnAbs > 0L -> CheckoutFlowType.MIXED
			else -> CheckoutFlowType.NORMAL_SALE
		}
		val payout = if (flowType == CheckoutFlowType.PURE_DEPOSIT_RETURN) depositReturnAbs else 0L
		val netDue = if (flowType == CheckoutFlowType.PURE_DEPOSIT_RETURN) 0L else cartTotal.coerceAtLeast(0L)
		return CheckoutFlowContext(
			flowType = flowType,
			cartTotalCents = cartTotal,
			salesTotalCents = salesTotal,
			depositReturnTotalCents = depositReturnAbs,
			payoutCents = payout,
			netDueCents = netDue
		)
	}

	/** Schritt 2: Erhaltenen Betrag eingeben und Rückgeld berechnen */
	private fun showPaymentDialog(context: CheckoutFlowContext) {
		val input = EditText(this).apply {
			hint = "Erhaltener Betrag (z. B. 20.00)"
			inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
			if (context.flowType == CheckoutFlowType.PURE_DEPOSIT_RETURN || context.netDueCents <= 0L) setText("0,00")
		}
		val container = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			val pad = (16 * resources.displayMetrics.density).toInt()
			setPadding(pad, pad, pad, 0)
			addView(input)
		}
		val message = when (context.flowType) {
			CheckoutFlowType.PURE_DEPOSIT_RETURN -> "Reine Pfandrückgabe\nAuszahlung: ${currencyFormatter.format(context.payoutCents / 100.0)}\nZahlbetrag kann 0,00 € sein."
			CheckoutFlowType.MIXED -> "Gemischter Warenkorb\nVerkauf: ${currencyFormatter.format(context.salesTotalCents / 100.0)}\nPfandrückgabe: ${currencyFormatter.format(context.depositReturnTotalCents / 100.0)}\nNetto zu kassieren: ${currencyFormatter.format(context.netDueCents / 100.0)}"
			CheckoutFlowType.NORMAL_SALE -> "Wie viel hat der Kunde gegeben?"
		}
		AlertDialog.Builder(this)
			.setTitle("Kassieren – ${currencyFormatter.format(context.cartTotalCents / 100.0)}")
			.setMessage(message)
			.setView(container)
			.setPositiveButton("Kassieren") { _, _ ->
				if (checkoutInProgress) return@setPositiveButton
				val givenParsed = MoneyValueService.parseAmountToCents(input.text.toString(), allowZero = true)
				if (!givenParsed.isValid) {
					showAmountValidationError(givenParsed.error)
					return@setPositiveButton
				}
				val givenCents = givenParsed.cents ?: 0L
				if (context.flowType != CheckoutFlowType.PURE_DEPOSIT_RETURN && context.netDueCents > 0L && givenCents < context.netDueCents) {
					showAmountValidationError(MoneyValueService.ParseError.ZERO_OR_NEGATIVE, customMessage =
						"Unterzahlung: es fehlen ${currencyFormatter.format((context.netDueCents - givenCents) / 100.0)}")
					return@setPositiveButton
				}

				checkoutInProgress = true
				updateOperationState(UiOperationState.Saving)
				val transactionId = currentCheckoutTransactionId ?: UUID.randomUUID().toString().also {
					currentCheckoutTransactionId = it
				}
				val changeCents = when (context.flowType) {
					CheckoutFlowType.PURE_DEPOSIT_RETURN -> -context.payoutCents
					else -> givenCents - context.netDueCents
				}
				val drafts = cartItems.map { entry ->
					SaleDraftEntry(
						displayName = entry.displayName,
						totalCents = entry.totalCents,
						articleName = entry.articleName,
						category = entry.category,
						servingType = entry.servingType,
						priceCents = entry.priceCents,
						depositCents = entry.depositCents,
						isEmployee = entry.isEmployee,
						createBottleHelperRecord = entry.createBottleHelperRecord,
						explicitOfferTaxCategory = entry.explicitOfferTaxCategory,
						orderDraft = entry.orderDraft
					)
				}
				lifecycleScope.launch {
					when (val result = persistCheckout(drafts, context.cartTotalCents, transactionId)) {
						is OperationResult.Success -> {
							persistDepositReturnMovementsForTransaction(transactionId)
							cartItems.clear()
							currentCheckoutTransactionId = null
							updateCart()
							updateOperationState(UiOperationState.Success("Kassiervorgang gespeichert"))
							showChangeDialog(changeCents)
						}
						is OperationResult.Error -> {
							updateOperationState(UiOperationState.Error(result.message))
						}
					}
					checkoutInProgress = false
				}
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun showAmountValidationError(error: MoneyValueService.ParseError?, customMessage: String? = null) {
		val message = customMessage ?: when (error) {
			MoneyValueService.ParseError.EMPTY -> "Bitte Betrag eingeben."
			MoneyValueService.ParseError.INVALID_FORMAT -> "Ungültiger Betrag. Erlaubt sind z. B. 12,50 oder 12.50."
			MoneyValueService.ParseError.NEGATIVE -> "Negative Beträge sind nicht erlaubt."
			MoneyValueService.ParseError.ZERO_OR_NEGATIVE -> "Betrag muss größer als 0 sein."
			MoneyValueService.ParseError.TOO_LARGE -> "Betrag ist zu groß."
			null -> "Ungültiger Betrag."
		}
		AlertDialog.Builder(this)
			.setTitle("Eingabe prüfen")
			.setMessage(message)
			.setPositiveButton("OK", null)
			.show()
	}

	/** Schritt 3: Rückgeld anzeigen */
	private fun showChangeDialog(changeCents: Long) {
		val changeText = when {
			changeCents > 0L -> "Rückgeld: ${currencyFormatter.format(changeCents / 100.0)}"
			changeCents < 0L -> "Auszahlung: ${currencyFormatter.format((-changeCents) / 100.0)}"
			else -> "Kein Rückgeld / keine zusätzliche Auszahlung"
		}
		AlertDialog.Builder(this)
			.setTitle("Kassiert ✓")
			.setMessage(changeText)
			.setPositiveButton("OK", null)
			.show()
	}

	private fun persistDepositReturnMovementsForTransaction(transactionId: String) {
		val movements = cartItems.filter { it.category == CategoryType.PFAND.storageValue && it.totalCents < 0 }
			.mapNotNull { entry ->
				val normalized = entry.articleName.lowercase(Locale.GERMANY)
				val depositType = when {
					normalized.contains("glas") -> "GLASS"
					normalized.contains("flasche") -> "BOTTLE"
					normalized.contains("teller") -> "PLATE"
					else -> return@mapNotNull null
				}
				val quantity = Regex("x(\\d+)").find(entry.displayName)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
				val totalAmount = -entry.totalCents.toLong()
				val unitAmount = (totalAmount / quantity).coerceAtLeast(1L)
				DepositMovement(
					transactionId = transactionId,
					depositType = depositType,
					displayName = when (depositType) {
						"GLASS" -> "Glas"
						"BOTTLE" -> "Flasche"
						else -> "Teller"
					},
					quantity = quantity,
					unitAmountCents = unitAmount,
					totalAmountCents = totalAmount,
					movementType = DepositMovementType.RETURNED
				)
			}
		if (movements.isNotEmpty()) {
			depositMovementStorage.appendMovements(movements)
		}
	}

	private fun addToCart(entry: CartEntry) {
		cartItems.add(entry)
		currentCheckoutTransactionId = null
		updateCart()
	}

	private fun removeFromCart(position: Int) {
		if (position in cartItems.indices) {
			cartItems.removeAt(position)
			currentCheckoutTransactionId = null
			updateCart()
		}
	}

	private fun updateCart() {
		cartAdapter.submitList(cartItems.toList())
		val total = cartItems.sumOf { it.totalCents }
		cartTotalText.text = "Gesamt: ${currencyFormatter.format(total / 100.0)}"
		if (::cartCheckoutButton.isInitialized) {
			val isSaving = operationState is UiOperationState.Saving
			cartCheckoutButton.isEnabled = cartItems.isNotEmpty() && !isSaving
		}
	}

	private fun setupCategoryButtons() {
		try {
			categoryButtons = mapOf(
				CategoryType.WEIN to findViewById(R.id.categoryWeinButton),
				CategoryType.SOFTGETRAENKE to findViewById(R.id.categorySoftButton),
				CategoryType.SPECK to findViewById(R.id.categorySpeckButton),
				CategoryType.KAESE to findViewById(R.id.categoryKaeseButton),
				CategoryType.SNACKS to findViewById(R.id.categorySnacksButton),
				CategoryType.ANGEBOT to findViewById(R.id.categoryAngebotButton)
			)
			categoryButtons.forEach { (category, button) ->
				button.setOnClickListener { selectCategory(category) }
			}
			findViewById<Button?>(R.id.depositReturnActionButton)?.setOnClickListener {
				showDepositReturnTypeDialog()
			}
			updateCategoryButtonsState(CategoryType.WEIN)
		} catch (e: Exception) {
			updateOperationState(UiOperationState.Error("Kategorien konnten nicht geladen werden"))
		}
	}

	private fun updateCategoryButtonsState(activeCategory: CategoryType) {
		if (!::categoryButtons.isInitialized) return
		categoryButtons.forEach { (category, button) ->
			button.isSelected = category == activeCategory
		}
	}

	private fun selectCategory(categoryType: CategoryType) {
		selectedCategory = categoryType
		updateCategoryButtonsState(categoryType)
		observeJob?.cancel()
		observeJob = lifecycleScope.launch {
			try {
				repository.observeArticlesByCategory(categoryType)
					.collectLatest { articles ->
						adapter.submitList(articles)
					}
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (error: Exception) {
				Log.e("SalesActivity", "Artikel konnten nicht geladen werden", error)
				updateOperationState(
					UiOperationState.Error(
						"Artikel konnten nicht geladen werden: ${error.message ?: "unbekannter Fehler"}"
					)
				)
			}
		}
	}

	private fun buildVirtualDepositReturnArticles(): List<ArticleEntity> {
		val glass = ArticleEntity(
			"Glas",
			CategoryType.PFAND.storageValue,
			globalDepositSettings.glassDepositCents,
			null,
			false,
			false,
			false,
			false,
			false,
			false,
			false,
			false,
			0,
			0,
			false,
			false,
			0,
			0
		)
		glass.id = -101
		val bottle = ArticleEntity(
			"Flasche",
			CategoryType.PFAND.storageValue,
			globalDepositSettings.bottleDepositCents,
			null,
			false,
			false,
			false,
			false,
			false,
			false,
			false,
			false,
			0,
			0,
			false,
			false,
			0,
			0
		)
		bottle.id = -102
		val plate = ArticleEntity(
			"Teller",
			CategoryType.PFAND.storageValue,
			globalDepositSettings.plateDepositCents,
			null,
			false,
			false,
			false,
			false,
			false,
			false,
			false,
			false,
			0,
			0,
			false,
			false,
			0,
			0
		)
		plate.id = -103
		return listOf(glass, bottle, plate)
	}

	private fun showDepositReturnTypeDialog() {
		val virtual = buildVirtualDepositReturnArticles()
		val labels = virtual.map { article ->
			"${article.name} (${fmtCents(article.priceCents)})"
		}.toTypedArray()
		AlertDialog.Builder(this)
			.setTitle("Pfandrückgabe")
			.setItems(labels) { _, which ->
				showDepositReturnDialog(virtual[which])
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun handleArticleSelection(article: ArticleEntity) {
		if (selectedCategory == CategoryType.PFAND && article.id < 0L) {
			showDepositReturnDialog(article)
			return
		}
		if (selectedCategory == CategoryType.WEIN && article.isWein) {
			showWineDepositDialog(article)
			return
		}
		if (selectedCategory == CategoryType.SOFTGETRAENKE && (article.depositApplicable || article.glassDepositOptional)) {
			showSoftdrinkDepositDialog(article)
			return
		}
		if (article.category == CategoryType.ANGEBOT.name) {
			showAngebotSizeDialog(article)
			return
		}
		if (selectedCategory == CategoryType.SNACKS) {
			showSnackSizeDialog(article)
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
		// Pfand-Typ aus Artikelkonfiguration: glassDepositOptional=true → Glaspfand, sonst Flaschenpfand
		val depositToken = if (article.glassDepositOptional) "glas" else "flasche"
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
						depositTypeToken = depositToken,
						customPriceCents = article.priceCents,
						isEmployee = false
					)
				}
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun showAngebotSizeDialog(article: ArticleEntity) {
		val hasLarge = article.hasLargeOption && article.largePriceCents > 0
		val hasSmall = article.hasSmallOption && article.smallPriceCents > 0
		when {
			hasLarge && hasSmall -> {
				val options = arrayOf(
					"Groß (${fmtCents(article.largePriceCents)})",
					"Klein (${fmtCents(article.smallPriceCents)})"
				)
				AlertDialog.Builder(this)
					.setTitle("${article.name} – Größe wählen")
					.setItems(options) { _, which ->
						val sizeName = if (which == 0) "Groß" else "Klein"
						val price = if (which == 0) article.largePriceCents else article.smallPriceCents
						showSnackToppingsDialog(article, sizeName, price) { gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch ->
							showAngebotGlaeserDialog(
								article = article,
								sizeName = sizeName,
								priceCents = price,
								gurken = gurken,
								tomaten = tomaten,
								zwiebeln = zwiebeln,
								oliven = oliven,
								brezeln = brezeln,
								sonderwunsch = sonderwunsch
							)
						}
					}
					.setNegativeButton("Abbrechen", null)
					.show()
			}
			hasLarge -> showSnackToppingsDialog(article, "Groß", article.largePriceCents) { gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch ->
				showAngebotGlaeserDialog(article, "Groß", article.largePriceCents, gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch)
			}
			hasSmall -> showSnackToppingsDialog(article, "Klein", article.smallPriceCents) { gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch ->
				showAngebotGlaeserDialog(article, "Klein", article.smallPriceCents, gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch)
			}
			else -> showSnackToppingsDialog(article, "", article.priceCents) { gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch ->
				showAngebotGlaeserDialog(article, "", article.priceCents, gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch)
			}
		}
	}

	private fun showAngebotGlaeserDialog(
		article: ArticleEntity,
		sizeName: String,
		priceCents: Int,
		gurken: Int,
		tomaten: Int,
		zwiebeln: Int,
		oliven: Int,
		brezeln: Int,
		sonderwunsch: String
	) {
		var g01 = 0
		var g02 = 0
		val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_angebot_glaeser, null)
		val g01CountTv = dialogView.findViewById<TextView>(R.id.glas01Count)
		val g02CountTv = dialogView.findViewById<TextView>(R.id.glas02Count)

		fun refresh() {
			g01CountTv.text = g01.toString()
			g02CountTv.text = g02.toString()
		}
		refresh()
		dialogView.findViewById<Button>(R.id.glas01Minus).setOnClickListener { if (g01 > 0) { g01--; refresh() } }
		dialogView.findViewById<Button>(R.id.glas01Plus).setOnClickListener { g01++; refresh() }
		dialogView.findViewById<Button>(R.id.glas02Minus).setOnClickListener { if (g02 > 0) { g02--; refresh() } }
		dialogView.findViewById<Button>(R.id.glas02Plus).setOnClickListener { g02++; refresh() }

		val sizeLabel = if (sizeName.isNotBlank()) " ($sizeName)" else ""
		AlertDialog.Builder(this)
			.setTitle("${article.name}$sizeLabel – Gläser")
			.setView(dialogView)
			.setPositiveButton("Weiter") { _, _ ->
				showAngebotPfandDialog(
					article = article,
					sizeName = sizeName,
					priceCents = priceCents,
					gurken = gurken,
					tomaten = tomaten,
					zwiebeln = zwiebeln,
					oliven = oliven,
					brezeln = brezeln,
					sonderwunsch = sonderwunsch,
					glaesser01 = g01,
					glaesser02 = g02
				)
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun showAngebotPfandDialog(
		article: ArticleEntity,
		sizeName: String,
		priceCents: Int,
		gurken: Int,
		tomaten: Int,
		zwiebeln: Int,
		oliven: Int,
		brezeln: Int,
		sonderwunsch: String,
		glaesser01: Int,
		glaesser02: Int
	) {
		val options = arrayOf(
			"Mit Tellerpfand + Glaspfand",
			"Mit Tellerpfand",
			"Mit Glaspfand",
			"Ohne Pfand",
			"Mitarbeiter"
		)
		AlertDialog.Builder(this)
			.setTitle("${article.name} – Pfand")
			.setItems(options) { _, which ->
				val isEmployee = which == 4
				val withTeller = !isEmployee && (which == 0 || which == 1)
				val withGlas = !isEmployee && (which == 0 || which == 2)
				val finalPrice = if (isEmployee) 0 else priceCents
				placeAngebotOrder(
					article = article,
					sizeName = sizeName,
					priceCents = finalPrice,
					withTellerDeposit = withTeller,
					withGlasDeposit = withGlas,
					gurken = gurken,
					tomaten = tomaten,
					zwiebeln = zwiebeln,
					oliven = oliven,
					brezeln = brezeln,
					sonderwunsch = sonderwunsch,
					glaesser01 = glaesser01,
					glaesser02 = glaesser02,
					isEmployee = isEmployee
				)
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun placeAngebotOrder(
		article: ArticleEntity,
		sizeName: String,
		priceCents: Int,
		withTellerDeposit: Boolean,
		withGlasDeposit: Boolean,
		gurken: Int,
		tomaten: Int,
		zwiebeln: Int,
		oliven: Int,
		brezeln: Int,
		sonderwunsch: String,
		glaesser01: Int,
		glaesser02: Int,
		isEmployee: Boolean
	) {
		val tellerDepositCents = if (withTellerDeposit && !isEmployee) globalDepositSettings.plateDepositCents else 0
		val glasDepositCents = if (withGlasDeposit && !isEmployee) {
			globalDepositSettings.glassDepositCents * (glaesser01 + glaesser02)
		} else 0
		val actualDepositCents = tellerDepositCents + glasDepositCents
		val total = priceCents + actualDepositCents
		val sizeLabel = if (sizeName.isNotBlank()) " ($sizeName)" else ""
		val empLabel = if (isEmployee) " – Mitarbeiter" else ""
		val pfandLabel = when {
			withTellerDeposit && withGlasDeposit -> " +Teller+Glaspfand"
			withTellerDeposit -> " +Tellerpfand"
			withGlasDeposit -> " +Glaspfand"
			else -> ""
		}
		addToCart(CartEntry(
			displayName = "${article.name}$sizeLabel$empLabel$pfandLabel",
			totalCents = total,
			articleName = article.name,
			category = CategoryType.ANGEBOT.storageValue,
			servingType = if (sizeName.isNotBlank()) sizeName.uppercase() else "STANDARD",
			priceCents = priceCents,
			depositCents = actualDepositCents,
			isEmployee = isEmployee,
			createBottleHelperRecord = true,
			explicitOfferTaxCategory = TaxCategory.FOOD,
			orderDraft = OrderDraftPayload(
				articleName = article.name,
				sizeName = sizeName,
				basePriceCents = priceCents,
				depositCents = actualDepositCents,
				isEmployee = isEmployee,
				gurken = gurken,
				tomaten = tomaten,
				zwiebeln = zwiebeln,
				oliven = oliven,
				brezeln = brezeln,
				sonderwunsch = sonderwunsch,
				glaesser01 = glaesser01,
				glaesser02 = glaesser02
			)
		))
	}

	private fun showSnackSizeDialog(article: ArticleEntity) {
		val hasLarge = article.hasLargeOption && article.largePriceCents > 0
		val hasSmall = article.hasSmallOption && article.smallPriceCents > 0
		when {
			hasLarge && hasSmall -> {
				val options = arrayOf(
					"Groß (${fmtCents(article.largePriceCents)})",
					"Klein (${fmtCents(article.smallPriceCents)})"
				)
				AlertDialog.Builder(this)
					.setTitle("${article.name} – Größe wählen")
					.setItems(options) { _, which ->
						val sizeName = if (which == 0) "Groß" else "Klein"
						val price = if (which == 0) article.largePriceCents else article.smallPriceCents
						showSnackToppingsDialog(article, sizeName, price) { gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch ->
							showSnackPfandDialog(article, sizeName, price, gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch)
						}
					}
					.setNegativeButton("Abbrechen", null)
					.show()
			}
			hasLarge -> showSnackToppingsDialog(article, "Groß", article.largePriceCents) { gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch ->
				showSnackPfandDialog(article, "Groß", article.largePriceCents, gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch)
			}
			hasSmall -> showSnackToppingsDialog(article, "Klein", article.smallPriceCents) { gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch ->
				showSnackPfandDialog(article, "Klein", article.smallPriceCents, gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch)
			}
			else -> showSnackToppingsDialog(article, "", article.priceCents) { gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch ->
				showSnackPfandDialog(article, "", article.priceCents, gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch)
			}
		}
	}

	private fun fmtCents(cents: Int): String =
		NumberFormat.getCurrencyInstance(Locale.GERMANY).format(cents / 100.0)

	private fun showSnackToppingsDialog(
		article: ArticleEntity,
		sizeName: String,
		priceCents: Int,
		onContinue: (gurken: Int, tomaten: Int, zwiebeln: Int, oliven: Int, brezeln: Int, sonderwunsch: String) -> Unit
	) {
		val counts = IntArray(5) { 1 }
		val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_snack_toppings, null)

		val countViews = listOf<TextView>(
			dialogView.findViewById(R.id.gurkenCount),
			dialogView.findViewById(R.id.tomatenCount),
			dialogView.findViewById(R.id.zwiebelCount),
			dialogView.findViewById(R.id.olivenCount),
			dialogView.findViewById(R.id.bretzelnCount)
		)
		val minusBtns = listOf<Button>(
			dialogView.findViewById(R.id.gurkenMinus),
			dialogView.findViewById(R.id.tomatenMinus),
			dialogView.findViewById(R.id.zwiebelMinus),
			dialogView.findViewById(R.id.olivenMinus),
			dialogView.findViewById(R.id.bretzelnMinus)
		)
		val plusBtns = listOf<Button>(
			dialogView.findViewById(R.id.gurkenPlus),
			dialogView.findViewById(R.id.tomatenPlus),
			dialogView.findViewById(R.id.zwiebelPlus),
			dialogView.findViewById(R.id.olivenPlus),
			dialogView.findViewById(R.id.bretzelnPlus)
		)
		val sonderwunschInput = dialogView.findViewById<EditText>(R.id.sonderwunschInput)

		fun refresh() { countViews.forEachIndexed { i, tv -> tv.text = counts[i].toString() } }
		refresh()
		minusBtns.forEachIndexed { i, btn -> btn.setOnClickListener { if (counts[i] > 0) { counts[i]--; refresh() } } }
		plusBtns.forEachIndexed  { i, btn -> btn.setOnClickListener { counts[i]++; refresh() } }

		val sizeLabel = if (sizeName.isNotBlank()) " ($sizeName)" else ""
		AlertDialog.Builder(this)
			.setTitle("${article.name}$sizeLabel – Zutaten")
			.setView(dialogView)
			.setPositiveButton("Weiter") { _, _ ->
				val sw = sonderwunschInput.text.toString().trim()
				onContinue(counts[0], counts[1], counts[2], counts[3], counts[4], sw)
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun showSnackPfandDialog(
		article: ArticleEntity, sizeName: String, priceCents: Int,
		gurken: Int, tomaten: Int, zwiebeln: Int, oliven: Int, brezeln: Int,
		sonderwunsch: String
	) {
		if (!article.depositApplicable) {
			placeSnackOrder(article, sizeName, priceCents, false,
				gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch, false)
			return
		}
		AlertDialog.Builder(this)
			.setTitle("${article.name} – Pfand")
			.setItems(arrayOf("Mit Pfand", "Ohne Pfand", "Mitarbeiter")) { _, which ->
				val isEmployee  = which == 2
				val withDeposit = which == 0
				val finalPrice  = if (isEmployee) 0 else priceCents
				placeSnackOrder(article, sizeName, finalPrice, withDeposit,
					gurken, tomaten, zwiebeln, oliven, brezeln, sonderwunsch, isEmployee)
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun placeSnackOrder(
		article: ArticleEntity, sizeName: String, priceCents: Int,
		withDeposit: Boolean,
		gurken: Int, tomaten: Int, zwiebeln: Int, oliven: Int, brezeln: Int,
		sonderwunsch: String, isEmployee: Boolean
	) {
		val actualDepositCents = if (withDeposit && !isEmployee) {
			globalDepositSettings.plateDepositCents
		} else 0

		val total     = priceCents + actualDepositCents
		val sizeLabel = if (sizeName.isNotBlank()) " ($sizeName)" else ""
		val empLabel  = if (isEmployee) " – Mitarbeiter" else ""
		addToCart(CartEntry(
			displayName  = "${article.name}$sizeLabel$empLabel",
			totalCents   = total,
			articleName  = article.name,
			category     = article.category,
			servingType  = if (sizeName.isNotBlank()) sizeName.uppercase() else "STANDARD",
			priceCents   = priceCents,
			depositCents = actualDepositCents,
			isEmployee   = isEmployee,
			orderDraft = OrderDraftPayload(
				articleName = article.name,
				sizeName = sizeName,
				basePriceCents = priceCents,
				depositCents = actualDepositCents,
				isEmployee = isEmployee,
				gurken = gurken,
				tomaten = tomaten,
				zwiebeln = zwiebeln,
				oliven = oliven,
				brezeln = brezeln,
				sonderwunsch = sonderwunsch
			)
		))
	}

	private fun showWineDepositDialog(article: ArticleEntity) {
		val wineGlassEnabled = article.wineGlassDepositEnabled || article.glassDepositOptional
		val wineBottleEnabled = article.wineBottleDepositEnabled || article.hasBottleOption
		val labels = mutableListOf<String>()
		val actions = mutableListOf<Pair<Boolean, String?>>()
		if (wineGlassEnabled) {
			labels.add("Mit Glaspfand")
			actions.add(true to "glas")
		}
		if (wineBottleEnabled) {
			labels.add("Mit Flaschenpfand")
			actions.add(true to "flasche")
		}
		labels.add("Ohne Pfand")
		actions.add(false to null)
		labels.add("Mitarbeiter")
		actions.add(false to null)

		AlertDialog.Builder(this)
			.setTitle(article.name)
			.setItems(labels.toTypedArray()) { _, which ->
				val isEmployee = labels[which] == "Mitarbeiter"
				val action = actions[which]
				finalizeSelection(
					article = article,
					displayName = if (isEmployee) "${article.name} (Mitarbeiter)" else article.name,
					applyDeposit = !isEmployee && action.first,
					depositTypeToken = if (isEmployee) null else action.second,
					customPriceCents = if (isEmployee) 0 else article.priceCents,
					isEmployee = isEmployee,
					servingTypeStr = "STANDARD"
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

	private fun showDepositReturnDialog(article: ArticleEntity) {
		val token = detectNonWineDepositType(article)
		val unitAmountCents = globalDepositSettings.amountForToken(token)
		if (unitAmountCents <= 0) {
			showStateDialog("Fehler", "Für ${article.name} ist kein globaler Pfandbetrag konfiguriert.")
			return
		}
		val input = EditText(this).apply {
			hint = "Anzahl"
			inputType = InputType.TYPE_CLASS_NUMBER
		}
		AlertDialog.Builder(this)
			.setTitle("Pfandrückgabe – ${article.name}")
			.setView(input)
			.setPositiveButton("Hinzufügen") { _, _ ->
				val quantity = input.text.toString().trim().toIntOrNull()
				if (quantity == null || quantity <= 0) {
					showStateDialog("Fehler", "Bitte eine gültige Anzahl eingeben.")
					return@setPositiveButton
				}
				val total = unitAmountCents * quantity
				val entry = CartEntry(
					displayName = "Pfandrückgabe ${article.name} x$quantity",
					totalCents = -total,
					articleName = "Pfandrückgabe ${article.name}",
					category = CategoryType.PFAND.storageValue,
					servingType = "RETURN",
					priceCents = 0,
					depositCents = -total,
					isEmployee = false
				)
				addToCart(entry)
			}
			.setNegativeButton("Abbrechen", null)
			.show()
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
		lifecycleScope.launch {
			updateOperationState(UiOperationState.Loading)
			when (val result = buildCartEntry(
				article = article,
				displayName = displayName,
				applyDeposit = applyDeposit,
				depositTypeToken = depositTypeToken,
				customPriceCents = customPriceCents,
				isEmployee = isEmployee,
				servingTypeStr = servingTypeStr
			)) {
				is OperationResult.Success -> {
					addToCart(result.value)
					updateOperationState(UiOperationState.Idle)
				}
				is OperationResult.Error -> {
					updateOperationState(UiOperationState.Error(result.message))
				}
			}
		}
	}

	private suspend fun persistCheckout(
		drafts: List<SaleDraftEntry>,
		finalTotalCents: Long,
		transactionId: String
	): OperationResult<Unit> {
		return withContext(Dispatchers.IO) {
			runCatching {
				checkoutService.checkout(drafts, finalTotalCents, transactionId)
			}.fold(
				onSuccess = { OperationResult.Success(Unit) },
				onFailure = {
					OperationResult.Error(
						message = "Speichern fehlgeschlagen. Bitte erneut kassieren.",
						cause = it
					)
				}
			)
		}
	}

	private suspend fun buildCartEntry(
		article: ArticleEntity,
		displayName: String,
		applyDeposit: Boolean,
		depositTypeToken: String?,
		customPriceCents: Int,
		isEmployee: Boolean,
		servingTypeStr: String
	): OperationResult<CartEntry> {
		return withContext(Dispatchers.IO) {
			runCatching {
				val depositCents = if (!isEmployee && applyDeposit && !depositTypeToken.isNullOrBlank()) {
					globalDepositSettings.amountForToken(depositTypeToken)
				} else 0
				val drinkPrice = if (isEmployee) 0 else customPriceCents
				val totalCents = drinkPrice + depositCents
				val entryName = buildString {
					append(displayName)
					if (depositCents > 0) append(" + Pfand")
				}
				CartEntry(
					displayName = entryName,
					totalCents = totalCents,
					articleName = article.name,
					category = article.category,
					servingType = servingTypeStr,
					priceCents = drinkPrice,
					depositCents = depositCents,
					isEmployee = isEmployee
				)
			}.fold(
				onSuccess = { OperationResult.Success(it) },
				onFailure = {
					OperationResult.Error(
						message = "Artikel konnte nicht in den Warenkorb übernommen werden.",
						cause = it
					)
				}
			)
		}
	}

	private fun updateOperationState(state: UiOperationState) {
		operationState = state
		renderOperationState(state)
	}

	private fun renderOperationState(state: UiOperationState) {
		if (::cartClearButton.isInitialized && ::cartCheckoutButton.isInitialized) {
			val blockButtons = state is UiOperationState.Saving
			cartClearButton.isEnabled = !blockButtons
			cartCheckoutButton.isEnabled = !blockButtons && cartItems.isNotEmpty()
		}
		when (state) {
			is UiOperationState.Success -> showStateDialog("Erfolg", state.message)
			is UiOperationState.Error -> showStateDialog("Fehler", state.message)
			else -> Unit
		}
	}

	private fun showStateDialog(title: String, message: String) {
		AlertDialog.Builder(this)
			.setTitle(title)
			.setMessage(message)
			.setPositiveButton("OK", null)
			.show()
	}

	}
