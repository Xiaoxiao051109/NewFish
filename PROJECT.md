# NewFish 项目文档

> 面向开发者/维护者的技术文档。面向服主与玩家的说明见 [`README.md`](README.md)；
> 上游来源与许可合规见 [`../NewFish-许可与二次开发调研.md`](../NewFish-许可与二次开发调研.md)。

---

## 一、项目概览

**NewFish** 是一个面向 **Paper / Folia 1.21.x ~ 26.x** 的钓鱼玩法扩展插件：接管原版钓鱼流程，
在保留原版手感的前提下，为每一次抛竿注入自定义战利品池，并围绕钓鱼构建装备、附魔、图鉴与纪录系统。

| 项 | 值 |
|---|---|
| 插件名 | `NewFish` |
| 主类 | `org.example.xiaoxiao.newFish.NewFish` |
| 引导类 | `org.example.xiaoxiao.newFish.NewFishBootstrap`（引导期注册原版附魔） |
| 版本 | `1.0-SNAPSHOT` |
| 加载器 | **Paper 插件**（`paper-plugin.yml` 与 `plugin.yml` 并存） |
| 目标 API | 编译基线 `paper-api 1.21.6`，`api-version: '1.21'`（覆盖 1.21.x ~ 26.x） |
| Java | 编译目标 **21**（Gradle toolchain 25，`options.release = 21`） |
| Folia | ✅ `folia-supported: true` |
| 代码规模 | 13 个 Java 文件 / 约 3.0k 行 |
| 数据目录 | `plugins/NewFish/`（`config.yml`、`players.yml`） |

### 核心能力

- **48 种自定义鱼** + 原版鱼/垃圾/宝藏三类战利品池，权重、生物群系、世界白名单全可配
- **7 个真·原版附魔**（引导期经 `RegistryEvents.ENCHANTMENT` 注册），只能通过钓鱼获得
- **钓鱼图鉴 + 鱼获重量/品级**（普通/稀有/传说三档，传说档全服播报，纪录持久化）
- **寻宝地图**、**PAPI 占位符**
- **CraftEngine 可选集成**：不装 CE 也能完整运行（物品退化为原版外观）

---

## 二、依赖关系

| 依赖 | 类型 | 声明方式 | 说明 |
|---|---|---|---|
| **PlaceholderAPI** | **硬依赖** | `paper-plugin.yml` → `required: false` + `join-classpath: true`；`onEnable` 自检 | 缺失时插件**自行禁用**。`required` 必须为 false（引导期强校验会失败）；`join-classpath` 必须为 true（`NewFishExpansion extends PlaceholderExpansion`） |
| **CraftEngine** | 可选 | `required: false` + `join-classpath: false` | 全程反射访问，不继承其类。缺失时回落原版外观，钓鱼照常 |

> ⚠️ 这两条依赖声明是踩过坑之后的结果，改动前请先读 `paper-plugin.yml` 里的长注释。

---

## 三、目录结构

```
NewFish/
├── build.gradle.kts                     # Gradle 构建（toolchain 25 / run-paper 3.1.0）
├── LICENSE                              # CC BY 4.0（含上游声明）
├── THIRD_PARTY_NOTICES.md               # 上游来源与第三方许可
├── .gitignore
├── README.md                            # 面向用户/服主的说明
├── PROJECT.md                           # 本文件
├── src/main/java/org/example/xiaoxiao/newFish/
│   ├── NewFish.java                     # 主类：命令、装配、自检、配置迁移
│   ├── NewFishBootstrap.java            # 引导期：注册 7 个原版附魔
│   ├── GameplayListener.java            # 鱼获食用兜底
│   ├── data/PlayerDataStore.java        # 图鉴 / 个人最佳 / 全服纪录（players.yml）
│   ├── enchantment/
│   │   ├── EnchantmentKeys.java         # 附魔 ID 与中文名集中定义
│   │   └── EnchantmentService.java      # 附魔行为：末影钩、钩击、水迹、雷鸣
│   ├── fishing/
│   │   ├── FishingService.java          # 钓鱼接管：抽签、构建鱼获、抛给玩家
│   │   └── FishWeight.java              # 重量与品级判定
│   ├── integration/
│   │   ├── CraftEngineBridge.java       # 反射桥接 CraftEngine（可选）
│   │   └── NewFishExpansion.java        # PAPI 占位符扩展（identifier: newfish）
│   ├── item/
│   │   ├── ItemFactory.java             # 物品构建（CE 优先 + 原版兜底）
│   │   └── ItemIdentity.java            # PDC：物品类型、鱼种、重量、品级、自定义附魔
│   └── util/Scheduler.java              # Paper/Folia 统一调度门面
└── src/main/resources/
    ├── paper-plugin.yml                 # Paper 描述符（bootstrapper / folia / dependencies）
    ├── plugin.yml                       # 传统描述符（permissions；⚠️ 不含 commands 段）
    ├── config.yml                       # 主配置（config-version: 5）
    ├── newfish-pack/                    # CraftEngine 内容包
    │   ├── pack.yml
    │   ├── configuration/items.yml      # 48 种鱼的物品定义
    │   ├── LICENSE_exlinefishing        # 贴图/模型来源许可（CC0 1.0 全文）
    │   └── resourcepack/assets/newfish/{models,textures}/item/
    └── third-party/NOTICE-exlinefishing.txt
```

