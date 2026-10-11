package top.wkbin.taixu.iteration.engine

import android.content.Context
import java.io.File

/**
 * CustomIterationBootstrap — 太墟自定义迭代环境自举引擎。
 * 
 * 职责：
 * 1. 准备沙盒内隔离的源码工作区路径 `~/custom_taixu`；
 * 2. 从 APK assets 读取预置的 `taixu-custom-iteration` Skill，并落盘到 Agent 的技能目录；
 * 3. 从 APK assets 读取 GitHub Actions CI 工作流模板并落盘；
 * 4. 生成引导 Agent 执行自迭代开发的规范提示词。
 *
 * 目录布局与幂等写入由 [CustomIterationWorkspace] 承担（纯 JVM、可单测），
 * 本对象只保留「读 assets + 编排 + 错误语义」这一层薄逻辑。
 */
object CustomIterationBootstrap {

    /** 隔离工作区目录名，与 [CustomIterationWorkspace] 共用同一事实源。 */
    const val WORKSPACE_NAME = CustomIterationWorkspace.WORKSPACE_NAME

    const val OFFICIAL_REPO = "https://github.com/wkbin/taixu"

    const val BOOTSTRAP_PROMPT = """我准备在太墟（TaiXu）的手机 Linux 虚拟沙盒中进行 TaiXu 自定义迭代。

请按以下步骤引导我：
1. 检查本地开发环境：
   - 确认当前命令在 Linux PRoot 沙盒中执行；
   - 确认独立工作区为 ~/custom_taixu；
   - 优先使用 GitHub Actions 构建 TaiXuDev APK，无需在手机本地安装庞大的 Android SDK/NDK。

2. 检查并配置 GitHub 认证：
   - 检查 gh CLI 与 Git 配置；
   - 引导我使用 gh auth login（设备码流程）或 SSH Key 完成登录验证；
   - 不要把 Token、私钥写入命令历史或日志中。

3. 验证与克隆仓库：
   - 为官方仓库 $OFFICIAL_REPO 点星；
   - Fork main 分支到我自己的 GitHub 账户；
   - 将 Fork 后的仓库克隆到 ~/custom_taixu。

4. 遵循 taixu-custom-iteration Skill 开发规范：
   - 按照太墟的 Jetpack Compose、Koin 和多模块规范进行修改；
   - 编写或调整功能后运行单元测试验证；
   - 提交修改并推送到 Fork 仓库的特性分支。

5. 通过 GitHub Actions 构建独立的 TaiXuDev APK：
   - 触发 .github/workflows/taixudev-build.yml 编译；
   - 实时监控构建进度并在成功后下载 APK 至手机；
   - 校验包名 top.wkbin.taixu.dev 和应用名 TaiXuDev，与正式版独立共存。

6. 若体验满意，协助我生成标准 PR 提交到 $OFFICIAL_REPO。"""

    /**
     * 初始化自定义迭代环境与工作区。
     *
     * 不只是建目录：预置 Skill 与 CI 工作流模板必须真正写入，否则 Agent 拿到的是一个
     * 「看起来就绪、实际无规范可读」的空壳工作区。
     */
    fun bootstrap(context: Context, rootfsHomeDir: File): BootstrapResult = try {
        val layout = CustomIterationWorkspace.layoutOf(rootfsHomeDir)
        CustomIterationWorkspace.ensureDirectories(layout)

        val skillMarkdown = readAssetOrNull(context, CustomIterationWorkspace.SKILL_ASSET_PATH)
        val workflowTemplate =
            readAssetOrNull(context, CustomIterationWorkspace.WORKFLOW_TEMPLATE_ASSET_PATH)

        val missingAssets = buildList {
            if (skillMarkdown == null) add(CustomIterationWorkspace.SKILL_ASSET_PATH)
            if (workflowTemplate == null) add(CustomIterationWorkspace.WORKFLOW_TEMPLATE_ASSET_PATH)
        }

        if (missingAssets.isNotEmpty()) {
            // 资产缺失即部署失败：宁可直接报错，也不交付半成品工作区。
            BootstrapResult(
                success = false,
                workspacePath = layout.workspaceDir.absolutePath,
                prompt = "",
                errorMessage = "缺少预置资产：${missingAssets.joinToString("、")}"
            )
        } else {
            CustomIterationWorkspace.writeIfChanged(layout.skillFile, skillMarkdown)
            CustomIterationWorkspace.writeIfChanged(layout.workflowTemplateFile, workflowTemplate)
            BootstrapResult(
                success = true,
                workspacePath = layout.workspaceDir.absolutePath,
                prompt = BOOTSTRAP_PROMPT
            )
        }
    } catch (e: Exception) {
        BootstrapResult(
            success = false,
            workspacePath = "",
            prompt = "",
            errorMessage = e.message ?: "Bootstrap failed"
        )
    }

    /** 读取 APK assets 文本；资产缺失或读取异常返回 null，由调用方统一转成部署失败。 */
    private fun readAssetOrNull(context: Context, assetPath: String): String? =
        runCatching { context.assets.open(assetPath).bufferedReader().use { it.readText() } }
            .getOrNull()

    data class BootstrapResult(
        val success: Boolean,
        val workspacePath: String,
        val prompt: String,
        val errorMessage: String? = null
    )
}
