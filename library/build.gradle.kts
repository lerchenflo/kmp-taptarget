import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Same group JitPack serves a multi-module repo under. With any other group JitPack moves the
// artifacts and rewrites the Gradle module metadata, dropping the "-metadata" / "-sources" classifiers
// from the file names, which breaks the IDE sync of Kotlin Multiplatform apps (iOS metadata jar not found)
group = "com.github.lerchenflo.kmp-taptarget"
//JitPack passes the git tag as -Pversion, local builds get a snapshot version
version = providers.gradleProperty("version").getOrElse("1.0.0-SNAPSHOT")

kotlin {

    jvm {
        compilerOptions {
            //11 so consumers with an older jvmTarget can use the library
            jvmTarget = JvmTarget.JVM_11
        }
    }

    android {
        namespace = "io.github.lerchenflo.taptarget"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            //11 so consumers with an older jvmTarget can use the library
            jvmTarget = JvmTarget.JVM_11
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.material3)
            implementation(libs.ui)
            implementation(libs.kotlinx.coroutines.core)

            //StringResource overloads of the tour DSL
            api(libs.components.resources)

            //For icons
            implementation(libs.material.icons.extended)

            //System back handling during a tour
            implementation(libs.navigationevent.compose)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ui.test)
        }

        //Overlay pointer tests run headless on the desktop JVM
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

//Keeps the Java part of the jvm target on the same bytecode level as the Kotlin part
tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}

mavenPublishing {

    coordinates(group.toString(), "kmp-taptarget", version.toString())

    pom {
        name.set("kmp-taptarget")
        description.set("Spotlight onboarding tours for Compose Multiplatform")
        inceptionYear.set("2026")
        url.set("https://github.com/lerchenflo/kmp-taptarget/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("main")
                name.set("lerchenflo")
                url.set("https://github.com/lerchenflo")
            }
        }
        scm {
            url.set("https://github.com/lerchenflo/kmp-taptarget/")
        }
    }
}
