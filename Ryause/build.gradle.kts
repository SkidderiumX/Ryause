import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    java
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
}

group = "omg.depchaivl.ryause"
version = "1.0.0"

java { toolchain.languageVersion.set(JavaLanguageVersion.of(8)) }

loom {
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        mixinConfig("clickgui.mixins.json")
    }
    mixin { defaultRefmapName.set("clickgui.refmap.json") }
    runConfigs {
        "client" {
            isIdeConfigGenerated = true
            programArgs("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker", "--mixin", "clickgui.mixins.json")
        }
    }
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/")
}

val embed: Configuration by configurations.creating
configurations.implementation.get().extendsFrom(embed)

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")
    embed(files("libs/mixin-0.7.11-SNAPSHOT.jar"))
}

val buildDate = LocalDate.now().format(DateTimeFormatter.ofPattern("MMddyy"))
val isBetaBuild = gradle.startParameter.taskNames.any {
    it == "buildBeta" || it.endsWith(":buildBeta")
}
val outputDirectoryName = if (isBetaBuild) "Beta-builds" else "Ryause-Build"
val outputPrefix = if (isBetaBuild) "Ryause-Beta-" else "Ryause-"
val dailyDirectory = projectDir.parentFile.resolve(outputDirectoryName).resolve(buildDate)
val numberPattern = Regex("^${Regex.escape(outputPrefix)}${buildDate}\\+(\\d+)\\.jar$")
val latestBuild = dailyDirectory.listFiles()
    ?.mapNotNull { numberPattern.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
    ?.fold(0) { latest, number -> if (number > latest) number else latest } ?: 0
val dailyBuildNumber = latestBuild + 1
val buildVersion = "$buildDate+$dailyBuildNumber"

tasks.processResources {
    inputs.property("version", buildVersion)
    filesMatching("mcmod.info") { expand("version" to buildVersion) }
}
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }

tasks.jar {
    from(embed.map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF", "LICENSE.txt")
        exclude("META-INF/services/javax.annotation.processing.Processor")
    }
    manifest.attributes(
        "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
        "TweakOrder" to "0",
        "MixinConfigs" to "clickgui.mixins.json",
        "FMLCorePluginContainsFMLMod" to "true",
        "ForceLoadAsMod" to "true"
    )
}

tasks.named<org.gradle.jvm.tasks.Jar>("remapJar") {
    archiveFileName.set("$outputPrefix$buildDate+$dailyBuildNumber.jar")
    destinationDirectory.set(dailyDirectory)
}

tasks.register("buildBeta") {
    group = "build"
    description = "Build the beta jar with its own daily build number."
    dependsOn("build")
}
