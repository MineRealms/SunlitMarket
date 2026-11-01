# SunlitMarket 阵营结盟值改造 —— 设计报告

> 版本：2026-11-02 · 基线：SunlitMarket 0.1.0（1529 行 Java + 5 个 KubeJS 脚本）
> 目标：把现有的「商人声望」升级成「玩家 × 派系 结盟值」，并让 ME / Sunlit / 其他玩家的 NPC 与玩家成为可交易的顾客。

---

## 0. 摘要

现有系统是**货架型商店**：商人有固定商品，玩家买或卖，价格由商人定。
目标系统是**当铺 + 派系政治**：顾客带着东西上门，玩家定价收购，同时每一笔交易都在 17 个中洲派系里累积或消耗结盟值。

三件事必须一起做，缺一件整个循环不成立：

1. **结盟值**：从「商人声望」升级成「玩家 × 派系带符号数值」，支持队伍维度、负值、阈值。
2. **顾客身份**：ME NPC / Sunlit NPC / 玩家，各自解析出所属派系。
3. **方向翻转**：从「玩家买货架」改成「顾客上门求售 + 玩家出价」。

---

## 1. 现状基线（实测，非推测）

### 1.1 已有的东西

| 模块 | 实现 | 关键约束 |
|---|---|---|
| `ReputationData` | SavedData `sunlitmarket_reputation`，`Map<UUID, Map<String,Integer>>` | 无上下限、无队伍维度、无时间衰减 |
| 声望影响价格 | `MarketService.buyPrice`：`discount = min(0.10, max(0,rep) * 0.001)` | 100 点封顶 10% 折扣 |
| 声望影响售价 | `sellPrice`：`bonus = min(0.05, max(0,rep) * 0.0005)` | 100 点封顶 5% 加成 |
| 声望门槛 | `Offer.reputation`，不足则整条拒绝 | 硬门槛，无梯度 |
| 成交涨声望 | `Offer.repGain * qty` | 只有涨，没有跌 |
| 每日库存 | `StockData`，键 `merchant#index` | 按 `dayTime/24000` roll |
| 每日限购 | `LimitData`，键同上 | 按玩家、按天 |
| 解锁门槛 | `UnlockData` + `Offer.unlock` | 字符串 id |
| 价格倍率 | `PriceModifiers` + KubeJS `priceMultiplier` | 按 merchant+item |
| 商人识别 | tag → `NpcData.Type` → ForgeData | 已支持 ME |
| UI | `MarketScreen` 113 行，8 行固定列表，无滚动、无贴图 | 纯文字按钮 |

### 1.2 缺的东西

- 结盟值没有**负值语义**（价格公式里 `max(0,rep)` 直接吃掉了负数）
- 没有**队伍维度**（FTB Teams）
- 没有**阈值表**（只有 `Offer.reputation` 这一个单点门槛）
- 没有**扣分路径**（宰客、收赃物、卖违禁品都不掉声望）
- 没有**派系外交**（不知道刚铎和魔多敌对）
- 没有**顾客概念**（只有"商人"）
- 没有**收购请求**（只有固定货架）

### 1.3 运行环境

| 组件 | 主客户端 | -DEV 实例 |
|---|---|---|
| Middle-earth 1.4.2-1.20.1-port.0 | ✅ | ✅ |
| Connector 1.0.0-beta.49 | ✅ | ✅ |
| ftb-teams-forge 2001.3.2 | ✅ | ❌ |
| ftb-library-forge 2001.2.12 | ✅ | ❌ |
| CreateNumismatics 1.1.0 | ✅ | ❌ |

**结论**：队伍维度和货币相关的东西只能在主客户端验，或者先把 FTB Teams + Numismatics 拷进 -DEV。建议后者——-DEV 就是拿来测的。

---

## 2. 目标模型

### 2.1 三层值

| 层 | 含义 | 载体 | 谁改 |
|---|---|---|---|
| **结盟值** | 你对某派系的长期立场，−1000…+2000 | `AlignmentData`（新） | 交易、宰客、收赃、杀人、任务 |
| **热度** | 单件物品的赃物嫌疑，0…100，每天 −18 | 物品 NBT | 收购赃物时写入，每日衰减 |
| **通缉度** | 你对执法派系的累计违法记录，0…∞ | `AlignmentData` 里的独立字段 | 收赃、卖违禁品 |

