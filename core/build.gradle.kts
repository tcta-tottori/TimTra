plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

// Android SDK に依存しない純 Kotlin/JVM モジュール。
// java.time を使うため Android 側は minSdk 26 以上（CLAUDE.md 9）。
// ツールチェーンは固定せず、実行中の JDK でビルドして 17 互換のバイトコードを出す
// （Android 側の compileOptions と揃える）。
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // 手順 1 のツールが生成したプリパッケージ DB を JVM から読んで検証するため（テスト専用）
    testImplementation(libs.sqlite.jdbc)
}

// tools/gtfs_import.py で合成フィクスチャから DB を生成し、core がそのスキーマを
// 正しく解釈できることをテストする。python3 が必要。
val sampleDb = layout.buildDirectory.file("sample/timtra_gtfs.db")
val generateSampleGtfsDb by tasks.registering(Exec::class) {
    val tools = rootProject.layout.projectDirectory.dir("tools")
    inputs.file(tools.file("gtfs_import.py"))
    inputs.file(tools.file("gtfs_config.json"))
    inputs.dir(tools.dir("testdata/sample_gtfs"))
    outputs.file(sampleDb)
    workingDir(rootProject.layout.projectDirectory)
    commandLine(
        "python3",
        "tools/gtfs_import.py",
        "build",
        "tools/testdata/sample_gtfs",
        "--config",
        "tools/gtfs_config.json",
        "--out",
        sampleDb.get().asFile.absolutePath,
    )
}

tasks.test {
    useJUnitPlatform()
    dependsOn(generateSampleGtfsDb)
    systemProperty("timtra.sampleDb", sampleDb.get().asFile.absolutePath)
    systemProperty(
        "timtra.jrTimetableJson",
        rootProject.layout.projectDirectory
            .file("app/src/main/assets/jr_timetable.json")
            .asFile.absolutePath,
    )
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
