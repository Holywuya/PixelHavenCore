# Economy 模块

多货币经济系统模块，基于 VaultUnlockedAPI 提供余额读写、转账、税务收益池与 Placeholder 支持。

## 功能概述

- **多货币账户**
  - 支持默认货币与多币种定义
  - 统一格式化显示（单复数、精度）

- **Vault 服务注册**
  - 启动时注册 `Economy` 服务供外部插件调用
  - 关闭时自动注销服务

- **央行双账户治理**
  - 默认货币启用 `C/D` 双账户模型
  - 外部插件通过 Vault 调用时同样会进入央行路由
  - 支持动态扩容、休眠资产回收与储备率观测

- **Vault 底层拦截**
  - 其他插件调用 Vault 发钱/扣费时，同样会进入本模块底层账务逻辑
  - 发钱进入玩家账户时自动累计到税务收益池
  - 扣费与税务结算统一回流中心银行 `C` 账户

- **税务子系统整合**
- 税务命令已并入 `/economy tax ...`
  - 配置仍保持独立文件，位于 `feature/economy/` 目录下
  - 统一维护收益池、待缴税额与欠税追踪

- **玩家余额命令**
  - 查询、转账、管理员增减设余额
  - 支持可选货币参数

- **PlaceholderAPI 扩展**
  - 提供余额、货币列表、币种元信息占位符

- **存储实现**
  - 使用 TabooLib PlayerDatabase 容器持久化玩家余额
  - 异步自动保存与脏数据刷新

## 指令

主命令：`/economy`（别名：`/eco`）

- `/economy`
  - 查看自己的默认货币余额

- `/economy pay <玩家> <金额> [货币]`
  - 向玩家转账，默认货币无需写币种

- `/economy balance <玩家> [货币]`
  - 查看玩家余额（管理员）

- `/economy add <玩家> <金额> [货币]`
  - 增加玩家余额（管理员，默认货币无需写币种）

- `/economy give <玩家> <金额>`
  - 通过中心银行发放金额（管理员）

- `/economy remove <玩家> <金额> [货币]`
  - 扣除玩家余额（管理员，默认货币无需写币种）

- `/economy set <玩家> <金额> [货币]`
  - 设置玩家余额（管理员，默认货币无需写币种）

- `/economy reload`
  - 重载整个经济系统配置（管理员）

- `/economy tax status`
  - 查看收益池与应缴税统计（管理员）

- `/economy tax settle`
  - 立即执行一次统一结税（管理员）

- `/economy tax reload`
  - 重载税务配置（管理员）

- `/economy cbank view`
  - 查看央行宏观状态（管理员）

- `/economy cbank inject <金额>`
  - 向中心银行 `C` 账户注资（管理员）

- `/economy cbank drain <金额>`
  - 从中心银行 `C` 账户缩表（管理员）

## PlaceholderAPI 变量

标识符：`phcoreeco`

- `%phcoreeco_balance%`
  - 默认货币余额

- `%phcoreeco_balance_raw%`
  - 默认货币纯数字余额

- `%phcoreeco_balance_<currency>%`
  - 指定货币余额

- `%phcoreeco_balance_<currency>_raw%`
  - 指定货币纯数字余额

- `%phcoreeco_default_currency%`
  - 默认货币 ID

- `%phcoreeco_currency_list%`
  - 货币列表文本

- `%phcoreeco_cbank_balance%`
  - 默认货币 C 账户余额（管理员）

- `%phcoreeco_cbank_balance_raw%`
  - 默认货币 C 账户纯数字余额（管理员）

- `%phcoreeco_cbank_reserve_rate%`
  - 默认货币储备率（管理员）

- `%phcoreeco_active_m0%`
  - 默认货币活跃流通量（管理员）

- `%phcoreeco_active_m0_raw%`
  - 默认货币活跃流通量纯数字（管理员）

- `%eco_cbank_balance_<currency>%`
  - 白皮书别名方式读取 C 账户余额（管理员）

- `%eco_cbank_balance_raw`
  - 白皮书别名方式读取 C 账户纯数字余额（管理员）

以上税务/央行占位符均返回纯数字字符串，不带单位后缀。

标识符：`phcoretax`

- `%phcoretax_enabled%`
  - 税务模块启用状态

- `%phcoretax_current_income%` / `%phcoretax_current_total_income%`
  - 当前玩家收益池总额

- `%phcoretax_current_income_raw%` / `%phcoretax_current_total_income_raw%`
  - 当前玩家收益池纯数字

- `%phcoretax_tax_due%` / `%phcoretax_pending_due_tax%`
  - 当前玩家待缴税额

- `%phcoretax_tax_due_raw%` / `%phcoretax_pending_due_tax_raw%`
  - 当前玩家待缴税纯数字

- `%phcoretax_tax_debt%` / `%phcoretax_pending_tax_debt%`
  - 当前玩家历史欠税额

- `%phcoretax_tax_debt_raw%` / `%phcoretax_pending_tax_debt_raw%`
  - 当前玩家历史欠税纯数字

- `%phcoretax_pending_income%` / `%phcoretax_total_income%`
  - 全服收益池总额

- `%phcoretax_pending_income_raw%` / `%phcoretax_total_income_raw%`
  - 全服收益池纯数字

- `%phcoretax_pending%` / `%phcoretax_pending_tax%`
  - 全服待缴税额总和

- `%phcoretax_pending_raw%` / `%phcoretax_pending_tax_raw%`
  - 全服待缴税纯数字

- `%phcoretax_next_settle_seconds%` / `%phcoretax_next_settlement_seconds%`
  - 距离下次结税的秒数

## 配置文件

路径：`feature/economy/`

- `economy.yml`
  - 经济主配置、默认货币与币种定义

- `central-bank.yml`
  - 中心银行治理与宏观参数配置

- `tax.yml`
  - 税务收益池、阶梯税率与统一结税配置


## 主要代码结构

- `EconomySettings.kt`
  - 配置读取、货币定义管理

- `EconomyStorageService.kt`
  - 余额缓存、异步持久化

- `EconomyProvider.kt`
  - Vault 服务实现与注册

- `CentralBankService.kt`
  - `C/D` 央行储备、动态发行、休眠回收与宏观指标

- `CentralBankSettings.kt`
  - 央行独立配置读取

- `MoneyCommand.kt`
  - 经济系统命令入口

- `EconomyCommandDisplay.kt`
  - 央行/税务命令展示逻辑与格式化工具

- `EconomyPlaceholders.kt`
  - 经济 PlaceholderAPI 扩展

- `TaxSettings.kt`
  - 税务独立配置读取与阶梯税率解析

- `TaxService.kt`
  - 收益池累计、统一结税与欠税追踪

- `TaxPlaceholders.kt`
  - 税务 PlaceholderAPI 扩展