三层互相独立。结盟值是**派系对你**的态度，热度是**物品**的属性，通缉度是**执法系统**对你的记忆。

### 2.2 为什么不用 ME 自己的阵营

ME 的 `PlayerData` 只有 `faction / spawn / race / origin` 四个字段，**没有数值**。NPC 打不打你只看 `Faction.isHostileToward(playerFactionId)`，二元。

所以 ME 只提供两件事：
- 这个 NPC 属于哪个派系（从 `NpcData.Type` 前缀推）
- 玩家宣誓效忠了哪个派系（从 `middle_earth_player_datas` 读）

**数值完全由 SunlitMarket 自己管**，不改 ME、不 Mixin、不 import。

---

## 3. 数据层设计

### 3.1 新类 `AlignmentData`

替换 `ReputationData`（保留旧类做迁移源）。

```java
// SavedData 名：sunlitmarket_alignment
public final class AlignmentData extends SavedData {
    // 主体：按"归属者"存，归属者 = 玩家 UUID 或 队伍 UUID
    private final Map<UUID, Entry> values;

    public static final class Entry {
        Map<String, Integer> alignment;  // factionId -> -1000..+2000
        int heatRecord;                  // 通缉度
        long lastDecayDay;               // 上次衰减的游戏日
    }

    // 读：先看队伍，没有队伍再回落到玩家
    public int get(ServerPlayer player, String faction);
    public int getByOwner(UUID owner, String faction);

    // 写：写到自己所属的 owner 上
    public void add(ServerPlayer player, String faction, int delta);
    public void set(ServerPlayer player, String faction, int value);

    // 批量：双降规则用
    public void addMany(ServerPlayer player, Map<String,Integer> deltas);

    // 每日衰减（结盟值向 0 回归 1 点/天，通缉度 −2/天）
    public void roll(long day, MinecraftServer server);

    // 枚举某玩家所有非零派系，给 UI 用
    public Map<String,Integer> snapshot(ServerPlayer player);
}
```

### 3.2 队伍维度

**owner 解析规则**（`AlignmentData.resolveOwner`）：

1. 如果 FTB Teams 已加载 **且** 玩家在队伍里 **且** 队伍人数 > 1 → owner = team UUID
2. 否则 → owner = player UUID

这样单人玩家和多人队伍走同一套存储，不需要两套代码。

**为什么不用"队伍展开成成员 UUID"**：成员变动（加入/退出/解散）时要迁移全部数据，而且退出队伍会带走队伍结盟值。用 team UUID 做 owner 只需要在退出时把该玩家的贡献归零或保留快照。

**FTB Teams 接入方式**：`compileOnly` + 独立 `compat/FtbTeamsCompat` 类，内部反射，类加载用 `ModList.get().isLoaded("ftbteams")` 守门。理由和 ME 一致：不让 mod 硬依赖，也不进 Mixin。

待确认（运行时验证）：2001.3.2 的入口是 `FTBTeamsAPI.getTeamManager()` 还是 `TeamManager` 静态方法，`Team#getId()` 返回 `UUID`。这一步在写代码前先用桥接探一次。

### 3.3 迁移

`sunlitmarket_reputation` → `sunlitmarket_alignment`：

- 首次加载时，如果新数据为空且旧数据存在，逐条复制，`alignment = oldValue`，`heatRecord = 0`
- 旧数据不删除（回滚用），但不再读写
- 迁移写一行日志：`[SunlitMarket] migrated N players from reputation to alignment`

### 3.4 每日衰减

挂在 `ServerEvents.loaded` 不够（不跨天）。用 `ServerTickEvent.Post`，每 200 tick 检查一次 `dayTime/24000` 是否变化：

- 结盟值：每过 1 天，非零值向 0 靠拢 1 点（正负都向 0）
- 通缉度：每天 −2
- 物品热度：由物品自己带（见 5.3），在玩家背包里也衰减

衰减必须**可配置**，因为测试时需要关掉。

---

## 4. 身份层：谁是顾客

### 4.1 ME NPC → 派系

`NpcData.Type` 的命名是 `<faction>.<rank>`，且子派系用点号续接：

