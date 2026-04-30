# Spawners 模块

用于在玩家附近定时生成 MythicMobs 怪物。

## 主文档

- 仓库根目录 `README.md`

## 目录

- 主配置：`src/main/resources/feature/spawners/spawners.yml`
- 刷怪定义：`src/main/resources/feature/spawners/spawners/*.yml`
- 代码目录：`src/main/kotlin/com/pixlehavencore/feature/spawners/`

## 配置字段

- `enabled`
  - 主配置模块开关，位于 `spawners.yml`。
- `<顶层条目名>`
  - 每个 YAML 顶层 section 代表一个刷怪定义，条目名默认作为刷怪器 ID。
- `Enable`
  - 单条刷怪定义开关。
- `Type`
  - MythicMobs 内部怪物 ID。
- `Level`
  - 可写单整数，如 `1`。
  - 也可写权重列表，如 `- 1 0.1`、`- 2 0.2`。
- `Chance`
  - 每个周期是否触发刷怪，范围 `0.0 ~ 1.0`。
- `Worlds`
  - 生效世界列表，留空表示所有世界。
- `Interval`
  - 刷怪间隔，单位秒，内部自动换算为 tick。
- `SpawnAmount`
  - 每次触发生成数量。
- `Distance`
  - 生成半径，同时作为延迟清理的判定距离。
- `MaxAmount`
  - 在 `2 * Distance` 范围内，此刷怪器生成实体的最大数量。
- `Delay`
  - 延迟多少 tick 后开始执行距离清理。
- `Priority`
  - 数值越大，排序越靠前。
- `RemoveWhenFarAway`
  - 是否交给原版远距离清理。

## 指令

- `/spawners reload`
  - 重载刷怪配置
  - 权限：`phcore.spawners.admin`

## 线程模型

- 周期调度使用全局任务。
- 实际刷怪和延迟清理分别切到目标位置 / 实体区域线程执行。
