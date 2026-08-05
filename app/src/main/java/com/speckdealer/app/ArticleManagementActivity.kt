package com.speckdealer.app

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.speckdealer.app.data.AppGraph
import com.speckdealer.app.data.ArticleEntity
import com.speckdealer.app.data.ArticleRepository
import com.speckdealer.app.data.CategoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

class ArticleManagementActivity : AppCompatActivity() {

	private lateinit var repository: ArticleRepository
	private lateinit var adapter: ArticleAdapter
	private var selectedCategory: CategoryType = CategoryType.WEIN
	private var observeJob: Job? = null
	private var editingArticleId: Long? = null
	private var selectedImageUri: String? = null

	private lateinit var selectedCategoryTitle: TextView
	private lateinit var articleNameInput: EditText
	private lateinit var articlePriceInput: EditText
	private lateinit var isWeinCheckbox: CheckBox
	private lateinit var hasBottleCheckbox: CheckBox
	private lateinit var hasGlass01Checkbox: CheckBox
	private lateinit var hasGlass02Checkbox: CheckBox
	private lateinit var glassDepositOptionalCheckbox: CheckBox
	private lateinit var depositApplicableCheckbox: CheckBox
	private lateinit var wineOptionsContainer: View
	private lateinit var selectImageButton: Button
	private lateinit var selectedImageLabel: TextView
	private lateinit var glass01PriceInput: EditText
	private lateinit var glass02PriceInput: EditText

	private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
		if (uri != null) {
			lifecycleScope.launch {
				val internalPath = withContext(Dispatchers.IO) { copyImageToInternalStorage(uri) }
				if (internalPath != null) {
					selectedImageUri = internalPath
					selectedImageLabel.text = File(internalPath).name
				} else {
					showMessage("Bild konnte nicht gespeichert werden")
				}
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_article_management)

		try {
			repository = AppGraph.repository(this)
			setupViews()
			setupRecyclerView()
			setupCategoryButtons()
			setupFormActions()
			selectCategory(CategoryType.WEIN)
		} catch (e: Exception) {
			e.printStackTrace()
			Snackbar.make(findViewById(android.R.id.content), "Fehler beim Laden: ${e.message}", Snackbar.LENGTH_LONG).show()
		}
	}

	private fun setupViews() {
		selectedCategoryTitle = findViewById(R.id.selectedCategoryTitle)
		articleNameInput = findViewById(R.id.articleNameInput)
		articlePriceInput = findViewById(R.id.articlePriceInput)
		isWeinCheckbox = findViewById(R.id.isWeinCheckbox)
		hasBottleCheckbox = findViewById(R.id.hasBottleCheckbox)
		hasGlass01Checkbox = findViewById(R.id.hasGlass01Checkbox)
		hasGlass02Checkbox = findViewById(R.id.hasGlass02Checkbox)
		glassDepositOptionalCheckbox = findViewById(R.id.glassDepositOptionalCheckbox)
		depositApplicableCheckbox = findViewById(R.id.depositApplicableCheckbox)
		wineOptionsContainer = findViewById(R.id.wineOptionsContainer)
		selectImageButton = findViewById(R.id.selectImageButton)
		selectedImageLabel = findViewById(R.id.selectedImageLabel)
		glass01PriceInput = findViewById(R.id.glass01PriceInput)
		glass02PriceInput = findViewById(R.id.glass02PriceInput)
	}

	private fun setupRecyclerView() {
		adapter = ArticleAdapter(
			onEditClicked = { article -> loadArticleForEditing(article) },
			onDeleteClicked = { article -> confirmDelete(article) }
		)
		findViewById<RecyclerView>(R.id.articleRecyclerView).apply {
			layoutManager = LinearLayoutManager(this@ArticleManagementActivity)
			adapter = this@ArticleManagementActivity.adapter
		}
	}

