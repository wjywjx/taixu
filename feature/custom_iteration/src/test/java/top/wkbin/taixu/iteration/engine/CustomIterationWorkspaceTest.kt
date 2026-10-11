package top.wkbin.taixu.iteration.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * CustomIterationWorkspace 的纯 JVM 单元测试。
 *
 * 覆盖自定义迭代工作区中最容易被写错的两件事：
 * 1. 目录布局必须稳定落在 `~/custom_taixu` 与 `~/.taixu/` 下的技能目录、模板目录，
 *    且互不嵌套覆盖；
 * 2. 落盘必须幂等，重复自举不得破坏已部署内容。
 *
 * 另加三条契约测试：assets 侧声明的资产路径必须真实存在、Skill 的 frontmatter 名称必须与
 * 部署目录同名、工作流模板必须与 `.github/workflows` 下的权威版本同步（模板滞后会让用户
 * 拿到已经失效的 CI 配置）。
 */
class CustomIterationWorkspaceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val rootfsHome: File get() = tempFolder.root

    // ---------- 目录布局 ----------

    @Test
    fun layoutPutsWorkspaceAndDeployTargetsUnderRootfsHome() {
        val layout = CustomIterationWorkspace.layoutOf(rootfsHome)

        assertEquals(
            File(rootfsHome, "custom_taixu").absolutePath,
            layout.workspaceDir.absolutePath,
        )
        assertEquals(
            File(rootfsHome, ".taixu/skills/taixu-custom-iteration/SKILL.md").absolutePath,
            layout.skillFile.absolutePath,
        )
        assertEquals(
            File(rootfsHome, ".taixu/templates/workflows/taixudev-build.yml").absolutePath,
            layout.workflowTemplateFile.absolutePath,
        )
    }

    @Test
    fun workspaceDirDoesNotOverlapDeployTargets() {
        val layout = CustomIterationWorkspace.layoutOf(rootfsHome)
        val skillDir = requireNotNull(layout.skillFile.parentFile).absolutePath

        // 源码工作区与部署目录必须彼此独立，否则一次写入会覆盖另一次。
        assertFalse(layout.workspaceDir.absolutePath.startsWith(skillDir))
        assertFalse(layout.skillFile.absolutePath.startsWith(layout.workspaceDir.absolutePath))
    }

    // ---------- 目录骨架 ----------

    @Test
    fun ensureDirectoriesCreatesNestedSkeleton() {
        val layout = CustomIterationWorkspace.layoutOf(rootfsHome)

        CustomIterationWorkspace.ensureDirectories(layout)

        assertTrue(layout.workspaceDir.isDirectory)
        assertTrue(requireNotNull(layout.skillFile.parentFile).isDirectory)
        assertTrue(requireNotNull(layout.workflowTemplateFile.parentFile).isDirectory)
    }

    @Test
    fun ensureDirectoriesKeepsAlreadyDeployedContent() {
        val layout = CustomIterationWorkspace.layoutOf(rootfsHome)
        CustomIterationWorkspace.ensureDirectories(layout)
        layout.skillFile.writeText("# 已存在的规范")

        CustomIterationWorkspace.ensureDirectories(layout)

        assertEquals("# 已存在的规范", layout.skillFile.readText())
    }

    // ---------- 幂等落盘 ----------

    @Test
    fun writeIfChangedWritesNewFile() {
        val target = File(rootfsHome, "a/b/notes.md")

        val written = CustomIterationWorkspace.writeIfChanged(target, "hello")

        assertTrue(written)
        assertEquals("hello", target.readText())
    }

    @Test
    fun writeIfChangedSkipsIdenticalContent() {
        val target = File(rootfsHome, "notes.md")
        CustomIterationWorkspace.writeIfChanged(target, "hello")
        val stampBefore = target.lastModified()

        val written = CustomIterationWorkspace.writeIfChanged(target, "hello")

        assertFalse(written)
        assertEquals("hello", target.readText())
        assertEquals(stampBefore, target.lastModified())
    }

    @Test
    fun writeIfChangedOverwritesStaleContent() {
        val target = File(rootfsHome, "notes.md")
        CustomIterationWorkspace.writeIfChanged(target, "旧内容")

        val written = CustomIterationWorkspace.writeIfChanged(target, "新内容")

        assertTrue(written)
        assertEquals("新内容", target.readText())
    }

    @Test
    fun writeIfChangedSkipsMissingOrBlankContent() {
        val target = File(rootfsHome, "missing.md")

        assertFalse(CustomIterationWorkspace.writeIfChanged(target, null))
        assertFalse(CustomIterationWorkspace.writeIfChanged(target, "   \n  "))
        // 资产缺失时不得留下一份空文件冒充「已部署」。
        assertFalse(target.exists())
    }

    // ---------- assets 契约 ----------

    @Test
    fun declaredSkillAssetPathResolvesToSourceFile() {
        assertTrue(
            "Skill 资产缺失：${skillAsset.path}，与 CustomIterationWorkspace.SKILL_ASSET_PATH 声明不一致",
            skillAsset.isFile,
        )
    }

    @Test
    fun declaredWorkflowTemplateAssetPathResolvesToSourceFile() {
        assertTrue(
            "工作流模板资产缺失：${workflowTemplateAsset.path}，" +
                "与 CustomIterationWorkspace.WORKFLOW_TEMPLATE_ASSET_PATH 声明不一致",
            workflowTemplateAsset.isFile,
        )
    }

    @Test
    fun skillFrontmatterNameMatchesDeployedDirectory() {
        val declaredName = skillAsset.readLines()
            .drop(1)
            .takeWhile { it.trim() != "---" }
            .firstOrNull { it.startsWith("name:") }
            ?.removePrefix("name:")
            ?.trim()

        assertEquals(
            "SKILL.md 的 frontmatter name 必须与部署目录同名，否则技能注册表无法按名解析",
            CustomIterationWorkspace.SKILL_DIR_RELATIVE_PATH.substringAfterLast('/'),
            declaredName,
        )
    }

    @Test
    fun workflowTemplateStaysInSyncWithCanonicalWorkflow() {
        val canonical = File(
            repoRoot,
            ".github/workflows/${CustomIterationWorkspace.WORKFLOW_FILE_NAME}",
        )

        assertEquals(
            "部署给用户的工作流模板必须与 .github/workflows 下的权威版本逐字一致；" +
                "模板滞后会让用户拿到已经失效的 CI 配置（如已下线的 setup-android tools 包）",
            canonical.readText(),
            workflowTemplateAsset.readText(),
        )
    }

    private val skillAsset: File
        get() = File(
            repoRoot,
            "feature/custom_iteration/src/main/assets/${CustomIterationWorkspace.SKILL_ASSET_PATH}",
        )

    private val workflowTemplateAsset: File
        get() = File(
            repoRoot,
            "app/src/main/assets/${CustomIterationWorkspace.WORKFLOW_TEMPLATE_ASSET_PATH}",
        )

    private companion object {
        /** 单元测试的工作目录默认是模块目录，向上回溯定位仓库根。 */
        val repoRoot: File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("无法向上定位仓库根目录（未找到 settings.gradle.kts）")
    }
}
