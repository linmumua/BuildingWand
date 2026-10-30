import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.File
import io.izzel.taboolib.gradle.Basic
import io.izzel.taboolib.gradle.Bukkit
import io.izzel.taboolib.gradle.BukkitHook
import io.izzel.taboolib.gradle.BukkitUtil
import io.izzel.taboolib.gradle.CommandHelper

plugins {
    java
    id("io.izzel.taboolib") version "2.0.38"
    // TabooLib 的 Gradle 插件在配置阶段引用 Kotlin 插件类，即使本项目是纯 Java 也必须让它出现在 classpath 上。
    kotlin("jvm") version "2.2.0"
}

group = "cn.linmumua.buildingwand"
version = "1.0.0"

taboolib {
    env {
        // 云载：这些模块不打进 jar，由 TabooLib 运行时按需下载到服务端 libraries 缓存
        install(Basic, Bukkit, BukkitHook, BukkitUtil, CommandHelper)
    }
    description {
        name = "BuildingWand"
        desc("建筑之杖 —— ConstructionWand 服务端移植")
        // 需要在世界可用后注册配方与物品
        load("POSTWORLD")
        bukkitApi("1.21")
        dependencies {
            // CraftEngine 是硬前置：物品、模型、贴图全部由 CE 资源包提供
            name("CraftEngine")
        }
    }
    version {
        taboolib = "6.3.0-b4bf495"
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.tabooproject.org/repository/releases/")
}

// TabooLib 的 Gradle 插件会追加 repo.spongepowered.org，本机 DNS 解析不到该域名，
// 任何它先命中的坐标都会让整个构建失败。这里在仓库刚被加入时就移除它。
repositories.whenObjectAdded {
    if (this is MavenArtifactRepository && url.host == "repo.spongepowered.org") {
        repositories.remove(this)
    }
}

// 【为什么单独开一个配置装测试期依赖】
// testRuntimeClasspath 会被 TabooLib 插件填进几十个 taboolib-* jar，classpath 变得很长；
// Windows 下 Gradle 这时会改用 argfile 传递，而本项目位于中文路径，argfile 里的非 ASCII
// 路径会被按非 UTF-8 解码，运行期就变成 ClassNotFoundException（类文件其实一直在）。
// 算法层测试不依赖 TabooLib，所以给测试一套极短的独立 classpath。
val pureTestRuntime: Configuration by configurations.creating

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // CraftEngine 未发布标准 Maven 坐标：编译期用本地 jar，运行期由前置插件提供。
    compileOnly(files("libs/craft-engine-paper-plugin-26.10-SNAPSHOT.jar"))
    // 服务端自带 snakeyaml（Bukkit 的 YamlConfiguration 就依赖它），编译期声明即可。
    compileOnly("org.yaml:snakeyaml:2.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    // 契约测试要编译期就能看到 SnakeYAML 的 API
    testImplementation("org.yaml:snakeyaml:2.2")
    pureTestRuntime("org.junit.jupiter:junit-jupiter:5.11.4")
    pureTestRuntime("org.junit.platform:junit-platform-launcher:1.11.4")
    // 契约测试要真正解析资源包里的 YAML，用与服务端同一个解析器
    pureTestRuntime("org.yaml:snakeyaml:2.2")
}

// 【为什么必须显式重建 test 的 classpath，而且必须放在 afterEvaluate】
// TabooLib 的 Gradle 插件会在配置阶段末尾接管主 sourceSet 的产物目录，
// 于是默认装配的 test classpath 里不含本项目的 classes 输出，运行期直接
// ClassNotFoundException: ...WandActionTest（类文件确实存在，只是不在 classpath 上）。
// 放在 afterEvaluate 里才能保证我们是最后一个写入 classpath 的人。
// 【为什么测试要跑在 ASCII 暂存目录里】
// 本项目位于中文路径下。Gradle 的 test worker 在解析 classpath 时会丢掉非 ASCII 的目录项，
// 表现为 ClassNotFoundException —— 类文件确实存在，直接用 java -cp 也能加载。
// 已实测验证：把构建目录整体挪到 ASCII 路径后测试立即通过。
// 因此这里只把「跑测试所需的 classes」同步一份到系统临时目录（ASCII），
// 产物仍然留在 build/libs，不影响部署流程。
val asciiTestRoot = File(System.getProperty("java.io.tmpdir"), "buildingwand-test-classes")

val stageTestClasses = tasks.register<Sync>("stageTestClasses") {
    from(sourceSets["main"].output.classesDirs) { into("main") }
    from(sourceSets["test"].output.classesDirs) { into("test") }
    // 契约测试要从 classpath 读资源包里的 YAML 与模型文件，所以资源也要一起暂存
    sourceSets["main"].output.resourcesDir?.let { resourcesDir ->
        from(resourcesDir) { into("resources") }
    }
    into(asciiTestRoot)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    dependsOn(stageTestClasses)
    doFirst {
        // doFirst 保证这是最后写入 classpath 的时机（TabooLib 插件在配置阶段末尾会改写它）
        testClassesDirs = files(File(asciiTestRoot, "test"))
        classpath = files(
            File(asciiTestRoot, "test"),
            File(asciiTestRoot, "main"),
            File(asciiTestRoot, "resources")
        ) + pureTestRuntime
    }
}

// 纯 Java 插件不需要 Kotlin 标准库进入 classpath 或产物。
configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// 资源包校验：贴图尺寸、UV 空间、包围盒、贴图引用与物品定义必须互相对应。
// 这类错位在游戏里只表现为「贴图糊了/错位」，肉眼很难第一时间定位，所以在构建期挡掉。
// Python 或 Pillow 缺失时只警告一次并跳过，避免在没装 Python 的机器上把构建彻底堵死。
val verifyCePack = tasks.register<Exec>("verifyCePack") {
    group = "verification"
    description = "校验 CraftEngine 资源包的贴图尺寸、UV 与模型引用"
    workingDir = projectDir
    commandLine("python", "tools/verify_ce_pack.py")
    isIgnoreExitValue = false
    onlyIf {
        val ok = try {
            providers.exec {
                commandLine("python", "-c", "import PIL")
            }.result.get().exitValue == 0
        } catch (e: Exception) {
            false
        }
        if (!ok) {
            logger.warn("[buildingwand] 未找到 python 或 Pillow，跳过资源包校验。")
        }
        ok
    }
}

tasks.named("build") {
    dependsOn(verifyCePack)
}


// 【为什么必须在 taboolibMainTask 之后剔除 plugin.yml】
// TabooLib 的 Gradle 插件在 jar 任务【之后】才运行 taboolibMainTask，由它重新打包产物
// 并注入一份 plugin.yml（main 指向 TabooLib 自己的包装类）。因此给 jar 任务加 exclude
// 是无效的——文件是后一步写进去的。
// 本插件改用 paper-plugin.yml 直接声明 main（原因见该文件顶部注释）。两个描述文件同时
// 存在时，Bukkit 的旧加载路径仍可能按 plugin.yml 走，于是又回到「onEnable 不被回调」。
// 这里在 taboolibMainTask 收尾时重写产物，只去掉 plugin.yml，其余内容原样保留。
afterEvaluate {
    tasks.matching { it.name == "taboolibMainTask" }.configureEach {
        doLast {
            val jarFile = layout.buildDirectory.dir("libs").get().asFile
                .listFiles { f -> f.name.startsWith("BuildingWand-") && f.extension == "jar" }
                ?.maxByOrNull { it.lastModified() }
                ?: return@doLast
            val tmp = File(jarFile.parentFile, jarFile.name + ".tmp")
            val zip = ZipFile(jarFile)
            try {
                val out = ZipOutputStream(tmp.outputStream())
                try {
                    for (entry in zip.entries()) {
                        if (entry.name == "plugin.yml") continue
                        out.putNextEntry(ZipEntry(entry.name))
                        if (!entry.isDirectory) {
                            val input = zip.getInputStream(entry)
                            try {
                                input.copyTo(out)
                            } finally {
                                input.close()
                            }
                        }
                        out.closeEntry()
                    }
                } finally {
                    out.close()
                }
            } finally {
                zip.close()
            }
            if (!tmp.renameTo(jarFile)) {
                tmp.copyTo(jarFile, overwrite = true)
                tmp.delete()
            }
            logger.lifecycle("[buildingwand] 已从产物中移除 TabooLib 生成的 plugin.yml，仅保留 paper-plugin.yml")
        }
    }
}