| npcType | 派系 | 备注 |
|---|---|---|
| `me:shire.peasant` | `me:shire` | |
| `me:gondor.militia` | `me:gondor` | |
| `me:longbeards.erebor.miner` | `me:longbeards.erebor` | 子派系，父级 `me:longbeards` |
| `me:hobgoblin_tribes.gundabad.goblin` | `me:hobgoblin_tribes.gundabad` | 两级 |
| `me:woodland_realm.artisan` | `me:woodland_realm` | |

**推导算法**（不能简单切第一个点，因为子派系）：

1. 取 `data/me/me/faction/*.json` 的全部 id 集合（启动时扫一次资源包，或硬编码 17 个）
2. 对 npcType 做**最长前缀匹配**：`me:longbeards.erebor.miner` 同时匹配 `me:longbeards` 和 `me:longbeards.erebor`，取长的
3. 匹配不到 → 无派系（`brigand` 之外的野怪、Shelob 后代等）

**为什么读 JSON 而不是硬编码**：ME 更新会加派系；读资源包只需要一次 `ResourceManager` 遍历，且不 import ME。

### 4.2 顾客资格

ME 的 `faction.json` 里有 `npc.ranks`：

```
CIVILIAN → 只能是顾客
MILITIA / SOLDIER / KNIGHT / VETERAN / LEADER → 不能当顾客，只按敌我判定
```

规则：**只有 CIVILIAN 档的 NPC 会触发收购请求**。否则敌对派系的士兵走进柜台会直接砍人，玩法崩掉。

### 4.3 Sunlit NPC

现有 Sunlit 侧的 NPC（非 ME）没有阵营概念。方案：复用实体 tag 约定

```
sunlitmarket.faction.<factionId>   // 例：sunlitmarket.faction.sunlitmarket:village
sunlitmarket.merchant.<id>         // 现有
```

不需要新机制，`MarketRegistry.resolve` 里已经有 tag 扫描。

### 4.4 其他玩家

玩家作为顾客 = 玩家 A 把东西卖给玩家 B 的店。这需要：

- 店铺所有权（`ShopData`：pos → owner UUID）
- 玩家 A 走到 B 的柜台 → 打开"求售"界面

**建议放到最后一阶段**，因为它引入 PvP 经济，风险最高（刷钱、串通、脚本）。

FTB Teams 在这里的作用不是"顾客分组"，而是**店铺归属**：一个队伍共有一个店，结盟值也共享。

### 4.5 玩家自己的派系

从 `middle_earth_player_datas` 读（SavedData，键 `list[].uuid/data/faction`）。不 import ME。

用途：
- 你效忠刚铎时，刚铎结盟值涨得更快（LoTR 的 pledge 机制）
- 敌对派系商人直接拒绝服务

---

## 5. 数值设计

### 5.1 结盟值阈值表

参考 LoTR 的分档，缩放到 MC 的交易频次（一次交易 10–200 spur）：

| 区间 | 称号 | 效果 |
|---|---|---|
| ≤ −500 | 死敌 | 该派系 NPC 见你就跑；商人永久拒绝 |
| −499 … −100 | 敌对 | 该派系商人拒绝交易；士兵主动攻击 |
| −99 … −1 | 冷淡 | 价格 +15%，无收购请求 |
| 0 | 中立 | 基准价 |
| +1 … +99 | 熟客 | 价格 −5%，可接小额收购请求 |
| +100 … +499 | 常客 | 价格 −10%，可接大额订单，解锁该派系专属货 |
| +500 … +999 | 盟友 | 价格 −15%，优先供货，可雇佣护卫 |
| ≥ +1000 | 挚友 | 价格 −20%，可买该派系旗帜/坐骑 |

**与现有代码的差异**：现在是 `min(0.10, rep*0.001)` 线性，改成**阶梯**。阶梯的好处是玩家能明确知道"再涨 20 点就进阶"，线性曲线感觉不到。

### 5.2 结盟值增减

| 行为 | 变化 |
|---|---|
| 公平成交（成交价 ≥ 市场价 90%） | 该派系 +`ceil(成交额 / 200)` |
| 压价收购（< 市场价 60%） | 该派系 −`ceil(差价 / 100)` |
| 让价出售（< 市场价 80%） | 该派系 +`ceil(差价 / 50)` |
| 收购赃物 | 该派系 −2，通缉度 +5 |
| 出售违禁品 | 通缉度 +`tier * 5` |
| 击杀该派系 NPC | −25（一次性） |
| 击杀该派系的敌对派系 NPC | +5，且只在**该派系势力范围内**生效 |

