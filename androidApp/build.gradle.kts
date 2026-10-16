import com.android.build.api.artifact.SingleArtifact
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import javax.inject.Inject

fun readXcconfigValue(file: File, key: String): String? {
    if (!file.exists()) return null
    return file.readLines()
        .asSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
        .map { line ->
            val separatorIndex = line.indexOf('=')
            line.substring(0, separatorIndex).trim() to line.substring(separatorIndex + 1).trim()
        }
        .firstOrNull { (entryKey, _) -> entryKey == key }
        ?.second
}

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sentry.android.gradle)
}

val localProps = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) propsFile.inputStream().use { load(it) }
}
val releaseStoreFile = localProps.getProperty("NUVIO_RELEASE_STORE_FILE")?.takeIf { it.isNotBlank() }
val releaseStorePassword = localProps.getProperty("NUVIO_RELEASE_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = localProps.getProperty("NUVIO_RELEASE_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val releaseKeyPassword = localProps.getProperty("NUVIO_RELEASE_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeystore = releaseStoreFile?.let(rootProject::file)
fun envOrLocalProperty(key: String): String? =
    providers.environmentVariable(key).orNull?.trim()?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }

val sentryAuthToken = envOrLocalProperty("SENTRY_AUTH_TOKEN")
val sentryOrg = envOrLocalProperty("SENTRY_ORG")
val sentryProject = envOrLocalProperty("SENTRY_PROJECT")
val sentryMappingUploadEnabled = sentryAuthToken != null && sentryOrg != null && sentryProject != null
val appVersionConfigFile = rootProject.file("iosApp/Configuration/Version.xcconfig")
val releaseAppVersionName = providers.gradleProperty("nuvio.app.versionName").orNull
    ?: readXcconfigValue(appVersionConfigFile, "MARKETING_VERSION")
    ?: error("MARKETING_VERSION is missing from ${appVersionConfigFile.path}")
// Avix: CI passes -Pavix.versionCode so every published APK has a monotonic versionCode.
val releaseAppVersionCode = providers.gradleProperty("avix.versionCode").orNull?.toIntOrNull()
    ?: readXcconfigValue(appVersionConfigFile, "CURRENT_PROJECT_VERSION")
    ?.toIntOrNull()
    ?: error("CURRENT_PROJECT_VERSION is missing or invalid in ${appVersionConfigFile.path}")
val requestedTaskNames = gradle.startParameter.taskNames.map { it.substringAfterLast(':') }
val buildsReleaseApks = requestedTaskNames.any {
    it.startsWith("assemble", ignoreCase = true) && it.endsWith("Release", ignoreCase = true)
}


// Avix: several AARs ship lib/<abi>/libc++_shared.so (ass-kt 0.5.1 with an older NDK, mpvlib 1.1.3
// with a newer one). The inherited pickFirsts rule let the older copy win, and libmpv.so then fails
// to load (missing std::__ndk1::__hash_memory). libc++_shared is backward compatible, so the newest
// copy (mpvlib's) is packaged from the app's own jniLibs, and verifyAvixCxxRuntime fails the build
// if any APK ends up with a different one.
val avixLegacyLibmpv = providers.gradleProperty("avix.legacyLibmpv").orNull?.toBoolean() == true
val avixCxxRuntimeDir = layout.buildDirectory.dir("generated/avixCxxRuntime")

abstract class ExtractCxxRuntimeTask @Inject constructor(
    private val archives: ArchiveOperations,
    private val files: FileSystemOperations,
) : DefaultTask() {
    @get:InputFiles
    abstract val aar: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun extract() {
        files.sync {
            from(archives.zipTree(aar.singleFile)) {
                include("jni/*/libc++_shared.so")
                eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
                includeEmptyDirs = false
            }
            into(outputDir)
        }
    }
}

abstract class VerifyCxxRuntimeTask : DefaultTask() {
    @get:InputFiles
    abstract val apkDir: DirectoryProperty

    @get:InputFiles
    abstract val expectedDir: DirectoryProperty

    @TaskAction
    fun verify() {
        fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val apks = apkDir.get().asFile.walk().filter { it.isFile && it.extension == "apk" }.toList()
        check(apks.isNotEmpty()) { "No APK found in ${apkDir.get().asFile}" }
        var checked = 0
        apks.forEach { apk ->
            ZipFile(apk).use { zip ->
                zip.entries().asSequence().filter { it.name.matches(Regex("lib/[^/]+/libc\\+\\+_shared\\.so")) }.forEach { entry ->
                    val abi = entry.name.split('/')[1]
                    val expected = expectedDir.get().file("$abi/libc++_shared.so").asFile
                    check(expected.exists()) { "No reference libc++_shared.so for $abi" }
                    val actual = zip.getInputStream(entry).use { it.readBytes() }
                    check(sha(actual) == sha(expected.readBytes())) {
                        "${apk.name}: ${entry.name} is not mpvlib's libc++_shared.so; libmpv.so would fail to load."
                    }
                    checked++
                }
            }
        }
        check(checked > 0) { "No libc++_shared.so found in ${apks.map { it.name }}" }
        logger.lifecycle("verifyAvixCxxRuntime: $checked libc++_shared.so entries match mpvlib")
    }
}

val extractAvixCxxRuntime = tasks.register<ExtractCxxRuntimeTask>("extractAvixCxxRuntime") {
    aar.from(
        configurations.detachedConfiguration(
            dependencies.create("app.gyrolet.mpvlib:mpvlib:${libs.versions.mpvlib.get()}@aar")
        )
    )
    outputDir.set(avixCxxRuntimeDir)
}

if (!avixLegacyLibmpv) {
    tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }.configureEach {
        dependsOn(extractAvixCxxRuntime)
    }
}

