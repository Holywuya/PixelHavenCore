# 领地税实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在税收系统中新增领地税——基于玩家领地大小，与收入税共享结税周期，逐领地收取持有费。

**架构：** 在 `tax.yml` 中新增 `dominion` 配置段，`TaxSettings` 中新增领地税相关字段和计算逻辑，`TaxService.settleNow()` 中在收入税结算后插入领地税结算 Phase，`DominionBridge` 中新增 `getAllDominions()`。

**技术栈：** Kotlin、TabooLib、DominionAPI、BigDecimal

---

### 任务 1：DominionBridge 新增 getAllDominions()

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/util/DominionBridge.kt`

- [ ] **步骤 1：添加 `getAllDominions()` 方法**

在 `getDominionSizeAt()` 方法之后添加：

```kotlin
fun getAllDominions(): List<DominionDTO> {
    val dominionAPI = api ?: return emptyList()
    return dominionAPI.allDominions
}
```

- [ ] **步骤 2：构建验证**

```bash
./gradlew build
```

预期：`BUILD SUCCESSFUL`

- [ ] **步骤 3：提交**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/util/DominionBridge.kt
git commit -m "feat(dominion): 新增 getAllDominions() 方法"
```

---

### 任务 2：TaxSettings 新增领地税配置

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/economy/TaxSettings.kt`

- [ ] **步骤 1：新增领地税基枚举和配置字段**

在 `TaxSettings` object 内，`TaxBracket` 数据类之前（约第 70 行），添加：

```kotlin
var dominionTaxEnabled: Boolean = false
    private set

var dominionTaxBase: DominionTaxBase = DominionTaxBase.SQUARE_AREA
    private set

var dominionTaxBrackets: List<TaxBracket> = emptyList()
    private set

enum class DominionTaxBase { SQUARE_AREA, VOLUME }

fun computeDominionTax(size: BigDecimal): BigDecimal {
    if (size <= BigDecimal.ZERO || dominionTaxBrackets.isEmpty()) return BigDecimal.ZERO
    var totalTax = BigDecimal.ZERO
    for (i in dominionTaxBrackets.indices) {
        val floor = dominionTaxBrackets[i].min
        val ceiling = if (i + 1 < dominionTaxBrackets.size) dominionTaxBrackets[i + 1].min else null
        if (size <= floor) break
        val taxable = if (ceiling != null) {
            size.coerceAtMost(ceiling).subtract(floor)
        } else {
            size.subtract(floor)
        }.coerceAtLeast(BigDecimal.ZERO)
        totalTax = totalTax.add(taxable.multiply(BigDecimal.valueOf(dominionTaxBrackets[i].rate)))
    }
    return totalTax.setScale(0, java.math.RoundingMode.HALF_UP).coerceAtLeast(BigDecimal.ZERO)
}
```

- [ ] **步骤 2：在 `reload()` 方法中新增配置读取**

在 `reload()` 方法末尾（`useMarginalRate = ...` 之前或之后），添加：

```kotlin
dominionTaxEnabled = config.getBoolean("dominion.enabled", false)
dominionTaxBase = try {
    DominionTaxBase.valueOf(config.getString("dominion.tax-base", "square")!!.uppercase())
} catch (_: IllegalArgumentException) {
    DominionTaxBase.SQUARE_AREA
}
dominionTaxBrackets = loadDominionTaxBrackets()
```

- [ ] **步骤 3：新增 `loadDominionTaxBrackets()` 私有方法**

在 `loadBrackets()` 方法之后添加：

```kotlin
private fun loadDominionTaxBrackets(): List<TaxBracket> {
    val section = config.getConfigurationSection("dominion.tax-brackets") ?: return emptyList()
    return section.getKeys(false).mapNotNull { key ->
        val node = section.getConfigurationSection(key) ?: return@mapNotNull null
        TaxBracket(
            min = node.getDouble("min", 0.0).coerceAtLeast(0.0).toBigDecimal(),
            rate = node.getDouble("rate", 0.0).coerceAtLeast(0.0)
        )
    }.sortedBy { it.min }
}
```

- [ ] **步骤 4：确保 `TaxBracket` 的 `min` 字段是 `BigDecimal` 类型**

确认 `TaxBracket` 数据类中 `min` 是 `BigDecimal`，`computeDominionTax` 使用 `BigDecimal` 做乘法。

- [ ] **步骤 5：构建验证**

```bash
./gradlew build
```

- [ ] **步骤 6：提交**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/economy/TaxSettings.kt
git commit -m "feat(tax): 新增领地税配置和计算逻辑"
```

---

### 任务 3：TaxService 新增领地税结算 Phase

**文件：**
- 修改：`project/core/src/main/kotlin/com/pixlehavencore/feature/economy/TaxService.kt`

- [ ] **步骤 1：添加 import**

在现有 import 之后添加：

```kotlin
import com.pixlehavencore.util.DominionBridge
```

- [ ] **步骤 2：在 `settleNow()` 中插入领地税结算**

在收入税循环结束后（第 241 行 `}` 之后）、`CentralBankService.recordCollectedTax()`（第 243 行）之前，插入：

```kotlin
        // Phase: 领地税
        if (TaxSettings.dominionTaxEnabled && DominionBridge.isAvailable()) {
            val dominions = DominionBridge.getAllDominions()
            dominions.forEach { dominion ->
                val ownerId = dominion.owner
                val size = dominion.toSizeInfo()
                val taxBase = when (TaxSettings.dominionTaxBase) {
                    TaxSettings.DominionTaxBase.SQUARE_AREA -> BigDecimal.valueOf(size.squareArea)
                    TaxSettings.DominionTaxBase.VOLUME -> BigDecimal.valueOf(size.volume)
                }
                val tax = TaxSettings.computeDominionTax(taxBase)
                if (tax <= BigDecimal.ZERO) return@forEach
                val collected = collectTaxFromAccount(ownerId, tax)
                settled = settled.add(collected)
                val remainingDebt = EconomySettings.normalizeAmount(tax.subtract(collected))
                outstandingDebt = outstandingDebt.add(remainingDebt)
            }
        }
```

- [ ] **步骤 3：构建验证**

```bash
./gradlew build
```

- [ ] **步骤 4：提交**

```bash
git add project/core/src/main/kotlin/com/pixlehavencore/feature/economy/TaxService.kt
git commit -m "feat(tax): 新增领地税结算 Phase"
```

---

### 任务 4：tax.yml 新增领地税配置段

**文件：**
- 修改：`project/core/src/main/resources/feature/economy/tax.yml`

- [ ] **步骤 1：在 tax.yml 末尾添加 `dominion` 配置段**

```yaml
dominion:
  enabled: false
  tax-base: "square"
  tax-brackets:
    tier1:
      min: 0
      rate: 0.0
    tier2:
      min: 100
      rate: 0.01
    tier3:
      min: 1000
      rate: 0.02
    tier4:
      min: 5000
      rate: 0.05
    tier5:
      min: 10000
      rate: 0.10
```

- [ ] **步骤 2：提交**

```bash
git add project/core/src/main/resources/feature/economy/tax.yml
git commit -m "feat(tax): 新增领地税 YAML 配置段"
```

---

### 任务 5：构建验证并最终提交

- [ ] **步骤 1：全量构建**

```bash
./gradlew build
```

预期：`BUILD SUCCESSFUL`

- [ ] **步骤 2：确认无编译错误，推送**

```bash
git push
```
