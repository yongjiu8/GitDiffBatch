plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.8.0"
}

group = "com.teixing"
version = "1.0.2-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2021.3.3")
    type.set("IC") // Target IDE Platform
    plugins.set(listOf("Git4Idea"))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }

    patchPluginXml {
        sinceBuild.set("203")
        untilBuild.set("222.*")
        changeNotes.set("增加右边对比版本支持Annotate with Git Blame功能，增加设置Settings/Preferences > Tools > GitDiffBatch 里切换右侧对比目标：比对选中的提交或选中分支的最新提交")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

    compileJava {
        options.encoding = "UTF-8"
    }

    runIde {
        // 运行IDEA用于插件测试
    }
}
