#!/usr/bin/env python3
"""Generates build.gradle.kts + AndroidManifest scaffolding for every module.

This script is idempotent and exists so the module graph stays reviewable in one
place. Run it from the repository root: python3 scripts/gen_modules.py
"""
import os
import textwrap

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# name -> (namespace suffix, [project deps], [catalog libs], compose?, serialization?)
JVM = {
    "core:common": ([], []),
    "core:math": (["core:common"], ["kotlinx-serialization-json"]),
    "core:time": (["core:common"], ["kotlinx-serialization-json"]),
    "core:undo": (["core:common"], []),
    "core:serialization": (["core:common"], ["kotlinx-serialization-json"]),
    "domain:drawing": (["core:common", "core:math"], ["kotlinx-serialization-json"]),
    "domain:animation": (["core:common", "core:math", "core:time"], ["kotlinx-serialization-json"]),
    "domain:rigging": (["core:common", "core:math", "domain:animation"], ["kotlinx-serialization-json"]),
    "domain:camera": (["core:common", "core:math", "core:time", "domain:animation"], ["kotlinx-serialization-json"]),
    "domain:audio": (["core:common", "core:time"], ["kotlinx-serialization-json"]),
    "domain:model": (["core:common", "core:math", "core:time", "domain:drawing", "domain:animation",
                      "domain:rigging", "domain:camera", "domain:audio"], ["kotlinx-serialization-json"]),
    "domain:export": (["core:common", "core:math", "core:time", "domain:model"], ["kotlinx-serialization-json"]),
    "engine:animation": (["core:math", "core:time", "domain:model", "domain:animation"], []),
    "engine:drawing": (["core:math", "domain:model", "domain:drawing"], []),
    "engine:rigging": (["core:math", "domain:model", "domain:rigging", "engine:animation"], []),
    "engine:scene": (["core:math", "core:time", "domain:model", "domain:animation", "domain:camera",
                      "domain:rigging", "engine:animation", "engine:rigging"], []),
    "engine:rendering": (["core:math", "core:time", "domain:model", "domain:drawing", "domain:camera",
                          "engine:scene", "engine:drawing"], []),
    "engine:compositing": (["core:math", "domain:model", "engine:rendering"], []),
    "engine:export": (["core:time", "domain:model", "domain:export", "engine:scene", "engine:rendering",
                       "engine:compositing"], []),
}

SERIALIZATION_JVM = {"core:serialization", "core:math", "core:time", "domain:model", "domain:drawing", "domain:animation",
                     "domain:rigging", "domain:camera", "domain:audio", "domain:export"}

# module -> (project deps, catalog libs, compose)
ANDROID = {
    "data:project-store": (["core:common", "core:serialization", "domain:model", "domain:drawing",
                            "domain:animation", "domain:camera", "domain:audio", "engine:scene"],
                           ["kotlinx-serialization-json", "kotlinx-coroutines-android"], False),
    "data:asset-store": (["core:common", "core:math", "domain:model", "domain:drawing", "domain:animation",
                          "domain:rigging", "core:serialization"],
                         ["kotlinx-serialization-json", "kotlinx-coroutines-android"], False),
    "data:preferences": (["core:common"], ["androidx-datastore-preferences", "kotlinx-coroutines-android"], False),
    "data:cache": (["core:common"], ["kotlinx-coroutines-android"], False),
    "platform:graphics": (["core:math", "core:time", "domain:model", "domain:drawing", "domain:camera",
                           "engine:scene", "engine:drawing", "engine:rendering", "engine:compositing"],
                          [], True),
    "platform:media": (["core:common", "core:time", "domain:model", "domain:export", "domain:audio"],
                       ["kotlinx-coroutines-android"], False),
    "platform:android": (["core:common", "core:undo", "core:math", "core:time", "domain:model", "domain:drawing",
                          "domain:animation", "domain:camera", "domain:audio", "domain:export", "domain:rigging",
                          "engine:scene", "engine:animation", "engine:drawing", "engine:rendering",
                          "engine:compositing", "engine:export", "data:project-store", "data:asset-store",
                          "data:preferences", "data:cache", "platform:graphics", "platform:media"],
                         ["kotlinx-coroutines-android"], False),
    "ui:design-system": ([], [], True),
    "ui:components": (["ui:design-system", "core:math", "core:time"], [], True),
    "feature:editor": (["ui:design-system", "ui:components", "platform:android", "platform:graphics",
                        "core:math", "core:time", "core:undo", "domain:model", "domain:drawing",
                        "domain:animation", "domain:camera", "domain:audio", "domain:export", "domain:rigging",
                        "engine:scene", "engine:rendering", "engine:drawing", "engine:animation",
                        "data:project-store", "data:asset-store", "data:preferences",
                        "feature:drawing", "feature:timeline", "feature:animation", "feature:assets",
                        "feature:scenes", "feature:audio", "feature:compositing", "feature:export"], [], True),
    "feature:drawing": (["ui:design-system", "ui:components", "platform:android", "platform:graphics",
                         "core:math", "domain:model", "domain:drawing", "engine:drawing", "engine:scene",
                         "engine:rendering"], [], True),
    "feature:timeline": (["ui:design-system", "ui:components", "platform:android", "core:math", "core:time",
                          "domain:model", "domain:animation", "engine:animation", "engine:scene"], [], True),
    "feature:animation": (["ui:design-system", "ui:components", "platform:android", "core:math", "core:time",
                           "domain:model", "domain:animation", "domain:rigging", "engine:animation",
                           "data:asset-store"], [], True),
    "feature:assets": (["ui:design-system", "ui:components", "platform:android", "platform:graphics",
                        "core:math", "domain:model", "domain:drawing", "domain:animation", "domain:rigging",
                        "data:asset-store"], [], True),
    "feature:scenes": (["ui:design-system", "ui:components", "platform:android", "core:math", "core:time",
                        "domain:model", "domain:camera", "engine:scene"], [], True),
    "feature:audio": (["ui:design-system", "ui:components", "platform:android", "platform:media", "core:time",
                       "domain:model", "domain:audio"], [], True),
    "feature:compositing": (["ui:design-system", "ui:components", "platform:android", "core:math",
                             "domain:model", "engine:compositing", "engine:rendering"], [], True),
    "feature:export": (["ui:design-system", "ui:components", "platform:android", "platform:media",
                        "platform:graphics", "core:time", "domain:model", "domain:export", "engine:export",
                        "engine:rendering", "engine:scene"], [], True),
    "feature:onboarding": (["ui:design-system", "ui:components", "platform:android", "domain:model",
                            "data:project-store", "data:preferences"], [], True),
    "feature:settings": (["ui:design-system", "ui:components", "platform:android", "data:preferences"],
                         [], True),
    "integrations:ai": (["core:common", "core:math", "domain:model", "domain:drawing", "domain:animation",
                         "platform:android"], ["kotlinx-coroutines-android"], False),
}


