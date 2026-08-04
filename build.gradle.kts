plugins {
	id("com.android.application") version "4.2.2" apply false
	id("org.jetbrains.kotlin.android") version "1.5.31" apply false
}

allprojects {
	repositories {
		google()
		mavenCentral()
	}
}
