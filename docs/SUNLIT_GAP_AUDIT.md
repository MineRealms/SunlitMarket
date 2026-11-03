# SunlitMarket × 整合包：联动缺口与可拓展点

状态：2026-11-03，基于生产服（Mohist 1.20.1-ecf8d5ad / Java 21）实测 + 代码/配置核对。
配套文档：`TESTING.md` §18（已验证项）、`docs/SUNLIT_INTEGRATION_PLAN.md`（S0–S4 与 C/R 建议）。

---

## 一、已经打通的联动（有运行时证据）

| 联动 | 落点 | 证据 |
|---|---|---|
| 出货箱价格锚 | `svCustomers.js` → `MarketRules` pool 的 `marketValue/askPrice` | 120 规则 / 105 货池加载，0 坏池 |
| 好感度 → 结盟值 | `svFriendship.js` | `friendship -> alignment bridge installed` |
| 付款先还债 | `svPayout.js` → `Currency.observedSettle` | `debt-first payout sink installed` |
| M0 / 债务上报 | `svMoneySupply.js` → `EconomyAudit` | `M0=46805902 debts=176128` |
| 成交 → 任务阶段 | `svTradeStages.js` | `trade stage hook installed` |
| 季节价格 | `meSeasonPrices.js`（SereneSeasons） | `seasonal multipliers applied: summer@93%` |
| 价格指数 | `svIndices.js` | `/sunlitmarket index` 返回三条 |
| 接受仓五路 | 物品 / 流体 / FE / 源质 / 魔力 | `DepositCapabilities` 五个 capability |
| 主题商人 | 匠魂 / 神秘 / 星辉 / 植物魔法 | 10 个商人已注册 |
| 债务、通缉、结盟值 | `AlignmentData` + `FactionRelations` | 16 派系快照 |

---

## 二、还没搞定的（按影响排序）

### 1. 商人 NPC 在服务器上没有落点（阻塞「中州 NPC 交易」）

- 6 位中州商人 + 4 位主题商人都是**靠实体 tag 匹配**的（`sunlitmarket.me.<id>` / `sunlitmarket.theme.<x>`），
  不是自然生成，也不是 `/sunlitmarket near` 能凭空找到的东西。
- 生产服当前扫到的实体里 **0 个带 `sunlitmarket.*` tag**（0 玩家在线时只有 41 个已加载实体，属未加载区块，结论只能算「未确认」）。
- 影响：没有商人 → 指数成员货架不成立 → `sv.produce / sv.metal / sv.delicacy` 永远 `n/a`；
  交易章节里「中州商路」这一支玩家实际走不通。
- 需要的动作：在地图上定点放置 10 个商人（`/summon me:npc ~ ~ ~ {Tags:["sunlitmarket.me.shire_farmer"],InitializationData:{Type:"me:dale.peasant"}}`），
  并确认它们的货架在 `MarketRegistry.all()` 里。

### 2. 中州派系与当铺派系是两套坐标系

- 中州（ME）那边玩家有自己的 `faction`（human/elf/dwarf/...），SunlitMarket 这边是 `society:<id>` 的结盟值。
- 已有只读桥 `MiddleEarthCompat.savedFaction()`（`SunlitMarket.playerFaction(player)` 可读），但**没有任何地方拿它去初始化结盟值**。
- 影响：玩家在中州选了什么阵营，当铺完全不认；「同族商人更便宜」这类玩法做不了。
- 建议：登录时按 ME faction 给对应 `society:*` 一个初始结盟值（例如 +50），只补不扣。

### 3. 接受仓的物品通道几乎不可用

- `deposit_values.json` 里 2337 项**全部是 -1（拒收）**——那是出货箱的整张价目表。
- 剩下能卖的只有没被 -1 覆盖、且商人货架有收购价的物品：实测 70 条商人 offer 里只有 21 条还能卖进接受仓，
  且集中在星辉/植物魔法/匠魂/神秘的材料与中州硬币。
- 影响：「接受仓收多余物品」这条玩法实际上不存在，玩家只能走流体/能量/源质/魔力四路。
- 要么承认这是设计（接受仓 = 能量出口），要么补一张真正的「非出货箱物品」价目表。

### 4. 源质按 1:1 计价，稀有要素没有区分

- `essentia.aspects` 是空表，全部要素 1 点 = 1 spur。
- 影响：把 auram（灵气）和 ignis（火）卖一个价，等于鼓励玩家只产最容易的要素。
- 建议：按要素稀有度补表（灵气/虚空/时空 > 复合要素 > 基础要素）。

### 5. HUD 面板在生产服被跳过

- 服务器的 `sunlithud` 是 0.1.0，没有 `provider` API，`svHud.js` 检测后整段跳过。
- 影响：余额 / 结盟称号 / 通缉值不在 HUD 上；只能用 `/sunlitmarket balance` 查。
- 动作：把新版 sunlithud 放进服务器 mods（客户端也要同步）。

### 6. 期货只有指数，没有物品

- `/sunlitfutures` 的标的是 `sv.produce` 这类指数；没有「1000 个铁锭在 X 日交割」这种实物合约。
- 影响：期货只能对赌行情，不能做套保；对生产型玩家价值有限。

### 7. 没有玩家之间的现货市场

- 现在玩家间交易只有柜台（NPC 顾客）和期货（指数对赌）。
- 没有挂单/拍卖/寄售，玩家之间的物品流通仍然靠丢箱子和喊话。

### 8. 结盟值共享依赖 FTB Teams，真实双人队伍未验

- `sharedAlignment` 默认 true，key = 队伍 UUID 或玩家 UUID。
- 单测与单人环境都通过，但**没有在真实双人队伍里验过**「一个人涨、另一个人看到」。

### 9. 柜台/商人 GUI 的视觉验收一直没做

- 按约定 GUI 由作者亲自开面板验收，至今没有做。功能路径（开面板、成交、扣款）在代码与单测里覆盖。

---

## 三、可以拓展的地方（按性价比排序）

1. **商人落点 + 向导牌**：给 10 位商人做定点（每族一个集市），配路牌写清收购什么。这是让「贸易章节」真正可玩的前提。
2. **ME 派系 → 初始结盟值**：见二.2，改动很小（登录事件里一次写入），体验提升明显。
3. **要素分级定价**：见二.4，纯配置。
4. **实物期货**：在 `FuturesSpecs` 里加 item 标的（需要新增交割逻辑），能让生产玩家套保。
5. **寄售行**：柜台加一个「玩家寄售」页签，成交抽税进 R。这是把 M0 从鲸鱼手里重新分配的最直接手段（当前 4 个号占 99.7%）。
6. **中州硬币来源收口**：中州硬币面值 1/8/64 已注册，但来源（结构箱子？）没确认；若会掉，等于第二个印钞口，需要在 S0 的「中州硬币来源」那一项上补扫。
7. **接受仓物品价目表**：见二.3，如果要让物品通道活着，需要一张独立于 `global.trades` 的表。
8. **HUD 升级**：把 sunlithud 换新版后，余额/称号/通缉常驻显示。
9. **行情面板加「我的持仓」页**：期货仓位与到期日，现在只能靠命令查。