---

## 四、核心机制

### 4.1 插件加载与附魔注册（易错点最多的一环）

NewFish 走 **Paper 插件**加载路径，因此：

| 规则 | 说明 |
|---|---|
| `bootstrapper` 只在 `paper-plugin.yml` 生效 | 写在 `plugin.yml` 里会被完全忽略，表现为附魔静默注册失败 |
| `folia-supported` 必须写在 `paper-plugin.yml` | 否则报 `Could not load plugin ... not marked as supporting Folia!` |
| Paper 插件**不读** YAML 命令声明 | `JavaPlugin#getCommand()` 在启用阶段直接抛 `UnsupportedOperationException` |
| 依赖的 `required` 是**引导期强校验** | 对传统插件写 `true` 必然 `UnknownDependencyException` |
| `join-classpath` 决定能否 `extends` 对方类 | 反射不需要；**继承必须开**，否则 `NoClassDefFoundError` |

命令因此改为 `NewFish implements BasicCommand` + `registerCommand("newfish", ...)`；
`plugin.yml` 刻意**不保留** `commands` 段（避免降级路径上 `tab-completer` 抛 `ClassCastException`）。

附魔注册流程（**双 API 分流**，见 4.5）：
`NewFishBootstrap.bootstrap()` 探测服务端能力 → 反射加载
`ComposeEnchantmentRegistrar`（1.21.6+）或 `FreezeEnchantmentRegistrar`（1.21.0~1.21.8）
→ 7 个附魔写入注册表 → `onEnable` 末尾**自检**（`EnchantmentKeys.missing()`），缺失则打 WARNING 列出。

> 引导期异常不会让插件崩溃，只会**静默丢结果** —— 所以 bootstrap 里必须显式 try-catch + `getLogger().error()`。

### 4.2 钓鱼接管（"仿原版抛鱼"）

`FishingService` 监听 `PlayerFishEvent`，只在 `CAUGHT_FISH` 状态接管：

1. **不取消事件** —— `CAUGHT_FISH` 同时承担"生成掉落物"和"收杆"两件事，
   一旦 `setCancelled(true)`，鱼钩就再也收不回来（玩家会什么也拿不到）
2. 立即取落点坐标 → 抽分类（鱼/垃圾/宝藏）→ 抽条目 → 在**事件线程**上完成 ItemStack 构造
3. `launchToPlayer(...)`：派发到物品所在 region → 先 `remove()` 掉原版那条鱼 → 生成自己的物品
   → `setVelocity(朝玩家, 水平 0.32 + 上扬 0.2)` → `setPickupDelay(0)`
4. 派发失败（实体已回收等）→ 回退 `deliverToPlayer(...)` 直接进背包

**战利品条目类型**（`fishing.loot.*[].type`）：`custom_fish`（自定义鱼）、`item`（原版物品，可带 `amount`）、
`mob`（在鱼钩处生成实体）、`map`（寻宝地图）、`enchanted`（自带附魔的鱼竿/弓/书）。
⚠️ 未知类型**不再静默回落成原版物品**，而是打警告并跳过本次钓获 —— 避免配置写错时表现为「玩家莫名钓到一件原版物品」。

**约定**：`handleFishCatch(...)`（写 PDC + 图鉴 + 播报）在任意路径下**恰好执行一次** ——
成功路径在 `launchToPlayer` 补调用，失败路径由 `deliverToPlayer` 内部调用。

> ⚠️ `player.getInventory().addItem(stack)` **会消耗传入的 ItemStack**，
> 因此所有写入（重量/品级/lore）必须在入库**之前**完成。

### 4.3 重量、品级与图鉴

