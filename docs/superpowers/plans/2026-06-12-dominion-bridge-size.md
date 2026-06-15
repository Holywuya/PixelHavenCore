# DominionBridge 领地大小查询扩展实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 DominionBridge 中新增领地大小查询功能——数据类、扩展函数、便捷方法。

**架构：** 在 `DominionBridge.kt` 中新增 `DominionSizeInfo` 数据类和两个方法，利用 `CuboidDTO` 已有 API 获取尺寸信息。不修改原有代码。

**技术栈：** Kotlin、DominionAPI (CuboidDTO)

---

### 任务 1：实现领地大小查询功能

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/util/DominionBridge.kt`

- [ ] **步骤 1：添加 import**

在 `import cn.lunadeer.dominion.api.dtos.flag.Flags` 之后添加：

```kotlin
import cn.lunadeer.dominion.api.dtos.DominionDTO
```

- [ ] **步骤 2：在 `canFlyAt` 方法之后、类结束 `}` 之前添加数据类和扩展方法**

```kotlin
data class DominionSizeInfo(
    val xLength: Long,
    val yLength: Long,
    val zLength: Long,
    val squareArea: Long,
    val volume: Long,
)

fun DominionDTO.toSizeInfo(): DominionSizeInfo {
    val cuboid = cuboid
    return DominionSizeInfo(
        xLength = cuboid.xLength(),
        yLength = cuboid.yLength(),
        zLength = cuboid.zLength(),
        squareArea = cuboid.square,
        volume = cuboid.volume,
    )
}

fun getDominionSizeAt(location: Location): DominionSizeInfo? {
    val dominionAPI = api ?: return null
    val dominion = dominionAPI.getDominion(location) ?: return null
    return dominion.toSizeInfo()
}
```

- [ ] **步骤 3：确认文件结构正确**

文件最终结构：
```
object DominionBridge {
    // 原有：PLUGIN_NAME, BYPASS_PERMISSION, api, isAvailable(), canFlyAt()
    
    // 新增：DominionSizeInfo, toSizeInfo(), getDominionSizeAt()
}
```

---

### 任务 2：构建验证并提交

- [ ] **步骤 1：构建项目**

```bash
./gradlew build
```

预期：`BUILD SUCCESSFUL`

- [ ] **步骤 2：提交**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/util/DominionBridge.kt
git commit -m "feat(dominion): 添加领地大小查询功能

- 新增 DominionSizeInfo 数据类，包装五维度尺寸信息
- 新增 DominionDTO.toSizeInfo() 扩展函数
- 新增 getDominionSizeAt(Location) 便捷方法"
```
