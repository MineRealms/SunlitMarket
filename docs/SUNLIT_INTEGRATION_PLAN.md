# Sunlit Valley × SunlitMarket 深度整合方案

状态：D1–D4（S1/S2）与 S3 联动已落地；S4 已上线到生产服（jar sha256 `7fd1a3e6…`，脚本 239/239 无错），S0 首轮数据已量出（见文末「S0 实测」）。
运行时验收清单见 `TESTING.md` §17–§18。四个待定项已由作者拍板：1 关钱包、2 改经济日、3 身份识别由实现方定、4 接受仓边界由实现方推荐。

调研基准：主实例 `G:\MinecraftGames\Sunlit Valley(BaopuEdition)` 的 kubejs 脚本与 `assets/society/lang`；jar 为 11-02 旧版（无 wallet/deposit/audit/treasury/futures）。

---

## 0. 一句话结论

整合包的钱、日、人三套坐标系都和 SunlitMarket 不一致。先把这三处对齐（D1–D3），再补一条接受仓边界（D4），最后才是好感度/HUD/任务这些联动——顺序反了会把钱和日结搞乱。

---

## D1 钱包关闭，统一走 Numismatics 银行

**决定**：整合包内 `wallet.enabled=false`，SunlitMarket 的收支全部落在银行账户/硬币上。

改动点（mod）：

1. `currency/Currency.java#balance()` 目前**无条件**加 `Wallet.balance()`。钱包关闭时必须忽略钱包余额，否则旧的 DEV 余额会继续被算进可支配资金。
2. `client/DepositBinScreen.java` 钱包带（余额 / 手续费 / 存入·取出）在 `walletEnabled=false` 时整条不画，不占布局高度。
3. `wallet/WalletExchange.java` 在关闭时直接返回「钱包已停用」，`/sunlitmarket wallet deposit|withdraw` 一并拒绝。
4. 迁移命令 `/sunlitmarket wallet migrate`：把钱包余额一次性转进 `global.GLOBAL_BANK` 对应账户（无余额则无操作）。只有在「DEV 版 jar 曾被放进服务器」的情况下才需要跑；跑完把钱包清零。

**为什么不会颠覆现有经济**：整合包的钱从不经过钱包——出货箱 `account.deposit(value)`、村民商店扣款、医院债务都直接走银行。关钱包只影响 SunlitMarket 自己的记账口径。

**唯一行为变化**：饰品栏插了 Numismatics 卡的玩家，SunlitMarket 的扣款/付款以后走卡片账户（和出货箱同一个账户），余额本身不变。

---

## D2 经济日跟随可见天数，只增不减

**现状**：`EconomyClock.tick()` 取 `getGameTime()`（只在服务器 tick 时 +1，睡觉不动）。整合包的 `global.getDay()` 取 `dayTime/24000`（睡觉推进、`/time add` 也推进）。玩家每晚都睡，两边会越差越远。

**改动**（`core/EconomyClock.java` + 新 `core/EconomyClockData.java`，SavedData `sunlitmarket_clock`）：

```
tick = max(dayTime, highWater)      // highWater 持久化
if (tick > highWater) highWater = tick
day = tick / 24000
```

- 睡觉推进日结（和出货箱早上结算、季节、`global.getDay` 对齐）。
- `/time set` 往回调不会重复日结（highWater 挡住）。
- 蜡烛桶、期货到期、回收预算、当铺日结全部沿用同一个 tick，不需要各自改。

**迁移**：首次加载时 `highWater = max(dayTime, 现有 fills 的最大 tick, 当前 gameTime)`。旧存档的 fills 用 gameTime 记，dayTime 一般 ≥ gameTime，所以新数据只会落在旧数据之后，K 线不会插进历史中间。

**风险**：这是全局时钟，改动会影响所有依赖它的子系统。必须先写单测（睡觉 / 回拨 / 跨重启三态）再上。

---

## D3 NPC 身份：按 `dialog.npc.<id>.name` 认，不按职业

**现状**：`MarketRules` 只有 `tag / npcType / entity / profession` 四个选择器；`svCustomers.js` 用原版职业匹配。

**为什么职业不可靠**：整合包用 `assets/society/lang` 重命名了原版职业，而且 zh_cn 和 en_us **互相矛盾**：

| profession id | zh_cn | en_us |
|---|---|---|
| mason | 机械师 | Mechanic |
| weaponsmith | 储物匠 | **Librarian** |
| librarian | 图书管理员 | Librarian |
| cleric | 牧师 | Witch |
| fletcher | 异域商人 | Exotic Trader |
| cartographer | 银行家 | Banker |
| toolsmith | 铁匠 | Blacksmith |

同一张表在两个语言里能对出两个「图书管理员」。而真正有名字的 NPC 是 `easy_npc:humanoid`，身份写在自定义名里（`dialog.npc.banker.name`），整合包自己的 `npcInteraction.js#getNpcKey` 就是从这个字符串里抠 id 的。

**决定**：mod 增加 `npcKey` 选择器，职业只作为无名字的环境村民兜底。

