# PixelHavenCore

一个功能丰富的Minecraft服务器插件，基于TabooLib框架开发。

## 功能特性

- **矿物连锁 (Veinminer)** - 支持连锁挖掘矿物
- **视距控制 (View Distance Controller)** - 动态调整玩家视距
- **隐身系统 (Vanish System)** - 完整的玩家隐身功能
- **服务器通知 (Server Notifications)** - 自动服务器广播
- **聊天功能 (Chat Features)** - 增强的聊天体验
- **砂轮修复 (Grindstone Repair)** - 工具修复功能
- **死亡掉落惩罚 (Death Drop)** - 危险世界死亡惩罚
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

## 开发信息

- **框架**: TabooLib 2.0.27
- **语言**: Kotlin 2.2.0
- **JDK**: 17 (构建时), JVM 1.8 (运行时)
- **构建工具**: Gradle

## 许可证

本项目采用 GNU General Public License v3.0 许可证。