	private fun setupCategoryButtons() {
		findViewById<Button>(R.id.categoryWeinButton).setOnClickListener { selectCategory(CategoryType.WEIN) }
		findViewById<Button>(R.id.categorySoftButton).setOnClickListener { selectCategory(CategoryType.SOFTGETRAENKE) }
		findViewById<Button>(R.id.categorySpeckButton).setOnClickListener { selectCategory(CategoryType.SPECK) }
		findViewById<Button>(R.id.categoryKaeseButton).setOnClickListener { selectCategory(CategoryType.KAESE) }
		findViewById<Button>(R.id.categorySnacksButton).setOnClickListener { selectCategory(CategoryType.SNACKS) }
		findViewById<Button>(R.id.categoryPfandButton).setOnClickListener { selectCategory(CategoryType.PFAND) }
	}

	private fun setupFormActions() {
		isWeinCheckbox.setOnCheckedChangeListener { _, _ ->
			wineOptionsContainer.visibility = if (selectedCategory == CategoryType.WEIN && isWeinCheckbox.isChecked) View.VISIBLE else View.GONE
		}

		selectImageButton.setOnClickListener { imagePicker.launch("image/*") }
		findViewById<Button>(R.id.saveArticleButton).setOnClickListener { saveArticle() }
		findViewById<Button>(R.id.clearArticleButton).setOnClickListener { clearForm() }
	}

	private fun selectCategory(categoryType: CategoryType) {
		selectedCategory = categoryType
		selectedCategoryTitle.text = "Kategorie: ${categoryType.displayName}"
		configureFormForCategory(categoryType)
		observeCategory(categoryType)
		clearForm()
	}