「势力范围」用 ME 的 `faction.json` 里的 `spawns.data[].coordinates` 做半径判定（每个据点 512 格）。这一条是 LoTR 的精髓，也是唯一需要读 ME 世界数据的部分——纯 JSON + 坐标，不 import。

### 5.3 双降规则

读 `faction.json` 的 `initial_diplomacy`，构建对称关系表（`HOSTILE` 边双向）。

规则：一次正向变动 `+d` 作用于派系 A 时，若 A 与 B 是 `HOSTILE`，且玩家对 B 的结盟值 > 0，则 B 的变动为 `-ceil(d * 0.5)`。

例：你给刚铎交了 200 spur 的税（+1 刚铎），同时魔多 −1（因为魔多恨刚铎）。

这张表在 `ServerStartedEvent` 加载一次，缓存在 `FactionRelations` 里。

### 5.4 物品热度

沿用 Probably Stolen 的表：

| 物品基础价值 | 初始热度 | 每晚 −18 后干净 |
|---|---|---|
| 18–49 | 25 | 2 晚 |
| 50–84 | 50 | 3 晚 |
| 85–139 | 75 | 5 晚 |
| ≥140 | 100 | 6 晚 |

载体：物品 NBT `sunlitmarket:heat`（int）+ `sunlitmarket:stolen`（byte）。
衰减：玩家背包/容器里的物品每天 −18，归零后移除 `stolen` 标记。

风险：每次衰减要遍历所有玩家的背包 + 所有已加载容器。玩家多的时候是性能问题。**建议第一版只遍历玩家背包**，容器里的不衰减（也算合理：藏起来的东西"保鲜"）。

---

## 6. 交易改造：从货架到收购请求

这是最大的一块，也是整个改造的地基。

### 6.1 现在

`Offer` = 商人货架上的固定商品。玩家点"买"付钱拿货，点"卖"交货拿钱。价格商人定。

### 6.2 目标

`Offer` 拆成两种：

```json
// 货架（保留，商人的固定商品）
{ "kind": "stock", "item": "me:leek", "count": 1, "buy": 14, "sell": 8, "stock": 16 }

// 收购请求（新，顾客带来的东西）
{ "kind": "offer", "item": "me:silver_coin", "count": 3,
  "baseValue": 24, "marketValue": 30, "askPrice": 36 }
```

收购请求的三价：

- `baseValue`：物品自身参考值（可以用现有 `coinValue` + 一个物品估值表）
- `marketValue`：受季节、声望、事件影响的实时估值
- `askPrice`：顾客开价（> marketValue 时说明他想宰你）

玩家可以：接受 / 还价（−10%/−20%）/ 拒绝。
还价成功率 = `clamp(0.5 + 结盟值/1000 - (ask-market)/market, 0.1, 0.9)`。

失败 → 顾客走人，该派系 −1。

### 6.3 兼容性

`Offer` 加 `kind` 字段，缺省 `"stock"`，老 JSON 和现有 KubeJS `SunlitMarket.merchant()` 完全不用改。这是刻意的——现有 6 个 ME 商人 + 2 个主题商人继续工作。

---

## 7. 顾客上门

### 7.1 柜台方块

新增方块 `sunlitmarket:counter`：

- 右键 → 打开"求售"界面（当前排队中的收购请求）
- 周围 8 格内有 CIVILIAN 档的 ME NPC → 该 NPC 每隔 N tick 生成一条收购请求（上限 3 条）
- 请求超时（1200 tick）自动消失，NPC 离开

**不做寻路**。NPC 只需要在范围内，不要求走到柜台前。这样避免 AI 改动、避免区块卸载导致 NPC 卡住。

### 7.2 请求生成

```
1. 取范围内所有 CIVILIAN NPC
2. 每个 NPC 按派系查"该派系卖什么"（现有 merchant 定义里的 offers）
3. 随机挑 1–3 件，生成 baseValue/marketValue/askPrice
4. askPrice = marketValue * (1 + 随机 -0.15..+0.35)
5. 写入 ShopData 的队列
```

复用现有 `Merchant.offers` 作为商品池，不新增数据。

