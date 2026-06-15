# DominionBridge 领地大小查询扩展设计

## 日期

2026-06-12

## 问题

`DominionBridge` 当前仅封装了 `canFlyAt()` 方法（飞行权限检查），缺少领地大小查询能力。`CuboidDTO` 已提供 `xLength()`/`yLength()`/`zLength()`/`getSquare()`/`getVolume()` 等尺寸 API，但未在 Bridge 中暴露。

## 方案

在 `DominionBridge` 中新增领地大小查询功能：

1. 新增 `DominionSizeInfo` 数据类，包装五维度尺寸信息
2. 新增 `DominionDTO.toSizeInfo()` 扩展函数
3. 新增 `getDominionSizeAt(Location)` 便捷方法

### 改动文件

- `project/core/src/main/kotlin/com/pixlehavencore/util/DominionBridge.kt`

### 数据结构

```kotlin
data class DominionSizeInfo(
    val xLength: Long,
    val yLength: Long,
    val zLength: Long,
    val squareArea: Long,   // 底面积 xLength * zLength
    val volume: Long,       // 体积 xLength * yLength * zLength
)
```

### API

| 方法 | 说明 |
|------|------|
| `DominionDTO.toSizeInfo()` | 从 DominionDTO 对象获取尺寸信息 |
| `DominionBridge.getDominionSizeAt(Location)` | 获取指定位置的领地尺寸，无领地时返回 null |

### 调用示例

```kotlin
val size = DominionBridge.getDominionSizeAt(player.location)
// 或
val dominion = DominionBridge.getDominionAt(location)  // 未来可能添加
val size = dominion.toSizeInfo()
```
