plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    // 编译基线：1.21.6。
    // 选它而不是 26.3 的原因：本插件要兼容 1.21.x ~ 26.x，直接引用 26.3 的 API
    // 会让旧版服务端在链接期抛 NoSuchMethodError/NoClassDefFoundError。
    // 1.21.6 是「compose() 与 freeze() 两套注册表事件 API 并存」的最早版本，
    // 因此两条附魔注册路径都能通过编译（运行期再按服务端能力二选一）。
    compileOnly("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")
    // PlaceholderAPI 是硬依赖（plugin.yml 里 depend），但只在编译期需要它的接口，
    // 运行期由服务端提供，因此用 compileOnly，不会被打进插件 JAR。
    compileOnly("me.clip:placeholderapi:2.11.6")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    // 1.21.x 起服务端最低要求 Java 21；用 21 编译出的字节码在 25 的 JVM 上同样可运行，
    // 因此这是覆盖 1.21 ~ 26.x 的最低公共语言等级。
    options.release.set(21)
    options.encoding = "UTF-8"
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.3")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        // paper-plugin.yml 同样含 ${version} 占位符，两个描述符都要展开。
        filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
            expand(props)
        }
        // 许可与第三方声明随产物分发（CC BY 4.0 要求保留署名，AGPL 要求随附声明）。
        // 直接引用仓库根目录的文件，避免在 resources 里再存一份副本导致两边不一致。
        from("LICENSE") {
            into("META-INF")
            rename { "LICENSE.txt" }
        }
        from("THIRD_PARTY_NOTICES.md") {
            into("META-INF")
            rename { "THIRD_PARTY_NOTICES.txt" }
        }
    }
}
