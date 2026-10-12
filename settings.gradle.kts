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
    }
}

val geckoRepos = file("gradle/gecko.repositories.gradle.kts")
if (providers.gradleProperty("whatsweb.gecko").orNull == "true" && geckoRepos.isFile) {
    apply(from = geckoRepos)
}

rootProject.name = "whatsweb"
include(":app")
