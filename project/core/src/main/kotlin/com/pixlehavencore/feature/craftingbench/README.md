# CraftingBench 模块

制作台模块的 MVP 版本，提供 CraftEngine 工作台映射、配方浏览、基础排队制作与产物发放。

## 当前范围

- CraftEngine 自定义方块 / 家具交互打开制作界面。
- 主配置工作台等级映射。
- 配方目录扫描与权限解锁。
- 基础队列、取消、在线/离线待领取发放。
- `/craftingbench reload|queue|cancel`。

## 配置路径

- 主配置：`src/main/resources/feature/crafting-bench/config.yml`
- 配方目录：`src/main/resources/feature/crafting-bench/recipes/*.yml`

## 指令

- `/craftingbench queue`
- `/craftingbench cancel <id>`
- `/craftingbench reload`

## 权限

- `phcore.craftingbench.admin`
- `craft.recipe.<id>`
- `craft.bench.<tier>` 由配置自定义

## 线程模型

- 配置与配方加载走普通初始化流程。
- 队列推进使用轻量周期任务。
- 玩家背包发放切回玩家实体调度执行。

## 后续扩展

- 数据库存储与离线恢复。
- 更完整的分类 GUI、分页和数量选择。
- 团队加速、环境修正、拆解与公开工作台。
