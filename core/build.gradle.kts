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
    implementation(libs.kotlinx.coroutines.core)
    // GTFS-RT の protobuf バインディング（純 Java。HTTP 取得は data 側）
    api(libs.gtfs.realtime.bindings)
    // 鳥取県の GTFS-RT は protobuf の JSON 表現で配られるため、JsonFormat で読む
    implementation(libs.protobuf.java.util)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // 手順 1 のツールが生成したプリパッケージ DB を JVM から読んで検証するため（テスト専用）
    testImplementation(libs.sqlite.jdbc)
}

// tools/gtfs_import.py で合成フィクスチャから DB を生成し、core がそのスキーマを
// 正しく解釈できることをテストする。python3 が必要。
// パスは core から見た相対（../tools, ../app）にしておく。ルートプロジェクトの位置に依存しない。
val repoRoot = layout.projectDirectory.dir("..")
val sampleDb = layout.buildDirectory.file("sample/timtra_gtfs.db")
val generateSampleGtfsDb by tasks.registering(Exec::class) {
    val tools = repoRoot.dir("tools")
    inputs.file(tools.file("gtfs_import.py"))
    inputs.file(tools.file("testdata/sample_config.json"))
    inputs.dir(tools.dir("testdata/sample_gtfs"))
    outputs.file(sampleDb)
    workingDir(repoRoot)
    commandLine(
        "python3",
        "tools/gtfs_import.py",
        "build",
        "tools/testdata/sample_gtfs",
        "--config",
        "tools/testdata/sample_config.json",
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
        layout.projectDirectory
            .file("src/test/resources/jr_timetable_sample.json")
            .asFile.absolutePath,
    )
    systemProperty(
        "timtra.realJrTimetableJson",
        rootProject.layout.projectDirectory
            .file("data/src/main/assets/jr_timetable.json")
            .asFile.absolutePath,
    )
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
