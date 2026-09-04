pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // Android 系のグループだけ Google Maven を見る（無関係な依存で問い合わせない）
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

rootProject.name = "timtra"

// core : Android 非依存の純 Kotlin/JVM（CLAUDE.md 3-3）。乗り継ぎ計算の本体。
// data : Room（プリパッケージ DB）・DataStore・リポジトリ。app と wear で共有する Android ライブラリ。
// app  : スマホ（Jetpack Compose）
// wear : Wear OS（手順 5 で追加）
include(":core")
include(":data")
include(":app")
