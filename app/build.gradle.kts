import java.util.Properties

plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
}

val keystoreProps = Properties().apply {
	val file = rootProject.file("keystore.properties")
	if (file.exists()) {
		file.inputStream().use { load(it) }
	}
}

fun secret(name: String, propertyKey: String): String? {
	return System.getenv(name)?.takeIf { it.isNotBlank() }
		?: (keystoreProps.getProperty(propertyKey)?.takeIf { it.isNotBlank() })
}

val releaseStoreFilePath = secret("KEYSTORE_FILE", "storeFile")
val releaseStorePassword = secret("KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = secret("KEY_ALIAS", "keyAlias")
val releaseKeyPassword = secret("KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = !releaseStoreFilePath.isNullOrBlank() &&
	!releaseStorePassword.isNullOrBlank() &&
	!releaseKeyAlias.isNullOrBlank() &&
	!releaseKeyPassword.isNullOrBlank()
val releaseStoreFile = releaseStoreFilePath?.let { rootProject.file(it) }
val isReleaseTaskRequested = gradle.startParameter.taskNames.any { task ->
	task.contains("Release", ignoreCase = true)
}

if (isReleaseTaskRequested && !hasReleaseSigning) {
	throw GradleException(
		"Release-Signing nicht konfiguriert. Bitte KEYSTORE_* Umgebungsvariablen setzen oder eine lokale keystore.properties (nicht committen) anlegen."
	)
}
if (isReleaseTaskRequested && (releaseStoreFile == null || !releaseStoreFile.exists())) {
	throw GradleException("Keystore-Datei für Release nicht gefunden: ${releaseStoreFile?.absolutePath}")
}

android {
	namespace = "com.speckdealer.app"
	compileSdk = 34

	defaultConfig {
		applicationId = "com.speckdealer.app"
		minSdk = 24
		targetSdk = 34
		versionCode = 41
		versionName = "0.1.41"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	signingConfigs {
		create("release") {
			if (hasReleaseSigning) {
				storeFile = releaseStoreFile
				storePassword = releaseStorePassword
				keyAlias = releaseKeyAlias
				keyPassword = releaseKeyPassword
			}
		}
	}

	buildTypes {
		getByName("release") {
			isMinifyEnabled = false
			signingConfig = signingConfigs.getByName("release")
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	kotlinOptions {
		jvmTarget = "17"
	}

	buildFeatures {
		viewBinding = true
		buildConfig = true
	}
}

dependencies {
	implementation("androidx.core:core-ktx:1.12.0")
	implementation("androidx.appcompat:appcompat:1.6.1")
	implementation("com.google.android.material:material:1.11.0")
	implementation("androidx.constraintlayout:constraintlayout:2.1.4")
	implementation("androidx.recyclerview:recyclerview:1.3.2")
	implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
	implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
	implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
	implementation("androidx.room:room-runtime:2.5.2")
	implementation("androidx.room:room-ktx:2.5.2")
	annotationProcessor("androidx.room:room-compiler:2.5.2")
	implementation("com.google.android.play:app-update:2.1.0")

	testImplementation("junit:junit:4.13.2")
	androidTestImplementation("androidx.test.ext:junit:1.1.5")
	androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
