# NewFish

> 一套完整的钓鱼扩展玩法：48 种自定义鱼类、7 个**已注册为原版附魔**的钓鱼附魔、钓鱼图鉴与鱼获品级、寻宝地图。

NewFish 是一个面向 Paper 服务端的钓鱼玩法插件。它接管玩家的钓鱼过程，在保留原版钓鱼手感的前提下，为每一次抛竿加入鱼类、垃圾、宝藏三类完整的自定义战利品池，并围绕钓鱼构建了一整套附魔、收集与纪录系统。

插件全部界面文本为简体中文，物品名称、描述、系统提示均已本地化。

---

## 目录

- [特性总览](#特性总览)
- [环境要求](#环境要求)
- [安装](#安装)
- [命令](#命令)
- [钓鱼系统](#钓鱼系统)
- [自定义鱼类](#自定义鱼类)
- [钓鱼图鉴与鱼获品级](#钓鱼图鉴与鱼获品级)
- [自定义附魔](#自定义附魔)
- [PlaceholderAPI 占位符](#placeholderapi-占位符)
- [寻宝地图](#寻宝地图)
- [配置文件](#配置文件)
- [从源码构建](#从源码构建)
- [设计说明](#设计说明)
- [常见问题](#常见问题)
- [资源许可](#资源许可)

---

## 特性总览

| 模块 | 内容 |
| --- | --- |
| 钓鱼系统 | 鱼类 / 垃圾 / 宝藏三类战利品池，权重全可配，支持生物群系与世界白名单 |
| 自定义鱼类 | 48 种鱼，带独立模型与贴图，可直接食用 |
| 钓鱼图鉴 | 逐鱼种记录收集进度，支持个人进度查询与全服纪录榜 |
| 鱼获品级与重量 | 每次钓获掷出重量，按重量分普通 / 稀有 / 传说三档，传说档全服播报 |
| 自定义附魔 | 7 个附魔，**注册为真正的原版附魔**，但只能通过钓鱼获得 |
| 附魔获取途径 | 钓鱼可自然钓到带自定义附魔的鱼竿、弓与附魔书 |
| PlaceholderAPI | 提供图鉴进度、个人最佳、全服纪录等占位符 |
| 寻宝地图 | 5 种定位原版结构的藏宝图 |
| 命令 | 5 个子命令，全部支持 Tab 补全 |
| 配置迁移 | 配置文件带版本号，升级插件后自动迁移，无需手动改配置 |

---

## 环境要求

| 项目 | 版本 |
| --- | --- |
| 服务端 | **Paper / Folia 1.21.x ~ 26.x（最高验证到 26.3）**，`api-version: 1.21` |
| Java | **21 及以上**（1.21 服务端的最低要求；在 Java 25 上同样正常运行） |
| PlaceholderAPI | **必需**（硬依赖，`plugin.yml` 中为 `depend`） |
| CraftEngine | 可选（软依赖）。未安装时插件仍可正常运行，自定义鱼会退化为原版占位物品，玩法逻辑不受影响 |

### 版本兼容说明

插件编译基线为 **paper-api 1.21.6**、字节码目标 **Java 21**，因此一个 jar 即可覆盖 1.21.x ~ 26.x。

有两处 API 在 1.21.x 内部发生过变化，插件已在运行期自动适配：

| 能力 | 1.21.0 ~ 1.21.5 | 1.21.6 ~ 1.21.8 | 1.21.9 ~ 26.x |
| --- | --- | --- | --- |
| 附魔注册 | `freeze()` 旧 API | 两套并存，优先 `compose()` | `compose()` 新 API |

启动日志会打印实际走的是哪条路径（`已通过 compose() 注册…` 或 `已通过 freeze() 注册…`）。
仅在服务端缺失全部注册表事件 API 时（低于 1.21），附魔才不可用，此时钓鱼、战利品、图鉴与品级照常运行。

CraftEngine 通过其公开 API 反射调用，**不会被打包进本插件的 JAR**，因此升级 CraftEngine 无需重新编译插件。PlaceholderAPI 同样只在编译期需要其接口，运行期由服务端提供。

### Folia 支持

插件已声明 `folia-supported: true`，可运行在 Folia 及其分支上。为此，所有随玩家或实体移动的状态都改为按实体所属的 region 调度，而不是用单一的全局任务：

- 水痕附魔按箭矢自身的调度器推进，方块写入始终发生在拥有该区块的 region 线程。
- 末影鱼钩的跨 region 传送改用 `teleportAsync`，由服务端完成线程切换后再施加后续效果。
- 周期性刷新 CraftEngine 物品、玩家档案自动落盘等不触碰世界状态的工作仍走全局调度。

在非 Folia 服务端上，这些调度会自动回退到传统的 Bukkit 调度器，行为与之前一致。

---

## 安装

1. 将 `NewFish.jar` 放入服务端的 `plugins/` 目录。
2. 安装 [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)（必需）。
3. （推荐）安装 CraftEngine，以便自定义鱼显示独立模型与贴图。
4. 启动服务端，插件会自动生成 `plugins/NewFish/config.yml`。
5. 若已安装 CraftEngine，执行一次 `/newfish assets`：

```text
/newfish assets
```

该命令会把随插件内置的资源包（`newfish` 命名空间、48 种鱼的模型与贴图）安装到 `plugins/CraftEngine/resources/newfish/`，随后自动请求 `/ce reload all` 使资源生效。

完成后即可正常钓鱼，无需其它配置。

---

## 命令

所有命令均需要 `newfish.admin` 权限（默认仅 OP），且**全部支持 Tab 补全**。

| 命令 | 说明 |
| --- | --- |
| `/newfish assets` | 安装或刷新内置资源包，并请求 CraftEngine 重载 |
| `/newfish reload` | 重载 `config.yml`，立即生效 |
| `/newfish give <玩家> <物品> [数量]` | 发放自定义鱼 |
| `/newfish enchant <附魔> [等级]` | 给手持物品附加自定义附魔 |
| `/newfish codex [玩家]` | 查看钓鱼图鉴进度；省略玩家名时查看自己 |

Tab 补全的层级：

- `/newfish ` → `assets`、`codex`、`enchant`、`give`、`help`、`reload`
- `/newfish codex ` → 当前在线玩家名
- `/newfish give ` → 当前在线玩家名
- `/newfish give <玩家> ` → 全部鱼类 ID
- `/newfish give <玩家> <物品> ` → 常用数量 `1` `2` `4` `8` `16` `32` `64`
- `/newfish enchant ` → 7 个附魔 ID
- `/newfish enchant <附魔> ` → 等级 `1` ~ `5`

示例：

```text
/newfish give Steve koi 3
/newfish give Steve marlin
/newfish enchant treasure_hunter 3
/newfish codex Steve
```

`/newfish enchant` 会校验手持物品是否匹配该附魔，不匹配时会提示应使用哪类物品，不会静默失败。

---

## 钓鱼系统

### 三类战利品池

每次成功钓上物品时，插件先按**分类权重**决定落在哪一类，再在该类内部按**条目权重**抽取：

```yaml
fishing:
  category-weights:
    fish: 85
    junk: 10
    treasure: 5
```

权重是相对值，填 `0` 即可禁用某一类。

### 影响权重的附魔

| 来源 | 效果 |
| --- | --- |
| 原版**海之眷顾**（Luck of the Sea） | 每级：宝藏权重 `+2`，垃圾权重 `-1` |
| 自定义附魔**宝藏猎人** | 每级：宝藏权重 `+2`，垃圾权重 `-1`（可与海之眷顾叠加） |

因此满配鱼竿（海之眷顾 III + 宝藏猎人 III）可把宝藏率从默认 5% 提升到约 16%。

### 其它规则

- **世界白名单**：`fishing.worlds` 留空表示所有世界生效，填写世界名后仅在这些世界生效。
- **生物群系限定**：战利品条目可加 `biomes` 字段，仅在指定群系中生效（例如竹子只在丛林类群系钓出）。
- **不覆盖原版战利品表**：插件通过 `PlayerFishEvent` 接管结果，不会修改 `minecraft:gameplay/fishing/*`，因此与原版钓鱼机制兼容。
- **生物类结果**：部分结果会在鱼钩位置直接生成实体（如溺尸、守卫者、海龟等），而不是给出物品。

---

## 自定义鱼类

共 **48 种**自定义鱼，全部可直接食用（饥饿值 3、饱和度 1.8），每种都有独立模型与贴图，并附带一句趣味描述。

鱼类通过 CraftEngine 渲染外观，CraftEngine ID 统一为 `newfish:<鱼类ID>`：

```text
newfish:anchovy      newfish:angelfish    newfish:arapaima
newfish:arowana      newfish:bass         newfish:catfish
newfish:coelacanth   newfish:jellyfish    newfish:koi
newfish:marlin       newfish:piranha      newfish:stringfish
...
```

完整 48 个 ID 见 `config.yml` 的 `craftengine.items` 段与 CraftEngine 的 `items.yml`。

**48 种鱼全部已加入随机钓鱼掉落池**，稀有度权重沿用 `50 / 35 / 20 / 10 / 5` 五档。

---

## 钓鱼图鉴与鱼获品级

### 鱼获重量与品级

每次钓到自定义鱼时，插件会为这条鱼掷出一个重量与品级。重量以配置的**基准重量**为中心上下浮动：

```text
实际重量 = 基准重量 × (1 ± variance)
```

- `fishing.weight.variance` 默认 `0.35`，即实际重量在基准的 ±35% 之间均匀取；
- 各鱼种的基准重量在 `fishing.weight.base-kilograms` 中逐个配置（`marlin` 40 kg、`bitterling` 0.1 kg…）；
- 未单独配置的鱼使用 `fishing.weight.default-base-kilograms`（默认 1.0 kg）。

品级由实际重量相对基准的偏离程度决定，共三档：

| 品级 | 判定条件 | 默认显示 |
| --- | --- | --- |
| 普通 | 低于基准的 `1 + variance/2` 倍 | 灰色「普通」 |
| 稀有 | 达到 `1 + variance/2` 倍以上 | 青色「稀有」 |
| 传说 | 达到 `fishing.weight.legendary-factor` 倍以上（默认 1.6） | 金色「传说」 |

品级名与颜色可在 `fishing.weight.grades` 中完全自定义。鱼获物品的 Lore 会显示品级与重量，并在已有全服纪录时附上一行全服最大值。

`fishing.weight.broadcast-legendary` 开启时（默认开启），任何玩家钓到**传说档且刷新全服纪录**的鱼，都会向全服播报。

### 图鉴

插件为每位玩家维护一份钓鱼档案，记录：

- **已收集鱼种**（个人首次钓到即计入）
- **各鱼种个人最佳重量**
- **累计钓获次数**

`/newfish codex` 可查看自己的图鉴进度，标题显示 `已收集 N/总数`，逐条列出每种鱼的状态：未钓到显示 `???`，已钓到显示中文名与个人最佳重量。`/newfish codex <玩家>` 可查看他人的进度（需 `newfish.admin`）。

首次钓到某个鱼种时会给玩家发一条进度提示（可用 `codex.announce-first-catch` 关闭）；刷新个人最佳时会单独提示。

档案保存在 `plugins/NewFish/players.yml`，写入采用「先写临时文件再原子替换」，防止服务器崩溃写坏文件。文件每 5 分钟自动落盘一次，关服时再存一次。

---

## 自定义附魔

7 个附魔全部实现真实行为，并且**已在引导期注册为真正的原版附魔**——它们与原版附魔系统完全一体，不使用持久化数据或自定义属性模拟，因此 tooltip、等级数字、发光效果、砂轮去除与其它插件的识别都由原版处理。

### 获取途径：只能通过钓鱼

这 7 个附魔被刻意配置成**无法从附魔台或村民处获得**：

- 附魔台的**权重压到原版允许的最低值 1**；
- **成本区间下界抬到 60**，远超 30 级附魔台能达到的成本上限。

原版附魔台挑选附魔时，会先由「物品附魔能力 + 书架加成」算出一个 1~30 级的 `modifiedLevel`（30 级满书架时上限约 50），再只从 `minimumCost <= modifiedLevel` 的候选里抽取。下界设为 60 后，**任何等级、任何书架配置的附魔台都抽不到它们**；图书管理员村民同理由此筛除。

唯一的正常来源是下方的钓鱼机制。铁砧合并与 `/enchant` 手动给予不走这套筛选，仍可正常使用，供管理员调试或补偿。

### 附魔一览

附魔的注册由 `NewFishBootstrap` 在服务端引导阶段通过 Paper 的注册表事件完成，各附魔的适用物品、权重、最大等级与出现成本都已按原版附魔的规格填充。

> **关于 `paper-plugin.yml`**：Paper 只在 `paper-plugin.yml` 里解析 `bootstrapper` 字段。若插件只有 `plugin.yml`，会被当作传统 Bukkit 插件加载，引导期代码**完全不会执行**，附魔注册随之静默失败（表现为 `/newfish enchant` 提示成功但物品毫无变化）。因此本插件的 JAR 内**同时包含 `paper-plugin.yml`（主类 + bootstrapper + 依赖）与 `plugin.yml`（commands + permissions）**，二者分工互补。启动日志里 `[NewFish] 引导期已启动` 与 `[NewFish] 已注册 7 个 NewFish 附魔` 两行即表示引导期正常。

| 附魔 ID | 中文名 | 适用物品 | 效果 |
| --- | --- | --- | --- |
| `sea_lure` | 海之诱饵 | 钓鱼竿 | 每级缩短咬钩等待时间 20 tick（1 秒） |
| `treasure_hunter` | 宝藏猎人 | 钓鱼竿 | 每级提升宝藏率（见上文） |
| `hooked_strike` | 鱼钩猛击 | 钓鱼竿 | 钓中实体时造成伤害，最高 6 点 |
| `ender_hook` | 末影鱼钩 | 钓鱼竿 | 下落时把玩家传送到鱼钩位置；代价是短暂失明、缓慢、虚弱、饥饿与 2 点伤害 |
| `water_trail` | 水痕 | 弓 | 箭矢飞行时沿轨迹生成水方块，单支箭最多 24 格 |
| `dolphins_grace` | 海豚恩惠 | 护腿 | 穿戴时获得海豚的恩惠 |
| `thundering` | 雷霆 | 三叉戟 | 命中目标时召唤闪电 |

### 钓鱼产出

当从宝藏池抽到「附魔鱼竿 / 附魔弓 / 附魔书」时，有一定概率自带一个 NewFish 附魔：

```yaml
enchantments:
  natural-loot:
    enabled: true
    chance: 0.35          # 附上附魔的概率
    pool:
      rod:  [sea_lure, treasure_hunter, hooked_strike, ender_hook]
      bow:  [water_trail]
      book: [sea_lure, treasure_hunter, hooked_strike, ender_hook,
             water_trail, dolphins_grace, thundering]
    levels:
      default: [1, 1]     # 等级区间，随机取整数（含两端）
      sea_lure: [1, 3]
      treasure_hunter: [1, 3]
      hooked_strike: [1, 3]
      water_trail: [1, 3]
```

- 鱼竿只能钓到四个鱼竿附魔，弓只能钓到「水痕」，**附魔书是最全的途径**（七种都可能出现）；
- 附魔书的附魔写入「存储的附魔」，玩家可正常用于铁砧；
- 各附魔的出现等级由 `levels` 段控制，缺省回落到 `default`。

---

## PlaceholderAPI 占位符

插件注册了 `newfish` 扩展（PlaceholderAPI 为硬依赖，无需额外安装扩展）。所有占位符前缀为 `%newfish_...%`：

### 与玩家相关

| 占位符 | 说明 |
| --- | --- |
| `%newfish_collected%` | 已收集鱼种数量 |
| `%newfish_collected_percent%` | 图鉴完成百分比（保留 1 位小数） |
| `%newfish_species%` | 图鉴总鱼种数 |
| `%newfish_missing%` | 尚未收集的鱼种数 |
| `%newfish_complete%` | 是否已集齐（`是` / `否`） |
| `%newfish_caught%` | 累计钓获次数 |
| `%newfish_best_<鱼ID>%` | 该鱼种的个人最佳重量（无纪录返回 `0`） |
| `%newfish_full_<鱼ID>%` | 个人最佳，附中文名（无纪录返回 `???`） |
| `%newfish_has_<鱼ID>%` | 是否已收录该鱼种（`是` / `否`） |

### 全服纪录

| 占位符 | 说明 |
| --- | --- |
| `%newfish_record_<鱼ID>%` | 该鱼种的全服最大重量（无纪录返回 `0`） |
| `%newfish_holder_<鱼ID>%` | 该鱼种的全服纪录保持者（无纪录返回 `-`） |
| `%newfish_top%` | 全服纪录榜（按重量降序，条数由 `codex.top-limit` 控制） |

示例：

```text
钓鱼图鉴：%newfish_collected%/%newfish_species%（%newfish_collected_percent%%）
你的鲈鱼纪录：%newfish_best_bass% kg
本服最大金枪鱼：%newfish_record_marlin% kg（%newfish_holder_marlin%）
```

占位符全部读取内存缓存，不做磁盘访问，可安全用于计分板、Tab 列表等高频刷新场景。

---

## 寻宝地图

**寻宝地图**（5 种）——使用 Bukkit 地图 API 生成，并在可能的情况下定位对应的原版结构，把地图中心指向该结构：

| 地图 | 指向结构 |
| --- | --- |
| 沉船地图 | 沉船 |
| 埋藏宝藏地图 | 埋藏的宝藏 |
| 海底神殿地图 | 海底神殿 |
| 海底废墟地图 | 海底废墟 |
| 废弃传送门地图 | 废弃传送门 |

搜索半径可通过 `fishing.map-search-radius` 调整（可选键，默认 128 格）。

---

## 配置文件

配置文件位于 `plugins/NewFish/config.yml`，修改后执行 `/newfish reload` 立即生效。

```yaml
config-version: 4        # 升级插件时自动迁移，请勿手动修改

fishing:
  enabled: true
  category-weights:      # 鱼类 / 垃圾 / 宝藏 分类权重
    fish: 85
    junk: 10
    treasure: 5
  worlds: []             # 留空表示所有世界
  loot:                  # 三类战利品条目，weight 为相对权重，0 表示禁用
    fish: [ ... ]
    junk: [ ... ]
    treasure: [ ... ]
  weight:                # 鱼获重量与品级
    enabled: true
    default-base-kilograms: 1.0
    variance: 0.35
    legendary-factor: 1.6
    broadcast-legendary: true
    base-kilograms: { ... }   # 各鱼种基准重量
    grades: { ... }           # 三档品级的显示名与颜色

codex:                   # 钓鱼图鉴
  enabled: true
  announce-first-catch: true
  unknown-name: '???'
  top-limit: 5

craftengine:
  enabled: true
  items:                 # 逻辑 ID → CraftEngine ID 映射
    anchovy: newfish:anchovy
    ...

enchantments:
  enabled: true
  sea_lure: true         # 每个附魔的功能监听器可单独开关
  ...
  water-trail:           # 水痕上限，防止滥用
    max-active-arrows: 64
    max-blocks-per-arrow: 24
    max-distance: 48
    lifetime-ticks: 20
  natural-loot:          # 钓鱼自然产出附魔物品
    enabled: true
    chance: 0.35
    pool: { rod: [...], bow: [...], book: [...] }
    levels: { ... }
```

配置文件带 `config-version` 版本号。插件升级时会自动迁移旧格式配置并保存，**不需要手动删除配置文件**，迁移过程会在控制台输出日志。迁移只补缺失项，不会覆盖你自行调整过的数值。

---

## 从源码构建

项目自带 Gradle Wrapper：

```text
.\gradlew.bat build
```

产物位于：

```text
build/libs/NewFish-1.0-SNAPSHOT.jar
```

构建依赖（`build.gradle.kts`）：

- `io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT`（`compileOnly`，编译基线也是最低支持版本的上界）
- `me.clip:placeholderapi:2.11.6`（`compileOnly`，运行期由服务端提供）
- Java 工具链 25
- 插件 `xyz.jpenilla.run-paper`，可直接 `./gradlew runServer` 启动测试服务端（默认拉取 26.3）

---

## 设计说明

- **钓鱼**由 `PlayerFishEvent` 驱动，不覆盖原版战利品表，可与其它玩法插件共存。
- **稳定物品身份**：自定义鱼先由 CraftEngine 生成外观，再写入插件自己的持久化数据标记作为逻辑身份。因此判断物品类型**不依赖显示名**，改名字、改描述都不会影响玩法。
- **附魔**通过 Paper 的注册表事件在引导期注册为真原版附魔，因此铁砧、砂轮、`/enchant` 与其它插件都能正常识别，无需为每个附魔单独兼容。同时把权重压到 1、成本下界抬到 60，使附魔台与村民无法产出，从而把获取途径锁定为钓鱼。
- **收集数据**保存在内存，磁盘写入采用原子替换并周期化，避免高频钓获时的 I/O 抖动。
- **生物生成与清理**在拥有该位置的 region 线程完成；水痕只跟踪插件自己生成的箭，并且只清理插件自己放置的水方块。
- **地图**使用 Bukkit 地图 API，可安全用于多世界环境。

---

## 常见问题

**Q：不装 CraftEngine 能用吗？**
可以。插件会使用原版物品作为占位，钓鱼、附魔等玩法完全正常，只是看不到自定义模型与贴图。

**Q：不装 PlaceholderAPI 能用吗？**
不能。PlaceholderAPI 是硬依赖，缺失时服务端会拒绝加载本插件。这是因为插件在启用时就会注册占位符扩展。

**Q：自定义附魔在哪里获取？**
钓鱼。这 7 个附魔是真正的原版附魔，但权重被压到最低值 1、成本下界抬到 60，附魔台与图书管理员村民都抽不到它们。唯一正常来源是从钓鱼宝藏池里钓到自带的附魔鱼竿 / 弓 / 附魔书（附魔书最全，七种都可能出现）。管理员仍可用 `/newfish enchant` 手动给予，铁砧合并也正常。

**Q：为什么某种鱼一直钓不上来？**
先确认该鱼是否在 `fishing.loot.fish` 池中，以及所处生物群系是否满足该条目的 `biomes` 限制。

**Q：改了配置不生效？**
执行 `/newfish reload`。若修改的是 CraftEngine 资源（`items.yml` 等），需要执行 `/newfish assets` 或 `/ce reload all`。

**Q：升级插件后需要删配置吗？**
不需要。配置文件带版本号并会自动迁移，控制台会打印迁移日志。

**Q：会覆盖原版钓鱼战利品表吗？**
不会。插件通过事件接管钓鱼结果，不修改原版战利品表。

**Q：钓鱼图鉴数据存在哪？**
`plugins/NewFish/players.yml`。删掉该文件即重置所有玩家的图鉴与纪录。

---

## 资源许可

- **本项目的原创部分**（Java 源码、配置模板、文档与自制美术）采用 **CC BY 4.0（署名 4.0 国际）**：
  可自由复制、修改、二次开发与商业使用，唯一义务是署名。
- **鱼类贴图与模型**来自 Exline's Fishing Mod（作者 Exline），上游许可为 MIT / CC0 1.0，
  本项目完整保留其许可文件并署名。
- **玩法框架**移植自数据包 Fishing Expansion（作者 lullaby，**AGPL-3.0-or-later**）——
  若认定构成其衍生作品，对外分发时整包须以 AGPL-3.0 发布并提供源码，该义务优先于上述 CC BY 4.0。

完整条款见仓库根目录的 `LICENSE` 与 `THIRD_PARTY_NOTICES.md`；随插件 JAR 附带：

- `third-party/NOTICE-exlinefishing.txt`
- `newfish-pack/LICENSE_exlinefishing`