- `FishWeight.roll(fishId)` 掷出重量与品级（按相对基准的偏离倍率分档，而非绝对重量）
- 结果写入物品 PDC（`ItemIdentity.setFishWeight/setFishGrade`）并渲染进 lore
- `PlayerDataStore.record(...)` 更新：个人最佳 / 全服纪录 / 图鉴收集数
- 存储：`plugins/NewFish/players.yml`，原子写（tmp → `REPLACE_EXISTING`），5 分钟自动落盘 + 关服落盘

### 4.4 Folia 调度门面

`util/Scheduler.java` 是**唯一的调度入口**，业务代码不出现 Folia 类型：

- Folia 探测：**`Bukkit.getGlobalRegionScheduler() != null`**（catch Throwable）
  —— ⚠️ `Bukkit.isFolia()` 在 paper-api 26.3 **不存在**，反射调用会 `NoSuchMethodException` 并被误判为非 Folia
- 提供 `runGlobal*` / `runEntity*` / `runAsync*` / `region` 与 `Handle`（三种取消方式）
- `runEntityChecked(...)` 返回 `boolean`，暴露"任务是否被接受"—— Folia 上实体已回收时返回 `false` 且**任务不执行**，调用方必须据此走兜底
- ⚠️ Folia 的 `entity.getScheduler().execute(..., delay)` 的 delay 是**真实延迟**（最少 1 tick），
  凡依赖实体存活的操作**不能**在派发后才去读实体状态

---

### 4.5 多版本兼容（1.21.x ~ 26.x）

编译基线 **paper-api 1.21.6**、字节码目标 **Java 21**，一个 jar 覆盖 1.21.x ~ 26.x。

| 能力 | 1.21.0 ~ 1.21.5 | 1.21.6 ~ 1.21.8 | 1.21.9 ~ 26.x |
|---|---|---|---|
| 附魔注册 | `freeze()` | 两套并存，优先 `compose()` | `compose()` |

**两条路径必须物理隔离**：`ComposeEnchantmentRegistrar` 引用的 `RegistryComposeEvent`
在 1.21.0~1.21.5 不存在；`FreezeEnchantmentRegistrar` 引用的 `RegistryFreezeEvent`
在 1.21.9+ 不存在。因此 `NewFishBootstrap` **只按类名反射加载**，走哪条路就只加载哪个类。

同理，`Scheduler` 里对 `Bukkit.getGlobalRegionScheduler()` 的直接调用只在服务端真的提供该方法时
才会执行（`detectFolia()` 用 `catch (Throwable)` 兜住 `NoSuchMethodError`）。1.21.0 的
`Bukkit` 已提供三个 region 调度器与 `Entity#getScheduler()`，因此 Folia 1.21+ 全部可用。

**升级基线时要做的事**：改 `build.gradle.kts` 的 `paper-api` 版本 → 跑
`import` 级兼容性扫描（把所有 `org.bukkit` / `io.papermc` 的 import 与目标基线 jar 比对）
→ 复查 `Scheduler` / `RuntimeCompat` 里靠反射兜底的调用点。

---

## 五、命令与权限

命令 `/newfish`（`BasicCommand` 注册，Brigadier）：

| 子命令 | 用途 | 权限 |
|---|---|---|
| `/newfish help` | 帮助 | — |
| `/newfish assets` | 重新安装 CraftEngine 内容包 | `newfish.admin` |
| `/newfish reload` | 重载配置 | `newfish.admin` |
| `/newfish give <玩家> <物品> <数量>` | 给予物品 | `newfish.admin` |
| `/newfish enchant <附魔> <等级>` | 手动附魔（写入失败会红字报错，不再假成功） | `newfish.admin` |
| `/newfish codex [玩家]` | 查看图鉴 | — |

权限节点：`newfish.admin`（default: op）

---

## 六、配置与数据文件

| 文件 | 位置 | 说明 |
|---|---|---|
| `config.yml` | `plugins/NewFish/` | `config-version: 5`；带版本自动迁移（补缺失键 + 清理下线内容，不覆盖用户调过的值） |
| `players.yml` | `plugins/NewFish/` | 图鉴、个人最佳、全服纪录 |
| CE 内容包 | `plugins/CraftEngine/resources/newfish/` | 由插件在启用时安装；**已存在则跳过不覆盖** |

主要配置段：`fishing.*`（开关/调试/分类权重/世界白名单/三类战利品池/重量品级）、
`codex.*`、`craftengine.*`、`enchantments.*`（含 `natural-loot` 自然产出池与等级区间）。

---

## 七、构建与部署

### 构建