### 7.3 为什么不用 ME 的对话

ME 的 NPC 右键会走它自己的对话。用柜台方块可以完全避开这个冲突，也不用 Mixin 进 ME。

---

## 8. UI

现在 `MarketScreen` 113 行、8 行固定列表、无滚动。要加的东西：

| 界面 | 内容 | 工作量 |
|---|---|---|
| 求售界面（柜台） | 3 条请求，每条显示物品/三价/还价按钮 | 中 |
| 结盟值面板 | 17 个派系的当前值 + 称号 + 进度条 | 中 |
| 交易结算 | 成交/还价成功/还价失败的反馈 | 小 |
| 现有商店界面 | 加称号显示、加"结盟值不足"的具体差值 | 小 |

**必须加滚动**。17 个派系一屏放不下，8 行的硬上限也得改。

技术选择：继续用原生 `Screen` + `Button`（现在就是这样），不引入 libgui。理由：现在没依赖任何 UI 库，加一个库的维护成本比手写滚动大。

---

## 9. KubeJS 接口扩展

现有 24 个方法够用，需要补：

```javascript
SunlitMarket.alignment(player, faction)        // 读（队伍感知）
SunlitMarket.addAlignment(player, faction, n)  // 写（自动应用双降）
SunlitMarket.setAlignment(player, faction, n)
SunlitMarket.title(player, faction)            // 返回称号字符串
SunlitMarket.factionOf(entity)                 // ME NPC → 派系 id
SunlitMarket.playerFaction(player)             // 读 ME 效忠
SunlitMarket.heat(stack)                       // 读物品热度
SunlitMarket.markStolen(stack, heat)           // 标记赃物
SunlitMarket.wanted(player)                    // 通缉度
```

保留 `getReputation/setReputation/addReputation` 作为 `alignment` 的别名，老脚本不破。

---

## 10. 分阶段实施

每阶段独立可验收，不通过就不进下一阶段。

### S1 · 结盟值数据层（不改玩法）

- 新建 `AlignmentData`，替换 `ReputationData`
- 加迁移、加负值、加每日衰减（可配置开关）
- 加 `FactionRelations`（读 `data/me/me/faction/*.json`）
- 价格曲线从线性改成阶梯

**验收**：`/sunlitmarket rep` 能显示负值；关掉 ME 也能跑（无派系时全部为 0）；旧存档能迁移。

### S2 · 队伍维度

- `FtbTeamsCompat`（反射，无硬依赖）
- `resolveOwner` 规则
- 命令 `/sunlitmarket team on|off`

**验收**：两个玩家组队后，A 交易涨的结盟值 B 也能看到；解散队伍后回落。

**前置**：把 ftb-teams + ftb-library 拷进 -DEV。

### S3 · 顾客身份

- ME npcType → 派系（最长前缀匹配）
- CIVILIAN 资格判定
- `/sunlitmarket debug <entity>` 输出：派系、rank、结盟值、是否可当顾客

**验收**：站在夏尔村庄，debug 能正确说出"me:shire / CIVILIAN / 结盟值 +0 / 可交易"。

### S4 · 收购请求 + 柜台

- `Offer.kind`
- 三价 + 还价
- `sunlitmarket:counter` 方块 + 队列
- 求售 UI

**验收**：放一个柜台，周围有夏尔农民，能弹出 3 条收购请求，还价成功/失败都有正确反馈和结盟值变化。

### S5 · 赃物 + 违禁品 + 通缉度

- 物品 NBT 标记
- 热度每日衰减
- 违禁品 4 档（item tag）
- 通缉度累积

**验收**：收赃物后物品带 `sunlitmarket:stolen`，2 天后自动洗白；通缉度到阈值触发事件。

### S6 · 执法（可选，最后）

- 检查员 NPC（复用 ME 的治安派系，如 `shire.shirriff`）
- 检查逻辑：扫玩家背包/附近容器，命中则没收 + 罚金
- 贿赂（消耗通缉度）

**验收**：通缉度 > 20 时，治安 NPC 走近会触发检查。

---