	private fun observeCategory(categoryType: CategoryType) {
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

	private fun configureFormForCategory(categoryType: CategoryType) {
		val isPfandCategory = categoryType == CategoryType.PFAND
		val isWeinCategory = categoryType == CategoryType.WEIN

		selectImageButton.visibility = if (isPfandCategory) View.GONE else View.VISIBLE
		selectedImageLabel.visibility = if (isPfandCategory) View.GONE else View.VISIBLE
		isWeinCheckbox.visibility = if (isWeinCategory) View.VISIBLE else View.GONE
		wineOptionsContainer.visibility = if (isWeinCategory && isWeinCheckbox.isChecked) View.VISIBLE else View.GONE
	}

	private fun saveArticle() {
		val name = articleNameInput.text.toString().trim()
		if (name.isEmpty()) {
			showMessage("Bitte Artikelname eingeben")
			return
		}

		val price = articlePriceInput.text.toString().trim().replace(',', '.')
		val priceCents = ((price.toDoubleOrNull() ?: -1.0) * 100).toInt()
		if (priceCents < 0) {
			showMessage("Bitte gültigen Preis eingeben")
			return
		}

		val isPfand = selectedCategory == CategoryType.PFAND
		val isWeinArtikel = selectedCategory == CategoryType.WEIN && isWeinCheckbox.isChecked

		val glass01Price = glass01PriceInput.text.toString().trim().replace(',', '.')
		val glass01Cents = if (isWeinArtikel && hasGlass01Checkbox.isChecked) {
			val v = ((glass01Price.toDoubleOrNull() ?: -1.0) * 100).toInt()
			if (v < 0) { showMessage("Bitte gültigen Preis für Glas 0,1l eingeben"); return }
			v
		} else 0

		val glass02Price = glass02PriceInput.text.toString().trim().replace(',', '.')
		val glass02Cents = if (isWeinArtikel && hasGlass02Checkbox.isChecked) {
			val v = ((glass02Price.toDoubleOrNull() ?: -1.0) * 100).toInt()
			if (v < 0) { showMessage("Bitte gültigen Preis für Glas 0,2l eingeben"); return }
			v
		} else 0

		val idToEdit = editingArticleId
		val article = ArticleEntity(
			name,
			selectedCategory.storageValue,
			priceCents,
			if (isPfand) null else selectedImageUri,
			isWeinArtikel,
			hasBottleCheckbox.isChecked,
			hasGlass01Checkbox.isChecked,
			hasGlass02Checkbox.isChecked,
			!isPfand && depositApplicableCheckbox.isChecked,
			selectedCategory == CategoryType.WEIN && glassDepositOptionalCheckbox.isChecked,
			glass01Cents,
			glass02Cents
		)
		idToEdit?.let { article.id = it }

		lifecycleScope.launch(Dispatchers.IO) {
			try {
				if (idToEdit == null) {
					repository.saveArticle(article)
				} else {
					repository.updateArticle(article)
				}
				launch(Dispatchers.Main) {
					showMessage("Artikel gespeichert")
					clearForm()
				}
			} catch (e: Exception) {
				e.printStackTrace()
				launch(Dispatchers.Main) {
					showMessage("Fehler: ${e.localizedMessage}")
				}
			}
		}
	}

	private fun loadArticleForEditing(article: ArticleEntity) {
		editingArticleId = article.id
		articleNameInput.setText(article.name)
		articlePriceInput.setText(String.format(Locale.US, "%.2f", article.priceCents / 100.0))
		selectedImageUri = article.imageUri
		selectedImageLabel.text = article.imageUri ?: "Kein Bild gewählt"
		isWeinCheckbox.isChecked = article.isWein
		glass01PriceInput.setText(if (article.glass01PriceCents > 0) String.format(Locale.US, "%.2f", article.glass01PriceCents / 100.0) else "")
		glass02PriceInput.setText(if (article.glass02PriceCents > 0) String.format(Locale.US, "%.2f", article.glass02PriceCents / 100.0) else "")
		hasBottleCheckbox.isChecked = article.hasBottleOption
		hasGlass01Checkbox.isChecked = article.hasGlass01Option
		hasGlass02Checkbox.isChecked = article.hasGlass02Option
		depositApplicableCheckbox.isChecked = article.depositApplicable
		glassDepositOptionalCheckbox.isChecked = article.glassDepositOptional
		wineOptionsContainer.visibility = if (selectedCategory == CategoryType.WEIN && isWeinCheckbox.isChecked) View.VISIBLE else View.GONE
	}

	private fun clearForm() {
		editingArticleId = null
		articleNameInput.setText("")
		articlePriceInput.setText("")
		selectedImageUri = null
		selectedImageLabel.text = "Kein Bild gewählt"
		glass01PriceInput.setText("")
		glass02PriceInput.setText("")
		isWeinCheckbox.isChecked = false
		hasBottleCheckbox.isChecked = false
		hasGlass01Checkbox.isChecked = false
		hasGlass02Checkbox.isChecked = false
		depositApplicableCheckbox.isChecked = false
		glassDepositOptionalCheckbox.isChecked = false
		wineOptionsContainer.visibility = View.GONE
	}

	private fun confirmDelete(article: ArticleEntity) {
		AlertDialog.Builder(this)
			.setTitle("Artikel löschen")
			.setMessage("\"${article.name}\" wirklich löschen?")
			.setPositiveButton("Löschen") { _, _ ->
				lifecycleScope.launch(Dispatchers.IO) {
					try {
						repository.deleteArticle(article)
						launch(Dispatchers.Main) {
							if (editingArticleId == article.id) clearForm()
							showMessage("\"${article.name}\" gelöscht")
						}
					} catch (e: Exception) {
						e.printStackTrace()
						launch(Dispatchers.Main) { showMessage("Fehler beim Löschen: ${e.localizedMessage}") }
					}
				}
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun showMessage(message: String) {
		Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
	}

	private fun copyImageToInternalStorage(uri: Uri): String? {
		return try {
			val imagesDir = File(filesDir, "images").apply { mkdirs() }
			val destFile = File(imagesDir, "${UUID.randomUUID()}.jpg")
			contentResolver.openInputStream(uri)?.use { input ->
				destFile.outputStream().use { output -> input.copyTo(output) }
			} ?: throw IOException("InputStream null für URI: $uri")
			destFile.absolutePath
		} catch (e: Exception) {
			Log.e("ArticleManagement", "Fehler beim Kopieren des Bildes: $uri", e)
			null
		}
	}
}