def namespace(module: str) -> str:
    return "com.cartoonstudio." + module.replace(":", ".").replace("-", "")


def dep_block(projects, libs, indent="    "):
    lines = []
    for p in projects:
        lines.append(f'{indent}api(project(":{p}"))')
    for l in libs:
        lines.append(f'{indent}implementation(libs.{l.replace("-", ".")})')
    return "\n".join(lines)


JVM_TEMPLATE = """plugins {{
    `java-library`
    alias(libs.plugins.kotlin.jvm)
{extra_plugins}}}

java {{
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}}

kotlin {{
    compilerOptions {{
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }}
}}

dependencies {{
{deps}
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}}
"""

ANDROID_TEMPLATE = """plugins {{
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
{extra_plugins}}}

android {{
    namespace = "{ns}"
    compileSdk = 34

    defaultConfig {{
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }}

    buildTypes {{
        release {{
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }}
    }}

    compileOptions {{
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }}

    kotlinOptions {{
        jvmTarget = "17"
    }}
{compose_android}}}

dependencies {{
{deps}
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
{compose_deps}
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
}}
"""

COMPOSE_ANDROID_BLOCK = """
    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
"""

COMPOSE_DEPS = """
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)
"""


def write(path, content):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as f:
        f.write(content)


def main():
    for module, (projects, libs) in JVM.items():
        path = module.replace(":", "/")
        extra = "    alias(libs.plugins.kotlin.serialization)\n" if module in SERIALIZATION_JVM else ""
        write(f"{path}/build.gradle.kts",
              JVM_TEMPLATE.format(extra_plugins=extra, deps=dep_block(projects, libs)))
        os.makedirs(os.path.join(ROOT, path, "src/main/kotlin"), exist_ok=True)
        os.makedirs(os.path.join(ROOT, path, "src/test/kotlin"), exist_ok=True)

    for module, (projects, libs, compose) in ANDROID.items():
        path = module.replace(":", "/")
        extra = ""
        if any("serialization" in l for l in libs):
            extra = "    alias(libs.plugins.kotlin.serialization)\n"
        write(f"{path}/build.gradle.kts", ANDROID_TEMPLATE.format(
            extra_plugins=extra,
            ns=namespace(module),
            deps=dep_block(projects, libs),
            compose_android=COMPOSE_ANDROID_BLOCK if compose else "",
            compose_deps=COMPOSE_DEPS if compose else "",
        ))
        write(f"{path}/consumer-rules.pro", "")
        write(f"{path}/proguard-rules.pro", "")
        write(f"{path}/src/main/AndroidManifest.xml",
              '<?xml version="1.0" encoding="utf-8"?>\n<manifest />\n')
        os.makedirs(os.path.join(ROOT, path, "src/main/kotlin"), exist_ok=True)

    print(f"generated {len(JVM)} jvm modules and {len(ANDROID)} android modules")


if __name__ == "__main__":
    main()
