# 领地税设计文档

## 日期

2026-06-12

## 问题

税收模块当前仅有收入税（交易收益抽税），缺少领地持有成本机制。需要新增基于领地大小的周期性税收，和现有收入税共享结税周期。

## 方案

在税收系统中新增领地税阶段，与收入税在同一 `settleNow()` 流程中结算。

### 架构

```
settleNow()
├── Phase 1: 收入税结算（现有，不变）
└── Phase 2: 领地税结算（新增）
    ├── 调用 DominionBridge 获取所有领地
    ├── 对每个领地：toSizeInfo() → 按配置选取税基(squareArea/volume)
    ├── 通过税档计算领地税
    ├── 从领主账户 collectTaxFromAccount() 扣款
    └── 余额不足 → 标记欠税
```

### 配置（tax.yml 新增）

```yaml
dominion:
  enabled: false
  tax-base: "square"            # square | volume
  tax-brackets:
    tier1:
      min: 0
      rate: 0.0
    tier2:
      min: 1000
      rate: 0.01
```

### 改动文件

| 文件 | 改动 |
|------|------|
| `TaxSettings.kt` | 新增 `dominionTaxEnabled`、`dominionTaxBase` 枚举、`dominionTaxBrackets`、`computeDominionTax()` |
| `TaxService.kt` | `settleNow()` 中在 Phase 1 之后插入 Phase 2 领地税结算 |
| `tax.yml` | 新增 `dominion:` 配置段 |
| `DominionBridge.kt` | 新增 `getAllDominions()` 便捷方法 |

### 领地税税档计算

沿用收入税的边际税率模式：领地尺寸按税档分拆计税，如 8000 m² 按 tier2 (1000-5000, 1%) + tier3 (5000+, 2%) 累进计算。

### 边界处理

- `dominion.enabled: false` → 跳过领地税结算
- Dominioin 插件未安装 → `DominionBridge.isAvailable() = false` → 跳过
- 领主离线 → `resolveOfflinePlayer()` 获取 OfflinePlayer，走 EconomyProvider 扣款
- 扣款不足 → 余额扣至 0，差额计入 `debt`