android {
    namespace = "com.nuvio.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileSdkMinor = libs.versions.android.compileSdkMinor.get().toInt()

    signingConfigs {
        create("release") {
            if (releaseKeystore != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null) {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.eibyx.nuvio"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = releaseAppVersionCode
        versionName = releaseAppVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
        }
        create("playstore") {
            dimension = "distribution"
        }
    }

    sourceSets.getByName("full") {
        manifest.srcFile("src/full/AndroidManifest.xml")
        jniLibs.directories.add("../composeApp/src/full/jniLibs")
    }

    // Avix: mpvlib's libc++_shared.so must win over older copies (see avixCxxRuntime below).
    if (!avixLegacyLibmpv) {
        sourceSets.getByName("main") {
            jniLibs.directories.add(avixCxxRuntimeDir.get().asFile.path)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf(
                "lib/*/libc++_shared.so",
                "lib/*/libavcodec.so",
                "lib/*/libavutil.so",
                "lib/*/libswscale.so",
                "lib/*/libswresample.so"
            )
        }
    }

    androidResources {
        noCompress += "cvr"
    }

    splits {
        abi {
            isEnable = buildsReleaseApks
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        getByName("release") {
            val minifyRelease = providers.gradleProperty("releaseMinifyEnabled")
                .map(String::toBooleanStrict)
                .getOrElse(true)
            isMinifyEnabled = minifyRelease
            isShrinkResources = minifyRelease
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "../composeApp/proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.applicationId.set("com.eibyx.nuvio.debug")
    }
}

if (!avixLegacyLibmpv) {
    androidComponents {
        onVariants { variant ->
            val cap = variant.name.replaceFirstChar { it.uppercase() }
            val verify = tasks.register<VerifyCxxRuntimeTask>("verifyAvixCxxRuntime$cap") {
                apkDir.set(variant.artifacts.get(SingleArtifact.APK))
                expectedDir.set(extractAvixCxxRuntime.flatMap { it.outputDir })
            }
            tasks.matching { it.name == "assemble$cap" }.configureEach { dependsOn(verify) }
        }
    }
}

sentry {
    includeProguardMapping.set(true)
    autoUploadProguardMapping.set(sentryMappingUploadEnabled)
    uploadNativeSymbols.set(false)
    autoUploadNativeSymbols.set(false)
    includeNativeSources.set(false)
    includeSourceContext.set(false)
    autoUploadSourceContext.set(false)
    includeDependenciesReport.set(false)
    telemetry.set(false)
    sentryAuthToken?.let(authToken::set)
    sentryOrg?.let(org::set)
    sentryProject?.let(projectName::set)
    ignoredBuildTypes.set(setOf("debug"))
    autoInstallation {
        enabled.set(false)
    }
    tracingInstrumentation {
        enabled.set(false)
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.appcompat)
    implementation(libs.compose.runtime)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    debugImplementation(libs.compose.uiTooling)
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.activity.compose)
    // MpvRxEngineSmokeTest talks to MPVLib directly (same AAR as :composeApp, deduplicated at packaging).
    if (providers.gradleProperty("avix.legacyLibmpv").orNull?.toBoolean() != true) {
        androidTestImplementation("app.gyrolet.mpvlib:mpvlib:${libs.versions.mpvlib.get()}@aar")
    }
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:${libs.versions.composeMultiplatform.get()}")
    debugImplementation("androidx.compose.ui:ui-test-manifest:${libs.versions.composeMultiplatform.get()}")
}