改动点：

1. `market/Merchant.java` 加 `public String npcKey = ""`，`fromJson` 读取。
2. `market/MarketRegistry.java#resolve()`：tag → npcKey → npcType → persistentData。
3. `core/MarketRules.java#matchesNpc()`：加 `npcKey` 选择器（和 tag 并列，命中即算）。
4. 取值方式与整合包一致：把 `entity.getCustomName()` 序列化成 JSON，正则抠 `dialog\.npc\.([a-z_]+)\.name`——**不要**用 `getName().getString()`（服务端语言文件会把 translatable 解析成英文，zh_cn 客户端和服务器判定会不一致）。
5. `kjs/SunlitMarketBinding.java` 暴露 `SunlitMarket.npcKey(entity)`，脚本侧可直接判定。

**整合包侧**（`svCustomers.js`）：有名字的 10 个 NPC（banker / blacksmith / carpenter / fisher / librarian / market / shepherd / trader / witch / wise_oak）改成 `npcKey` 规则；职业规则只留给环境村民，并按 zh_cn 表修正（mason→机械师，fletcher→异域商人，weaponsmith→储物匠，cleric→牧师），删掉现在错误的 `mason → carpenter`。

---

## D4 接受仓 vs 出货箱：屏蔽，不降价

**问题**：接受仓没配价格的物品会回落到「商人收购价」，而 `meMerchants.js` 给 Sunlit 农产品的收购价是出货价的 1.02 倍 → 接受仓立刻按 102% 付钱，比出货箱还划算，且即时到账、不扣债务。

**推荐：直接屏蔽出货箱能卖的物品。**

理由：

- 这些物品已经有更好的渠道（出货箱），而且出货箱是整合包自己的经济闭环，接受仓插进去只会分流并抬价。
- 「允许但价格 ≤ 出货价」需要一个逐物品的价格钳制表，而 `global.trades` 有上千条、还带品质倍率（`calculateQualityValue` 1.25/1.5/2/3），钳制表会长期漂移。
- 屏蔽是一条规则，不会随版本漂移。

**实现**：

1. `deposit_values.json` 的 `items` 支持 **-1 = 明确拒收**（现在 0 表示「不覆盖，继续回落到商人收购价」，语义不够）。
2. `deposit/DepositValues.java#itemUnit()`：配置值 < 0 时直接返回 0 并标记拒收（不进 `marketUnit` 兜底）。
3. 新绑定 `SunlitMarket.depositBlock(itemId)`。
4. 整合包脚本启动时遍历 `global.trades`（`globalRegistry.js:2021` 定义），对每个 key 调 `depositBlock`。
5. 接受仓剩余的五路（流体 / FE / 源质 / 魔力 / 未定价物品）保留——那些正是出货箱不处理的产能出口。

**副作用**：接受仓收入下降，回收预算（`buyback`）可以维持现值不动。

---

## E. 深度联动（按价值排序，D1–D4 之后再做）

1. **好感度 → 结盟值**：`player.persistentData.npcData[<id>].friendship`（100 点一颗心，`maxGifted` 在 500）映射到 `society:<id>` 派系。`invited_<id>` 阶段决定该顾客是否出现（`svMastery.js` 已有 invited_* → 解锁的先例）。同步时机：成交后 + 玩家登录，不要每 tick。
2. **M0 统计**：照 `serverEvents/updateCoinLeaderboard.js` 的做法遍历 `global.GLOBAL_BANK` 账户求和，喂给审计；`global.persistentData.debts` 里的债务单独记一项。审计里的 M0 就不再是 UNKNOWN。
3. **HUD**：`SunlitHud.provider('sunlitmarket', fn)` 显示余额 / 通缉值 / 指数。纯 KubeJS，不动 mod。
4. **任务联动**：`SunlitMarketEvents.afterTrade` → `player.stages.add(...)`；已有的 22 个阶段任务和 mastery 同步机制复用。
5. **对话入口**：dialog 的 entry 支持 `"command"`（现有 `openshop @p banker`）。**实际落地时改了方向**：整合包的 10 个有名字 NPC 在 `svCustomers.js` 里是柜台的**顾客**（`npcKey` + `pool` 需求），不是 SunlitMarket 的商人，`/sunlitmarket near` 在他们身上解析不到商人。所以对话里开的是**行情面板**（`sunlitmarket chart`），交易仍然走柜台方块。权限侧：根节点降到 0，写操作单独挂 2。
6. **债务一致**：SunlitMarket 给玩家付款（货架收购、接受仓）时，先扣 `server.persistentData.debts`，再入账，和出货箱 `handleShippingBinDebt` 一致。否则玩家可以用当铺绕开医院债务。

---

## 分阶段实施

