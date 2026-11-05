pluginManagement {
    repositories {
        maven { url = uri("${settingsDir}/local-m2") }
        // The aliyun mirrors answer 502 for KSP, and Gradle does not fall through to the next repo on 5xx.
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
            content { excludeGroup("com.google.devtools.ksp") }
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
            content { excludeGroup("com.google.devtools.ksp") }
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/gradle-plugin")
            content { excludeGroup("com.google.devtools.ksp") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("${settingsDir}/local-m2") }
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
            content { excludeGroup("com.google.devtools.ksp") }
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
            content { excludeGroup("com.google.devtools.ksp") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "WorkHub"
include(":shared")
include(":server")
include(":app")
