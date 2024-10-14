plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.gradleup.shadow") version "8.3.3"
}

group = "xyz.bluspring"
version = property("version")!!

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "com.gradleup.shadow")

    repositories {
        mavenCentral()
        maven("https://gitlab.com/api/v4/projects/26794598/packages/maven")
        maven("https://jitpack.io")
        maven("https://m2.dv8tion.net/releases")
        maven("https://libraries.minecraft.net")
    }


    fun DependencyHandlerScope.include(
        dependencyNotation: String,
        action: Action<ExternalModuleDependency> = Action<ExternalModuleDependency> { }
    ) {
        shadow(dependencyNotation, action)
        implementation(dependencyNotation, action)
    }

    dependencies {
        include("org.jetbrains.kotlin:kotlin-stdlib")
        include("org.jetbrains.kotlin:kotlin-reflect")
        include("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

        include("org.slf4j:slf4j-api:2.0.5")
        include("ch.qos.logback:logback-classic:1.4.7")
        include("ch.qos.logback:logback-core:1.4.7")

        include("com.charleskorn.kaml:kaml:0.61.0")
    }

    kotlin {
        jvmToolchain(21)
    }

    tasks.shadowJar {
        archiveBaseName.set("Sprinkles")
        this.archiveVersion.set(rootProject.property("version").toString())
    }
}