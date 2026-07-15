package com.aicode.feature.git.domain

/**
 * git 命令以非零退出码结束（真实失败），区别于进程异常崩溃。携带 git 自身输出的文本（stderr 优先），
 * UI 据此把「成功」误报纠正为「失败 + 原因」。代表场景：未配置署名提交、未授权、合并冲突、无远程等。
 *
 * @param output git stdout+stderr 合并文本，作 [message] 供 UI 展示与日志排查。
 */
class GitCommandFailureException(val output: String) : Exception(output)
