pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        val localProperties = java.util.Properties().apply {
            rootDir.resolve("local.properties")
                .takeIf { it.isFile }
                ?.inputStream()
                ?.use(::load)
        }
        val githubUser = providers.gradleProperty("gpr.user").orNull
            ?: localProperties.getProperty("gpr.user")
            ?: System.getenv("GITHUB_ACTOR")
        val githubToken = providers.gradleProperty("gpr.key").orNull
            ?: localProperties.getProperty("gpr.key")
            ?: System.getenv("GITHUB_TOKEN")

        if (!githubUser.isNullOrBlank() && !githubToken.isNullOrBlank()) {
            maven("https://maven.pkg.github.com/trustwallet/wallet-core") {
                credentials {
                    username = githubUser
                    password = githubToken
                }
            }
        }
    }
}

rootProject.name = "WpayinWalletAndroid"
include(":app")
