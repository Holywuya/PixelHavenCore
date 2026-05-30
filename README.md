# PixelHavenCore

一个功能丰富的Minecraft服务器插件，基于TabooLib框架开发。

## 功能特性

- **真实世界系统 (RealWorld)** - 完整的环境模拟系统
  - 四季循环（春/夏/秋/冬），影响温度和降水
  - 动态体温系统，受环境温度、护甲绝缘、热源辐射影响
  - 水分系统，受温度和活动影响
  - 骨折系统，跌倒或受到重击可能骨折
  - 区块级天气系统，不同区域天气独立
  - Per-player 天气同步，每个玩家看到基于自己位置的天气
  - 生存效果，极端温度/脱水导致负面状态
- **矿物连锁 (Veinminer)** - 支持连锁挖掘矿物
- **视距控制 (View Distance Controller)** - 动态调整玩家视距
- **隐身系统 (Vanish System)** - 完整的玩家隐身功能
- **服务器通知 (Server Notifications)** - 自动服务器广播
- **聊天功能 (Chat Features)** - 增强的聊天体验
- **砂轮修复 (Grindstone Repair)** - 工具修复功能
- **死亡掉落惩罚 (Death Drop)** - 危险世界死亡惩罚
- **刷怪器 (Spawners)** - 玩家附近 MythicMobs 定时生成
- **帮助指令拦截 (Help Interceptor)** - 自定义帮助系统
- **自动重启 (Auto Restart)** - 智能服务器重启管理
- **菜单系统 (Simple Menu)** - 便捷的GUI菜单

## 构建说明

### 本地构建

```bash
# 构建发行版本
./gradlew build

# 构建开发版本 (包含TabooLib本体)
./gradlew taboolibBuildApi -PDeleteCode
```

### 自动发布

项目配置了GitHub Actions自动构建和发布：

1. **自动构建**: 推送到master分支时自动构建和测试
2. **自动发布**: 创建版本标签时自动创建GitHub Release

#### 创建新版本

```bash
# 使用发布脚本 (推荐)
./release.sh 1.0.0

# 或手动操作
# 1. 更新 gradle.properties 中的版本号
# 2. 提交更改
# 3. 创建标签: git tag -a v1.0.0 -m "Release version 1.0.0"
# 4. 推送标签: git push origin v1.0.0
```

发布脚本会自动：
- 更新版本号
- 提交更改
- 创建Git标签
- 触发GitHub Actions自动构建和发布

## 安装使用

1. 下载最新的JAR文件从 [Releases](https://github.com/Holywuya/PixelHavenCore/releases)
2. 将JAR文件放入服务器的 `plugins/` 文件夹
3. 重启服务器
4. 根据需要配置插件 (见 `plugins/PixelHavenCore/` 目录)

## 配置说明

插件使用模块化配置，每个功能都有独立的配置文件：

- `settings.yml` - 全局设置
- `feature/` - 各功能模块配置
- `menus/` - 菜单配置

详细配置说明请参考各配置文件中的注释。

## PAPI 变量

### 真实世界系统 (`%phcorerw_*%`)

| 变量 | 说明 | 示例输出 |
|------|------|---------|
| `%phcorerw_season%` | 当前季节 | 春 |
| `%phcorerw_season_progress%` | 季节进度 | 45.2% |
| `%phcorerw_day_phase%` | 时间段 | 白天/黄昏/夜晚 |
| `%phcorerw_weather%` | 玩家位置天气 | 晴/雨 |
| `%phcorerw_temperature%` | 玩家体温（整数） | 25 |
| `%phcorerw_temperature_exact%` | 玩家体温（一位小数） | 25.3 |
| `%phcorerw_temperature_phase%` | 体温阶段 | 舒适/过热/寒冷... |
| `%phcorerw_biome_temperature%` | 群系基础温度 | 15.0 |
| `%phcorerw_near_heat_source%` | 最近热源 | CAMPFIRE/none |
| `%phcorerw_hydration%` | 玩家水分（整数） | 80 |
| `%phcorerw_hydration_phase%` | 水分阶段 | 充足/口渴/脱水 |
| `%phcorerw_wetness%` | 潮湿度 | 35% |
| `%phcorerw_shelter%` | 遮蔽类型 | 无遮蔽/树荫/建筑 |
| `%phcorerw_is_sheltered%` | 是否被遮蔽 | true/false |
| `%phcorerw_fracture%` | 骨折值（整数） | 30 |
| `%phcorerw_fracture_severity%` | 骨折严重程度 | 无骨折/轻微/中度/严重 |
| `%phcorerw_is_raining%` | 玩家位置是否下雨 | true/false |
| `%phcorerw_is_comfortable%` | 体温是否舒适 | true/false |
| `%phcorerw_is_thirsty%` | 是否口渴 | true/false |
| `%phcorerw_is_injured%` | 是否骨折 | true/false |

### 经济系统 (`%phcoreeco_*%`)

| 变量 | 说明 |
|------|------|
| `%phcoreeco_balance%` | 玩家余额 |
| `%phcoreeco_balance_formatted%` | 格式化余额 |

### 称号系统 (`%phcoretitle_*%`)

| 变量 | 说明 |
|------|------|
| `%phcoretitle_prefix%` | 玩家前缀 |
| `%phcoretitle_suffix%` | 玩家后缀 |

### 游戏时长 (`%phcoreplaytime_*%`)

| 变量 | 说明 |
|------|------|
| `%phcoreplaytime_total%` | 总游戏时长 |
| `%phcoreplaytime_today%` | 今日游戏时长 |
| `%phcoreplaytime_session%` | 本次会话时长 |

## 命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/phcore reload` | `phcore.admin` | 重载所有配置 |
| `/rw status` | `phcore.admin` | 查看真实世界状态 |
| `/rw player <name>` | `phcore.admin` | 查看玩家环境数据 |
| `/rw reset <player>` | `phcore.admin` | 重置玩家状态 |
| `/rw set season <season>` | `phcore.admin` | 强制设置季节 |
| `/rw set weather <weather>` | `phcore.admin` | 强制设置天气 |

## 模块文档

- `src/main/kotlin/com/pixlehavencore/feature/spawners/README.md`
  - 玩家附近 MythicMobs 刷怪模块说明

## 开发信息

- **框架**: TabooLib 6.3.0
- **语言**: Kotlin 2.2.0 + Java
- **JVM**: 21
- **服务器**: Paper 1.21.11
- **构建工具**: Gradle Kotlin DSL

## 许可证

本项目采用 GNU General Public License v3.0 许可证。
