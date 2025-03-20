plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    id("org.jetbrains.intellij") version "1.15.0"
}

group = "com.example"
version = "1.2-SNAPSHOT"

repositories {
    maven {
        url = uri("https://maven.aliyun.com/repository/public")
    }
}

dependencies {
    // 添加 Jettison 和 JSON 库依赖
    implementation("org.codehaus.jettison:jettison:1.5.3")
    implementation("org.json:json:20231013")
}

// Configure Gradle IntelliJ Plugin
intellij {
    version.set("2024.1.1")      // PyCharm 版本（确保存在）
    type.set("PC")               // 指定为 PyCharm
    sandboxDir.set(project.rootDir.canonicalPath + "/.sandbox")

}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("203")
        untilBuild.set("241.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
