import java.io.File
import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------------------
//  TDLib bootstrap
//  ---------------------------------------------------------------------------
//  The official prebuilt TDLib package for Android ships:
//     <root>/**/java/org/drinkless/tdlib/*.java   (typed bindings, ~5MB TdApi.java)
//     <root>/**/libs/<abi>/libtdjni.so            (native client)
//
//  We download it at build time.  NOTHING in our own source set references
//  org.drinkless.* at compile time (we bind through reflection in
//  TdLibGateway.kt), so if the download fails the app STILL compiles and simply
//  boots into offline mode instead of crashing.  That is deliberate.
// ---------------------------------------------------------------------------

val tdlibDir = layout.buildDirectory.dir("tdlib").get().asFile
val tdlibJavaDir = File(tdlibDir, "java")
val tdlibJniDir = File(tdlibDir, "jniLibs")
// اولی: بسته‌ی رسمیِ Telegram X (TDLib 1.8.67، همه‌ی ABI ها) — دومی: بسته‌ی قدیمی core.telegram.org
val tdlibUrls: List<String> = providers.environmentVariable("TDLIB_ZIP_URL").orNull
    ?.let { listOf(it) }
    ?: listOf(
        "https://github.com/CodexofLost/tdlib-packed/releases/download/v1.8.67/tdlib-v1.8.67.zip",
        "https://core.telegram.org/tdlib/tdlib.zip",
    )

/**
 * معماری‌هایی که بسته‌بندی می‌شوند.
 * پیش‌فرض هر سه‌تاست؛ با `-PitoAbis=arm64-v8a,armeabi-v7a` می‌شود محدودش کرد تا
 * APKِ یونیورسالِ «همه‌ی گوشی‌ها» زیر صد مگابایت بماند.
 */
val itoAbis: List<String> = (project.findProperty("itoAbis") as String?)
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: listOf("arm64-v8a", "armeabi-v7a", "x86_64")
val wantedAbis = itoAbis.toSet()
val skipTdlib = providers.environmentVariable("ITO_SKIP_TDLIB").getOrElse("0") == "1"

val fetchTdlib = tasks.register("fetchTdlib") {
    group = "ito"
    description = "Downloads the prebuilt TDLib Android package (best effort)."
    outputs.dir(tdlibJavaDir)
    outputs.dir(tdlibJniDir)
    doLast {
        tdlibJavaDir.mkdirs()
        tdlibJniDir.mkdirs()
        val marker = File(tdlibDir, ".ok")
        if (marker.exists()) {
            logger.lifecycle("[ito] TDLib already present, skipping download.")
            return@doLast
        }
        if (skipTdlib) {
            logger.lifecycle("[ito] ITO_SKIP_TDLIB=1 -> building in offline-only mode.")
            return@doLast
        }
        val zip = File(tdlibDir, "tdlib.zip")
        var downloaded = false
        for (url in tdlibUrls) {
            try {
                logger.lifecycle("[ito] Downloading TDLib from $url ...")
                val conn = URL(url).openConnection()
                conn.connectTimeout = 60_000
                conn.readTimeout = 300_000
                conn.setRequestProperty("User-Agent", "ito-build")
                conn.getInputStream().use { input ->
                    zip.outputStream().use { output -> input.copyTo(output) }
                }
                if (zip.length() > 1_000_000) {
                    downloaded = true
                    logger.lifecycle("[ito] Downloaded ${zip.length()} bytes")
                    break
                }
                logger.warn("[ito] Archive too small (${zip.length()} bytes), trying next mirror")
            } catch (e: Exception) {
                logger.warn("[ito] Download failed from $url: ${e.message}")
            }
        }
        if (!downloaded) {
            logger.warn("[ito] No TDLib archive available. Offline-only build.")
            return@doLast
        }
        val unpacked = File(tdlibDir, "unpacked")
        unpacked.deleteRecursively()
        copy {
            from(zipTree(zip))
            into(unpacked)
        }
        // locate org/drinkless/tdlib sources anywhere in the archive
        val pkgDir = unpacked.walkTopDown().firstOrNull {
            it.isDirectory && it.path.replace('\\', '/').endsWith("org/drinkless/tdlib")
        }
        if (pkgDir == null) {
            logger.warn("[ito] Could not find org/drinkless/tdlib inside the archive. Offline-only build.")
            return@doLast
        }
        val dest = File(tdlibJavaDir, "org/drinkless/tdlib")
        dest.mkdirs()
        pkgDir.listFiles()?.filter { it.extension == "java" }?.forEach { it.copyTo(File(dest, it.name), true) }

        // همه‌ی کتابخانه‌های نیتیوِ همراه (tdjni و وابستگی‌هایش) کپی می‌شوند
        val abis = HashSet<String>()
        unpacked.walkTopDown().filter { it.isFile && it.extension == "so" }.forEach { so ->
            val abi = so.parentFile.name
            if (abi !in wantedAbis) return@forEach
            val out = File(tdlibJniDir, abi)
            out.mkdirs()
            so.copyTo(File(out, so.name), true)
            abis += abi
        }
        logger.lifecycle(
            "[ito] TDLib ready: ${dest.listFiles()?.size ?: 0} java files, ABIs=${abis.sorted()}"
        )
        if (abis.isNotEmpty()) marker.writeText("ok")
    }
}

android {
    namespace = "ito.telegram"
    compileSdk = 35

    defaultConfig {
        applicationId = "ito.telegram"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-ito"
        multiDexEnabled = true
        vectorDrawables.useSupportLibrary = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include(*itoAbis.toTypedArray())
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("ito") {
            val ks = File(rootDir, "ito-release.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("ITO_STORE_PASS") ?: "itoTelegram"
                keyAlias = System.getenv("ITO_KEY_ALIAS") ?: "ito"
                keyPassword = System.getenv("ITO_KEY_PASS") ?: "itoTelegram"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (File(rootDir, "ito-release.jks").exists()) {
                signingConfig = signingConfigs.getByName("ito")
            }
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir(tdlibJavaDir)
            jniLibs.srcDir(tdlibJniDir)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        htmlReport = true
        textReport = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

tasks.named("preBuild").configure { dependsOn(fetchTdlib) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.multidex:multidex:2.0.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // camera + on-device face analysis for the strict 18+ gate
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
