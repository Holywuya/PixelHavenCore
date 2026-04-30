# Trade 模块

面对面交易模块，支持请求确认、双人 GUI 交易、报价输入与交易税结算。

## 功能概述

- **交易请求流程**
  - 请求、接受、拒绝
  - 可通过 Shift+右键快速发起
  - 请求超时自动失效

- **双人交易界面**
  - 双方独立投放物品
  - 双方金币报价
  - 双确认后结算

- **安全校验**
  - 余额与背包空间校验
  - 失败回滚与物品退款
  - 金额输入支持 `cancel` 取消

- **税收联动**
  - 对双方报价应用玩家交易税率
  - 交易入账先进入收款方个人收益池
  - 税额不会在交易瞬间直接结算，而是等统一结税时处理

## 指令

主命令：`/trade`（别名：`/faceTrade`）

- `/trade request <玩家>`
  - 发起交易请求

- `/trade accept <玩家>`
  - 接受交易请求

- `/trade deny <玩家>`
  - 拒绝交易请求

- `/trade reload`
  - 重载交易配置

## 配置文件

路径：`feature/face-trade.yml`

- `enabled`
  - 模块总开关

- `requestTimeoutSeconds`
  - 请求超时时间

- `title`
  - 交易界面标题

- `messages.*`
  - 请求、输入、完成、取消等提示文案

## 主要代码结构

- `TradeSettings.kt`
  - 配置读取与 `reload()`

- `TradeService.kt`
  - 请求管理、会话管理与结算逻辑

- `TradeListener.kt`
  - 交易相关事件监听

- `TradeCommand.kt`
  - 命令入口
