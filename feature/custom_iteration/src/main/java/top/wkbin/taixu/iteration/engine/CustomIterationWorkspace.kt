package top.wkbin.taixu.iteration.engine

import java.io.File

/**
 * CustomIterationWorkspace —— 自定义迭代工作区的目录布局与幂等落盘逻辑。
 *
 * 本对象刻意不引用任何 Android API，只操作 [java.io.File]：
 * 1. 让「目录骨架 + 内容落盘」这段最容易被写错的逻辑可以在纯 JVM 单元测试中直接验证；
 * 2. 使 [CustomIterationBootstrap] 退化为「读 assets → 交给本对象落盘」的薄编排层。
 *
 * 路径约定（相对 Linux rootfs 中的 home 目录，即 `~`）：
 * - 源码工作区：`~/custom_taixu`
 * - 预置 Skill：`~/.taixu/skills/taixu-custom-iteration/SKILL.md`
 * - CI 模板：`~/.taixu/templates/workflows/taixudev-build.yml`
 */
object CustomIterationWorkspace {

    /** 隔离工作区目录名，必须与 Skill 规范中的 `~/custom_taixu` 保持一致。 */
    const val WORKSPACE_NAME = "custom_taixu"

    /** 预置 Skill 的部署目录（相对 rootfs home）。 */
    const val SKILL_DIR_RELATIVE_PATH = ".taixu/skills/taixu-custom-iteration"

    /** CI 工作流模板的缓存目录（相对 rootfs home）。 */
    const val WORKFLOW_TEMPLATE_DIR_RELATIVE_PATH = ".taixu/templates/workflows"

    const val SKILL_FILE_NAME = "SKILL.md"

    const val WORKFLOW_FILE_NAME = "taixudev-build.yml"

    /** 预置 Skill 在 APK assets 根下的相对路径（feature 模块 assets 会并入 APK 根）。 */
    const val SKILL_ASSET_PATH = "skills/taixu-custom-iteration/$SKILL_FILE_NAME"

    /** CI 工作流模板在 APK assets 根下的相对路径（app 模块 assets 会并入 APK 根）。 */
    const val WORKFLOW_TEMPLATE_ASSET_PATH = "templates/workflows/$WORKFLOW_FILE_NAME"

    /**
     * 工作区各落点的绝对路径集合。
     */
    data class Layout(
        val workspaceDir: File,
        val skillFile: File,
        val workflowTemplateFile: File,
    )

    fun layoutOf(rootfsHomeDir: File): Layout = Layout(
        workspaceDir = File(rootfsHomeDir, WORKSPACE_NAME),
        skillFile = File(rootfsHomeDir, "$SKILL_DIR_RELATIVE_PATH/$SKILL_FILE_NAME"),
        workflowTemplateFile = File(
            rootfsHomeDir,
            "$WORKFLOW_TEMPLATE_DIR_RELATIVE_PATH/$WORKFLOW_FILE_NAME",
        ),
    )

    /**
     * 建立工作区目录骨架；已存在时保持原状（幂等）。
     *
     * 注意：仅建空目录不足以让 Agent 直接开工，[CustomIterationBootstrap] 还会把
     * Skill 正文与工作流模板写入其中。
     */
    fun ensureDirectories(layout: Layout) {
        layout.workspaceDir.mkdirs()
        layout.skillFile.parentFile?.mkdirs()
        layout.workflowTemplateFile.parentFile?.mkdirs()
    }

    /**
     * 幂等写入文本内容。
     *
     * - 内容为 null 或全为空白时跳过（视为资产缺失，由调用方决定是否视为失败）；
     * - 目标文件已存在且内容完全一致时不触碰磁盘，避免无谓的 mtime 变化。
     *
     * @return 本次是否真正写入了磁盘。
     */
    fun writeIfChanged(target: File, content: String?): Boolean {
        if (content.isNullOrBlank()) return false
        if (target.isFile && target.readText() == content) return false
        target.parentFile?.mkdirs()
        target.writeText(content)
        return true
    }
}
