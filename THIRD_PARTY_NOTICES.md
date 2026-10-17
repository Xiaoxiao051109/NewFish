# 第三方组件与来源声明

本文件列出 NewFish 使用、移植或引用的第三方内容及其许可。
分发本项目（含编译产物 `NewFish-*.jar`）时，请一并保留本文件与 `LICENSE`。

---

## 一、玩法框架来源

### Fishing Expansion（数据包）

| 项 | 内容 |
|---|---|
| 作者 | **lullaby**（GitHub `lullaby6/data-packs`，Modrinth `ly-fishing-expansion`） |
| 本项目参考版本 | v1.3.2（`[1.21.9-1.21.11] - No Boss`） |
| 许可 | **AGPL-3.0-or-later** |
| 参考副本位置 | `third-party/upstream/Fishing Expansion v1.3.2 [1.21.9-1.21.11] - No Boss/` |

**移植范围**：附魔体系（`sea_lure` / `treasure_hunter` / `hooked_strike` / `ender_hook` /
`water_trail` / `dolphins_grace`，与上游 ID 同名）、宝藏池中的寻宝地图类型
（沉船 / 埋藏宝藏 / 海底神殿 / 海底废墟 / 废弃传送门）、钓上生物的分类方式。

**未使用**：未复制数据包内的任何文件；源码中不含 `fishing_expansion` 命名空间；
Java 实现、数值参数与配置结构均为本项目自行编写。

> ⚠️ **AGPL-3.0 具有强著佐权与网络条款**。若本插件被认定为该数据包的衍生作品，
> 对外分发时须以 AGPL-3.0 发布并向接收者提供完整源码；该义务优先于 `LICENSE` 中的 CC BY 4.0。
> 完整分析见 `NewFish-许可与二次开发调研.md`。

---

## 二、美术资源来源

### Exline's Fishing Mod（Fabric 模组）

| 项 | 内容 |
|---|---|
| 作者 | **Exline**（`jexline`） |
| 本项目参考版本 | v1.0.4（Fabric 1.20.1） |
| 许可 | **上游自身存在不一致**：`fabric.mod.json` 声明 `"license": "MIT"`；jar 内 `LICENSE_exlinefishing` 正文为 **CC0 1.0 Universal** |
| 参考副本位置 | `third-party/upstream/exlinefishing-fabric-1.20.1-v1.0.4.jar` |
| 许可原文 | `src/main/resources/newfish-pack/LICENSE_exlinefishing`（随插件 JAR 分发） |

**使用范围**：48 种鱼的物品贴图（`textures/item/*.png`，与上游逐字节一致）与
对应的 `item/generated` 模型定义（仅将命名空间由 `exlinefishing` 改为 `newfish`）。

**两种许可下的义务**：

- 若按 **MIT**：可商用、可修改、可再分发，须保留版权声明与许可文本；
- 若按 **CC0 1.0**：可自由使用，无署名义务。

本项目**按较严格的一侧处理**：完整保留上游许可文件并在此署名，因此无论上游最终采用哪种口径均可满足。

未使用：未加载或再分发该模组的任何 Java 类、Mixin、`fabric.mod.json` 或 Fabric 运行时逻辑。

---

## 三、运行期依赖（不随本插件分发）

| 组件 | 许可 | 关系 |
|---|---|---|
| **PlaceholderAPI** | MIT / GPL-3.0（上游历史版本许可不一） | **硬依赖**：仅在编译期引用其公开接口，运行期由服务端提供，不打包 |
| **CraftEngine** | **GPL-3.0** | **可选依赖**：仅通过反射调用其公开 API，不打包、不分发其代码或资源 |

> 两者均未编入本插件 JAR。如你的分发方式涉及随包附带，请自行满足相应许可（GPL 需提供源码）。

---

## 四、编译期依赖（`provided`，不打进产物）

| 组件 | 版本 | 许可 |
|---|---|---|
| paper-api | 1.21.6-R0.1-SNAPSHOT | GPL-3.0（PaperMC）；社区惯例视编译期依赖不约束插件本体许可 |
| org.jetbrains:annotations | 26.0.2 | Apache-2.0 |
| Gradle Wrapper | — | Apache-2.0 |

---

## 五、许可溯源方法（可复核）

| 结论 | 验证方式 |
|---|---|
| 48 张贴图来自 ExlineFishing | 对 52 张同名贴图逐一比对 SHA256，48 张字节完全相同 |
| 48 个模型为命名空间改编 | 逐文件比对 JSON 结构，仅 `exlinefishing:` → `newfish:` 差异 |
| 未复制数据包文件 | `grep -r "fishing_expansion" src/` 零命中 |
| 附魔与地图类型对应上游 | 6/7 附魔 ID、5/5 地图类型与数据包完全一致 |

---

_本文件仅为工程侧的许可梳理，不构成法律意见；对外分发或商业化前建议自行复核或咨询专业人士。_