## 11. 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| ME 更新改了 npcType 命名 | 派系推导全错 | 用最长前缀 + 从 JSON 读派系表，不硬编码 |
| FTB Teams API 版本漂移 | 编译不过或运行时 NoSuchMethod | 反射 + 只依赖 `Team#getId` 一个方法 |
| 物品热度遍历背包的性能 | 多人服务器卡顿 | 第一版只扫背包、每 200 tick 一次、可配置关闭 |
| 17 个派系 × 队伍维度 → UI 爆炸 | 玩家看不懂 | 只显示**非零**派系 + 当前交互派系 |
| 方向翻转破坏现有 6 个 ME 商人 | 玩家存量配置失效 | `kind` 缺省 `stock`，老 JSON 原样工作 |
| 玩家间交易刷钱 | 经济崩 | S6 之前不做玩家作为顾客 |

---

## 12. 待定（需要你拍板）

1. **结盟值上限**：LoTR 是 +1000/+1500 分档，我这里按交易频次缩到 +1000。如果你想让节奏更慢，可以拉到 +3000。
2. **玩家作为顾客**要不要做？做了就是 PvP 经济，风险最高，我建议放最后。
3. **通缉度**要不要真的做成"被没收"？还是只做价格惩罚（更温和）。
4. **柜台方块**是否接受？替代方案是"右键任何 ME 平民直接弹求售"，但那会覆盖 ME 自己的对话。

---

## 13. 下一步

如果按这个报告走，第一步是 S1。S1 不碰 UI、不碰 ME、不碰 FTB，纯数据层，风险最低，而且做完立刻能在游戏里看到"夏尔 +12 / 魔多 −6"这种数字——后面的所有系统都挂在这上面。

S1 预计改动：`AlignmentData.java`（新，约 220 行）、`FactionRelations.java`（新，约 120 行）、`MarketService` 价格曲线（改约 30 行）、`ReputationData` 降级为迁移源。


---

## 14. 决策记录（2026-11-02 已定）

| # | 议题 | 决定 |
|---|---|---|
| 1 | 结盟值上限 | **+3000**，慢节奏曲线。已在 `SunlitMarketConfig.ALIGNMENT_MAX` 默认值 |
| 2 | 玩家作为顾客 | **可选功能**，配置开关控制，默认关闭。排到 S7 |
| 3 | 通缉度后果 | **没收 + 价格惩罚都做**，两者都可在配置里单独关闭 |
| 4 | 可配置范围 | 尽量全部进 `config/sunlitmarket-common.toml`，改数值不重编译 |
| 5 | 脚本层 | **KubeJS 优先**，所有数值/商品/派系行为都能从脚本写；必须支持 `/reload` 热重载 |
| 6 | 柜台方块 | 确认要做 |
| 7 | 整合包联动 | 延后，先做核心 |

### 14.1 已落地的配置项（S1）

`config/sunlitmarket-common.toml`：

```toml
[alignment]
  enabled = true
  min = -1000
  max = 3000
  decayEnabled = true
  decayPerDay = 1
  wantedDecayPerDay = 2
  wantedMax = 100

[diplomacy]
  doubleDropEnabled = true
  doubleDropRatio = 0.5
  useMiddleEarthFactionTable = true

[price]
  tiers = ["-1000|1.50|0.70|死敌", "-500|1.35|0.85|敌对", ...]
```

阶梯格式 `阈值|买入倍率|卖出倍率|称号`，取「阈值 ≤ 当前结盟值」的最大一档。
买入倍率乘的是玩家**付出**的钱（1.35 = 加价 35%），卖出倍率乘的是玩家**收到**的钱（0.85 = 少收 15%）。

改完配置要 `/reload` 或重进世界才生效（Forge config 的限制），但**不需要重启游戏**。

---

## 15. 工业品与科技门槛（GT / 魔法系）

### 15.1 基本判断

整合包实际装了 435 个 mod，其中相关的是：

| 类别 | mod |
|---|---|
| 工业 | `gtceu 7.5.3`、`gregtechterminals`、`gregfluxology`、`applied_greg`、`gregmek` |
| 自动化 | `create 6.0.8` + 8 个子模组、`mekanism 10.4`、`ae2 15.4.10` |
| 魔法 | `thaumcraft 4.2.3.5 (20748)` + tinkerer/horizons/bases、`astralsorcery`、`botania` |
| 经济 | `createnumismatics 1.1.0`、`numismatics_utils` |
| 农业 | `farmersdelight`、`farmlife`、`stardew_fishing`、`farmingforblockheads` |

