pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val geckoEngine = providers.gradleProperty("whatsweb.gecko").orNull == "true"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        if (geckoEngine) {
            maven {
                url = uri("https://maven.mozilla.org/maven2/")
            }
        }
    }
}

rootProject.name = "whatsweb"
include(":app")
