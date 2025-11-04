import java.io.ByteArrayOutputStream
import java.util.Properties

plugins {
    id("java")
}

group = "io.github.divinerealms.footcube"

val versionFile = file("version.properties")
val props = Properties().apply {
    if (versionFile.exists()) {
        load(versionFile.inputStream())
    } else {
        setProperty("major", "1")
        setProperty("minor", "0")
        setProperty("patch", "0")
    }
}

val major = props.getProperty("major").toInt()
val minor = props.getProperty("minor").toInt()
val patch = props.getProperty("patch").toInt()
val versionBase = "$major.$minor.$patch"

fun gitCommitHash(): String? = try {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        standardOutput = stdout
    }
    stdout.toString().trim()
} catch (_: Exception) {
    null
}

fun hasUncommittedChanges(): Boolean = try {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", "status", "--porcelain")
        standardOutput = stdout
    }
    stdout.toString().isNotBlank()
} catch (_: Exception) {
    false
}

val commit = gitCommitHash() ?: "unknown"
val dirty = hasUncommittedChanges()

version = if (dirty) "$versionBase-$commit-DEV"
else "$versionBase-$commit"

val jarLabel = if (dirty) "FootCube-$versionBase-$commit-DEV.jar"
else "FootCube-$versionBase-$commit.jar"

tasks.named("build") {
    doFirst {
        println("Building FootCube version: $version")
        println("Repository state: ${if (dirty) "UNCOMMITTED CHANGES" else "CLEAN"}")
        println("Output JAR: $jarLabel")
    }
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version.toString())
    }
}

tasks.jar {
    archiveFileName.set(jarLabel)
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://oss.sonatype.org/content/repositories/central")
    maven("https://repo.codemc.io/repository/nms")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("org.spigotmc:spigot:1.8.8-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("org.projectlombok:lombok:1.18.38")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.github.NEZNAMY:TAB-API:5.3.2")

    annotationProcessor("org.projectlombok:lombok:1.18.38")

    testCompileOnly("org.projectlombok:lombok:1.18.38")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.38")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
