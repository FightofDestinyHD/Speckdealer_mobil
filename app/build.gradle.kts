plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
}

val keystoreFilePath = System.getenv("KEYSTORE_FILE")
val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
val signingKeyAlias = System.getenv("KEY_ALIAS")
val signingKeyPassword = System.getenv("KEY_PASSWORD")
val hasCiSigning = !keystoreFilePath.isNullOrBlank() && !keystorePassword.isNullOrBlank() && !signingKeyAlias.isNullOrBlank() && !signingKeyPassword.isNullOrBlank()

android {
	compileSdkVersion(31)

	defaultConfig {
		applicationId = "com.speckdealer.app"
		minSdkVersion(24)
		targetSdkVersion(31)
		versionCode = 1
		versionName = "0.1.0"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	signingConfigs {
		if (hasCiSigning) {
			create("release") {
				storeFile = file(keystoreFilePath!!)
				storePassword = keystorePassword
				keyAlias = signingKeyAlias
				keyPassword = signingKeyPassword
			}
		}
	}

	buildTypes {
		getByName("release") {
			isMinifyEnabled = false
			if (hasCiSigning) {
				signingConfig = signingConfigs.getByName("release")
			}
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_1_8
		targetCompatibility = JavaVersion.VERSION_1_8
	}

	kotlinOptions {
		jvmTarget = "1.8"
	}

	buildFeatures {
		viewBinding = true
	}
}

dependencies {
	implementation("androidx.core:core-ktx:1.7.0")
	implementation("androidx.appcompat:appcompat:1.4.2")
	implementation("com.google.android.material:material:1.5.0")
	implementation("androidx.constraintlayout:constraintlayout:2.1.4")
	implementation("com.google.android.play:app-update:2.0.1")

	testImplementation("junit:junit:4.13.2")
	androidTestImplementation("androidx.test.ext:junit:1.1.3")
	androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}
