package com.aicode.feature.git.domain.model

/**
 * Git 提交拓扑图领域模型。由 [com.aicode.feature.git.domain.GitRepository.graph] 解析
 * `git log`（带 `%P` 父哈希）与 `git for-each-ref` 输出，并在纯 Kotlin 中算出泳道布局，
 * 供 UI 用 Canvas 绘制 IDE 风格的分支彩色连线。纯数据，无 Android 依赖。
 */

/**
 * 一条提交记录（拓扑图用）。在 [GitCommit] 基础上增加 [parents]：父提交哈希列表，
 * 一个为普通提交、两个为合并提交、空为根提交。拓扑连线与泳道分配依赖此字段。
 */
data class GraphCommit(
    val hash: String,
    val shortHash: String,
    val author: String,
    val date: String,
    val message: String,
    /** 父提交完整哈希列表（按 git 输出顺序，第一父为主线）。 */
    val parents: List<String>
) {
    /** 是否为合并提交（≥2 个父提交）。用于绘制合并节点。 */
    val isMerge: Boolean get() = parents.size >= 2
}

/**
 * 指向某提交的一个引用（分支或标签）。用于在拓扑图节点旁标注分支名并对分支着色。
 */
data class GitGraphRef(
    val name: String,
    /** true=本地/远程分支，false=标签。 */
    val isBranch: Boolean,
    /** 是否为当前 HEAD 所在分支（仅本地分支可能为 true）。 */
    val isCurrent: Boolean,
    /** 是否为远程分支（refs/remotes 下）。 */
    val isRemote: Boolean
)

/**
 * 一条连线：从 [fromLane] 所在泳道连到 [toLane] 所在泳道，对应相邻两个提交之间的父子关系。
 * 同列即竖线，跨列即分叉/合并的折线。颜色由泳道号映射调色板，UI 渲染时查表。
 */
data class GraphEdge(
    val fromLane: Int,
    val toLane: Int,
    /** 该边所属泳道号（决定颜色）：出边（分叉）取 [fromLane]（发起分叉的提交所在列），
     *  入边（合并支线汇入）取 [toLane]（父支线沿用其分支主色）。 */
    val lane: Int,
    /** true=合并入边（父支线从下方汇入本提交，画下半段贝塞尔）；
     *  false=出边（本提交向父分叉，画下半段贝塞尔）。 */
    val isMergeIn: Boolean
)

/**
 * 拓扑图整体视图。
 *
 * @property commits 按时间从新到旧排列的提交列表。
 * @property refs 每个提交哈希 → 指向它的引用列表（分支/标签），用于节点旁标注。
 * @property lanes 每个提交哈希 → 其所在泳道列号（0 起）。
 * @property edges 相邻提交之间的所有连线（含分叉/合并的跨泳道边），扁平有序，按提交顺序生成。
 * @property activeLanes 每个提交哈希 → 该行需要贯穿竖线的活跃泳道列号列表（含本提交所在列）。
 *   非本提交所属但被某分支穿过的泳道也在此列，UI 据此画贯穿竖线避免断裂。
 * @property maxLane 最大泳道列号，决定 Canvas 宽度。
 * @property hasMore 是否还有更旧的提交可分页加载。首批加载与写操作刷新后按本批返回条数是否达到页大小判定；
 *   UI 滚到底据此决定是否触发下一页。历史末尾时为 false。
 */
data class GitGraph(
    val commits: List<GraphCommit>,
    val refs: Map<String, List<GitGraphRef>>,
    val lanes: Map<String, Int>,
    val edges: List<GraphEdge>,
    val activeTopLanes: Map<String, List<Int>>,
    val activeBottomLanes: Map<String, List<Int>>,
    val activeLanes: Map<String, List<Int>>,
    val maxLane: Int,
    val hasMore: Boolean = false
) {
    companion object {
        val EMPTY = GitGraph(emptyList(), emptyMap(), emptyMap(), emptyList(), emptyMap(), emptyMap(), emptyMap(), 0, false)
    }
}