```bash
# 本地离线构建（依赖已在 Gradle 缓存中）
./gradlew clean build --offline

# 产物
build/libs/NewFish-1.0-SNAPSHOT.jar
```

需要 **JDK 25**（`JAVA_HOME` 指向 JDK 25）。仓库自带 Gradle Wrapper。

### 部署

1. 将 jar 放入服务端 `plugins/`
2. 确保服务端已安装 **PlaceholderAPI**（硬依赖，缺失时插件自行禁用）
3. （可选）安装 **CraftEngine** 以获得自定义物品外观与 CE 内容支持
4. 重启服务端；首次安装 CE 资源后如未生效，执行 `/newfish assets`

### ⚠️ 已装过旧版内容的服务器

`CraftEngineBridge.installAssets(false)` 遇到已存在的文件会**跳过不覆盖**。
升级内容包（例如汉化后）需要先删除 `plugins/CraftEngine/resources/newfish/` 再执行 `/newfish assets`。

---

## 八、排错手册

| 现象 | 根因与处理 |
|---|---|
| 附魔全部未注册、`/newfish enchant` 假成功 | 插件被当成传统 Bukkit 插件加载（缺 `paper-plugin.yml` 或 `bootstrapper` 写错文件） |
| `Could not load plugin ... not marked as supporting Folia!` | `folia-supported` 没写在 `paper-plugin.yml` |
| `UnknownDependencyException: [PlaceholderAPI]` | `paper-plugin.yml` 里 PAPI 的 `required` 被改成了 `true` |
| `UnsupportedOperationException: JavaPlugin#getCommand on a Paper plugin` | 试图从 YAML 读命令；应使用 `registerCommand` |
| `NoClassDefFoundError: PlaceholderExpansion` | PAPI 的 `join-classpath` 被改成了 `false` |
| 钓上来的全是"鳕鱼" | CraftEngine 未就绪导致 `ItemFactory.fish()` 回落；或鱼钩物品实体已失效 |
| 鱼钩收不回、钓了个空 | 有人在 `CAUGHT_FISH` 里 `setCancelled(true)`（见 4.2） |
| 图鉴计数翻倍 / 全服播报重复 | `handleFishCatch` 被调用了两次（见 4.2 的"恰好一次"约定） |
| 排查钓获异常 | `config.yml` 里打开 `fishing.debug: true`，会打印事件 state / `getCaught()` 实际类型 / 抽中条目 |

---

## 九、开发约定

- **改 `config.yml` 结构时同步提升 `config-version`**，并在 `migrateConfig()` 补迁移分支
- **新增物品**：`ItemFactory` 加 ID → `newfish-pack/configuration/items.yml` 加定义 → 资源包补模型与贴图 → `config.yml` 战利品池引用
- **新增附魔**：`EnchantmentKeys` 加常量 → `NewFishBootstrap` 注册 → `EnchantmentService` 实现行为 → `config.yml` 的 `natural-loot.pool` 决定投放池
- **不要在业务代码里直接调用 Bukkit/Folia 调度器**，一律走 `util/Scheduler`
- **不要在 `CAUGHT_FISH` 里取消事件**（会连带取消收杆）
- 面板/文案改动需同步 `README.md` 的命令表与 FAQ

---

## 十、许可与来源

- **本项目原创部分**（源码 / 配置模板 / 文档 / 自制美术）：**CC BY 4.0**，见仓库根 `LICENSE`。
- **鱼类贴图与模型**：Exline's Fishing Mod（作者 Exline），MIT / CC0 1.0，见 `THIRD_PARTY_NOTICES.md`。
- **玩法框架**：移植自数据包 Fishing Expansion（作者 lullaby，**AGPL-3.0-or-later**）。

> ⚠️ 若本插件被认定为 Fishing Expansion 的衍生作品，对外分发时整包须以 **AGPL-3.0** 发布
> 并向接收者提供完整源码 —— 该义务优先于 CC BY 4.0。详见
> [`../NewFish-许可与二次开发调研.md`](../NewFish-许可与二次开发调研.md)。

上游参考副本（不入版本库）统一放在 `third-party/upstream/`。

---

## 十一、待办与已知事项

- [x] `LICENSE`（CC BY 4.0 + 上游 AGPL 声明）
- [x] README 补「玩法框架来源」声明
- [x] `paper-plugin.yml` 顶部注释的自相矛盾已修正
- [ ] 无单元测试；验证依赖实机运行
- [ ] 若确认要做合规开源分发，建议按调研文档把整包改为 AGPL-3.0