| 阶段 | 内容 | 验收 |
|---|---|---|
| S0 校准（只读） ✅ | 服务器银行总额、活跃玩家数、日均出货额；扫中州硬币来源 | 已量出 M0=46805902 / 账户 12 / 债务 176128，见文末；C/R 建议值待作者拍板 |
| S1 三处对齐 | D1 钱包 + D2 经济日 + D3 npcKey（mod）；`svCustomers.js` 改 npcKey + 修职业表 | 单测：睡觉推进日结 / 回拨不重复 / 跨重启；DEV 客户端右键 easy_npc 能开面板 |
| S2 接受仓边界 | `-1` 语义 + `depositBlock` + 包脚本遍历 `global.trades` | DEV：出货箱能卖的物品放进去 → 合计 0、按钮置灰 |
| S3 联动 ✅ | 好感度→结盟、M0、HUD、对话入口、债务先扣（mod：`PayoutSink` / `auditSupply` / 权限拆分） | 代码+单测完成，见 `TESTING.md` §18 的 6 条待验 |
| S4 部署 ⏳ | jar + config + 脚本 + 上线说明（包已出：`server-pack-20261103-sunlitmarket/`，增量于 20261102） | 服务器首日 M0/债务/日结三条曲线不跳变 |

**C（当铺现金）与 R（发行准备金）不在本方案里拍数字**：C 决定「玩家能不能把东西卖给当铺」，设低了等于关掉收购。S0 量出 M0 后按 C ≈ 2–5% M0、R ≈ 1 个活跃日的回收流水给建议，再由作者定。

---

## 风险

- **D2 是全局时钟**：蜡烛桶、期货到期、回收预算、日结全挂在上面。旧存档迁移必须先算 highWater 再写第一条 fill，否则 K 线会插进历史中间。
- **D1 的钱包迁移**：只有在生产环境真的跑过带 wallet 的 jar 时才需要。迁移是一次性命令，不删任何数据。
- **D3 的名字解析**：easy_npc 的自定义名可能是 `{"translate":...}` 也可能是 `{"text":...}`，两种都要能抠出 id；抠不到时回落到 tag/npcType，不要抛异常。
- **中州硬币**：面值 1/8/64 已注册，但来源未确认。若结构箱子会掉，等于第二个印钞口，需要在 S0 一起扫。

---

## 待补数据（S0 产出）

1. ~~服务器 `GLOBAL_BANK` 总余额、账户数、前 10 名分布~~ ✅ 见下。
2. ~~`global.persistentData.debts` 总额~~ ✅ 见下。
3. 日均出货箱结算额（决定 R）—— 仍缺，需要至少一个活跃日的流水。
4. 中州硬币的 loot table / 结构箱子来源 —— 仍缺。

---

## S0 实测（2026-11-03，生产服只读）

`SunlitMarket.auditSupply` 上报 + 一次性探测脚本（已删除）拿到：

| 指标 | 值 | 说明 |
|---|---|---|
| M0（`GLOBAL_BANK` 总额） | **46,805,902** | 12 个账户，8 个非零 |
| 前 4 名余额 | 14,163,673 / 13,325,577 / 10,823,921 / 8,383,906 | 合计 46.66 M，**占 M0 的 99.7%** |
| 其余非零 | 91,427 / 17,318 / 64 / 16 | 长尾几乎为零 |
| 未偿债务 | **176,128** | `persistentData.debts`，占 M0 0.38% |
| 当铺现金 C | 200,000 | `cashTarget`=200k、`cashCeiling`=400k、`dailyRefill`=100k |
| 发行准备金 R | 5,000,000 | 占 M0 10.7% |
| NPC 日预算 | 50,000 | `npcDailyBudget`，当日已花 0 |
| 经济日 | 3434 | |

**关键结论：M0 高度集中。** 四个人占了 99.7%，第五名只有 9.1 万。所以「按 M0 的百分比定 C」要小心：

- 按 2–5% M0 算 C = 0.94 M – 2.34 M，这个量级是**对着鲸鱼的钱包**定的，不是对着普通玩家。
- 但 C 的实际作用只是「当铺当天能收多少货」。现在 C=200k、日补给 100k、上限 400k，对第五名（91k）这种
  中等玩家是够的，对鲸鱼一次清仓就顶穿。

**建议（不写死，交作者定）**

| 参数 | 现在 | 建议 | 理由 |
|---|---|---|---|
| `cashTarget` C | 200,000 | **1,000,000**（2.1% M0） | 让鲸鱼一次能卖出千级货而不被日补给卡住；仍远小于单鲸余额，不会变成提款机 |
| `cashCeiling` | 400,000 | **2,000,000** | 上限跟着 target 走，留 2 倍缓冲 |
| `dailyRefill` | 100,000 | **200,000** | 5 天从 0 回满，和现在的手感一致 |
| `npcDailyBudget` | 50,000 | 暂不动 | 顾客侧消耗本来就慢（当日 0），等有流水再说 |
| R | 5,000,000 | 暂不动 | 已覆盖 10 个 NPC 日预算（500k/日）的 10 天量；等量出真实日结算额再调 |

先只调 C 三件套，观察一个活跃日：如果 `npcSpentToday` 长期是 0、而柜台收购被 C 卡住，说明瓶颈在 C；
如果 C 常年用不完，说明瓶颈在玩家供给，不用再加钱。
