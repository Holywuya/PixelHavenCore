# Optimization 模块组

性能优化模块集合，包含实体清理、自然生成削减与动态视距控制。

## 子模块

- `viewdistance`
  - 动态视距、AFK 视距、性能与网络相关调节

- `entityclearer`
  - 周期性实体清理（掉落物/怪物）

- `spawnreducer`
  - 自然生成实体削减（按生成原因）

## 配置文件

- `feature/optimization/view-distance-controller.yml`
- `feature/optimization/entity-clearer.yml`
- `feature/optimization/spawn-reducer.yml`

## 说明

- 各子模块均有独立 `Settings/Service/Command/Listener`。
- 通过 `/phc reload` 可统一重载。
