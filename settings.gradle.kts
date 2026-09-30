pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "Cartoon Studio"

// ---------------------------------------------------------------------------
// Application
// ---------------------------------------------------------------------------
include(":app")

// ---------------------------------------------------------------------------
// Core — pure Kotlin foundations (no Android dependencies)
// ---------------------------------------------------------------------------
include(":core:common")
include(":core:math")
include(":core:time")
include(":core:undo")
include(":core:serialization")

// ---------------------------------------------------------------------------
// Domain — pure Kotlin production models and rules
// ---------------------------------------------------------------------------
include(":domain:model")
include(":domain:drawing")
include(":domain:animation")
include(":domain:rigging")
include(":domain:camera")
include(":domain:audio")
include(":domain:export")

// ---------------------------------------------------------------------------
// Engine — deterministic evaluation, drawing and rendering
// ---------------------------------------------------------------------------
include(":engine:scene")
include(":engine:animation")
include(":engine:drawing")
include(":engine:rigging")
include(":engine:rendering")
include(":engine:compositing")
include(":engine:export")

// ---------------------------------------------------------------------------
// Data — persistence, catalogs and caches
// ---------------------------------------------------------------------------
include(":data:project-store")
include(":data:asset-store")
include(":data:preferences")
include(":data:cache")

// ---------------------------------------------------------------------------
// Platform — Android and device adapters
// ---------------------------------------------------------------------------
include(":platform:graphics")
include(":platform:media")
include(":platform:android")

// ---------------------------------------------------------------------------
// UI — shared design system and components
// ---------------------------------------------------------------------------
include(":ui:design-system")
include(":ui:components")

// ---------------------------------------------------------------------------
// Features — user facing workflows
// ---------------------------------------------------------------------------
include(":feature:editor")
include(":feature:drawing")
include(":feature:timeline")
include(":feature:animation")
include(":feature:assets")
include(":feature:scenes")
include(":feature:audio")
include(":feature:compositing")
include(":feature:export")
include(":feature:onboarding")
include(":feature:settings")

// ---------------------------------------------------------------------------
// Integrations
// ---------------------------------------------------------------------------
include(":integrations:ai")