### 15.2 卡进度的原则

**只卡前期配方，不卡后期产量。**

GT 从 EV 开始单批产出就是几百上千，HV 之后按万算。任何"永久只能靠交易获得"的材料都会在中期直接锁死存档——因为需求量级和当铺能供给的量级差三个数量级。

所以门槛必须这样设计：

```
前期（LV/MV）：材料只能靠 ①当铺购买 ②寻宝
      ↓ 达成条件（结盟值 / 任务 / 研究）
后期（HV+）：配方解锁，自己量产
```

**解锁的对象是「配方」，不是「物品」。** 这是关键——玩家一旦解锁就能无限造，不会卡死。

### 15.3 具体机制（KubeJS 阶段实现）

1. **配方锁**：给目标 GT 配方挂一个 `unlock` 条件，用现有 `UnlockData` 的 id 体系
2. **当铺供给**：同一材料在当铺有 `Offer`，价格高、`stock` 少、`limit` 严格（比如每天 4 个）
3. **寻宝供给**：战利品表里加低概率掉落
4. **解锁条件**：可以是结盟值阈值（`me:gondor >= 500`）、任务阶段、或者"已经手搓过 X 个"

适合当门槛的材料（进入下一 tier 的瓶颈件，不是基础件）：

- 合金类：`hsla_steel`、`tungsten_carbide`、`ultimet`、`hssg`
- 化工类：`epoxy`、`polyethylene` 的中间体
- 电子类：`phenolic_board`、`plastic_circuit`、`advanced_circuit` 的基板

**不适合**卡的：铁、铜、锡、钢这类基础材料（卡了就是纯折磨，且没有替代路径）。

### 15.4 魔法系不卡

Thaumcraft / Astral / Botania 不涉及工业化量产，玩家买几十个不会破坏平衡，所以：

- **不设配方锁**
- 用 `Offer.limit` 限量（每天 2–4 个）
- 价格定高（3–5 倍于自产成本）

适合当铺卖的"神奇道具"（可交易 + 可合成，两条路并存）：

| 来源 | 例子 | 定位 |
|---|---|---|
| Thaumcraft | 法杖核心、研究笔记、要素罐 | 前期省研究时间 |
| AstralSorcery | 星辉水晶、共振宝石 | 跳过采集 |
| Botania | 神秘花、魔力钢 | 跳过刷花 |
| GT | 电路基板、合金锭、真空管 | 卡进度 |
| Create | 精密构件、机械臂 | 跳过产线 |
| ME | 中洲武器/护甲 | 战斗装备 |

### 15.5 货币

两种货币都走现有 `Currency`（支持多面额 + 找零）：

- **中洲币**：`me:copper_coin`(1) / `me:silver_coin`(8) / `me:gold_coin`(64) —— 已配置
- **星露谷币**：`numismatics:spur`(1) / `crown` / `sunken` —— 主客户端有，-DEV 没有

当铺可以同时接受两种，汇率在 `SunlitMarket.coinValue()` 里配。

---

## 16. S1 实施状态

已完成并通过编译（2026-11-02）：

| 文件 | 状态 |
|---|---|
| `config/SunlitMarketConfig.java` | 新增，ForgeConfigSpec，全部数值可配 |
| `alignment/AlignmentData.java` | 新增，SavedData `sunlitmarket_alignment`，含迁移/衰减/通缉度 |
| `alignment/FactionRelations.java` | 新增，读 `data/me/me/faction/*.json`，建敌对图 + 最长前缀匹配 |
| `market/MarketService.java` | 价格改阶梯、门槛改结盟值、成交走 `AlignmentData` |
| `market/MarketView.java` | 加 `title` 字段并序列化 |
| `client/MarketScreen.java` | 标题行显示称号 |
| `SunlitMarketMod.java` | 注册配置、每日衰减 tick、`/sunlitmarket align|rep|wanted|factions` |
| `kjs/SunlitMarketBinding.java` | 新增 `alignment/addAlignment/setAlignment/title/wanted/addWanted/factionOf/factions`，旧接口保留为别名 |

**S1 未做**（属于 S2/S3）：队伍维度（`ownerOf` 目前固定返回玩家 UUID，已留好接缝）、CIVILIAN 资格判定、收购请求。
