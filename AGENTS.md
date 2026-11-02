# AGENTS.md — Sunlit Market（给接手的 AI）

> 这是**项目级入口**。先读这份，再看 `TESTING.md` 和整合包里的 `docs/SUNLIT_MARKET.md`。
> 工程根：`H:\MinecraftMods\SunlitMarket`，Forge 1.20.1。

## 1. 这是什么

Sunlit Market 是给 Sunlit Valley 整合包写的 NPC 交易/声望附属 mod：

- 右键带标签的 NPC（含 Connector 上的 Fabric Middle-earth `me:npc`）打开服务端交易 GUI
- 独立声望（按玩家 UUID + faction），不修改 ME 的静态外交表
- 货币优先 Create: Numismatics 银行，缺失时退回硬币物品
- JSON + KubeJS 双层配置
- 当前状态：**MVP + P1 完成（限购/声望价/解锁门槛/季节曲线）；dev 自检 24/24 通过；
  已部署到主客户端 / -DEV / mirror；未上传服务器 mod；未完成 ME NPC 与 Numismatics 实机经济测试**
- 当铺核心层（S2–S6：FTB 可选队伍、柜台顾客、赃物/通缉、执法、脚本事件）已在 `core-implementation` 分支实现，
  设计/行为见 `docs/CORE.md`。**22 个单测 + 22 项 -DEV 集成断言通过**（`TESTING.md` 第 8 节）；
  FTB 真实双人队伍、Numismatics 银行、柜台/商人 GUI 视觉仍未验证（GUI 由用户亲自打开，勿用 Rosetta 操作界面）。
- **Sunlit Valley 深度联动**：价格锚、Sunlit 职业顾客、ME 派系顾客、阶段桥和 ME 商人收购见
  `docs/SUNLIT_VALLEY_INTEGRATION.md`。仓库 `examples/sunlit-valley/` 里的顾客/商人映射已按 ME 1.4.2 出货数据修正，
  **但尚未同步到主客户端 / -DEV / mirror**，也未完成 ME NPC 实机验收。
- **S0 经济观察层**：只记录本模已接入的资金步骤，不是全服 M0；说明和限制见 `docs/S0_OBSERVATIONS.md`。
  隔离专服真实 Currency 自检 14/14 通过；七天连续观测和 Numismatics 实机仍未完成。

## 2. 当前构建 / 部署

```powershell
H:\MinecraftMods\gradle-8.14.5\bin\gradle.bat --no-daemon build
# 产物 H:\MinecraftMods\SunlitMarket\build\libs\sunlitmarket-1.20.1-0.1.0.jar
```

已同步位置：

- `G:\MinecraftGames\Sunlit Valley(BaopuEdition)\.minecraft\versions\Society Sunlit Valley\mods`
- `...-DEV\.minecraft\versions\Society Sunlit Valley\mods`
- `...\mirror\versions\Society Sunlit Valley\mods`
- `overrides`、`pack/dev`、`pack/release` 的 mods

> 改完代码要重新构建，并把 jar 同步到以上位置；当前客户端若已启动，必须重启才能加载新 jar。
> 三处 jar 的 sha256 必须一致（`sha256sum` 前 16 位对一下即可）。

生产 DEV 客户端（60 mod，含 SereneSeasons / Astral / TC4R / Rosetta）用：

```powershell
Start-Process -FilePath 'G:\MinecraftGames\Sunlit Valley(BaopuEdition)-DEV\launch-sunlitmarket-dev.bat' -WindowStyle Hidden
```

- 参数文件 `_java_args_dev.txt` = 主客户端 `_java_args_sv.txt` 做路径替换 + `--quickPlaySingleplayer asverify`
- 日志 `...-DEV\devclient-sunlitmarket.log`（不是 latest.log）
- 探针脚本 `kubejs/server_scripts/sunlitmarket/_devprobe.js`，仅 DEV 实例使用

## 3. 目录

```
src/main/java/com/society/sunlitmarket/
  SunlitMarketMod.java          # @Mod：频道、事件、命令、NPC 交互
  market/
    Merchant.java               # id/name/faction/npcType/tag/offers
    Offer.java                  # item/count/buy/sell/reputation/stock
    MarketRegistry.java         # JSON + KubeJS 商人注册、NPC 解析
    MarketService.java          # 视图、买卖校验、库存、价格倍率
    MarketView.java             # 服务端→客户端 GUI 数据
    StockData.java              # 每日库存 SavedData
    PriceModifiers.java         # KubeJS 运行期价格倍率
  reputation/ReputationData.java# 玩家×派系独立声望 SavedData
  currency/Currency.java        # Numismatics 反射/硬币回退、找零
  net/                          # OpenMarket / Update / Action 包
  client/                       # MarketClient + MarketScreen
  kjs/                          # KubeJS 插件 + SunlitMarket 绑定
```

测试/整合：

- `run-client/kubejs/server_scripts/sunlitmarket_test.js` — 最小 GUI 测试
- `run-server/kubejs/server_scripts/sunlitmarket_test.js` — 服务端注册测试
- `TESTING.md` — 非阻塞测试流程
- 整合包 `docs/SUNLIT_MARKET.md` — 设计、兼容、玩法
- 整合包 `kubejs/server_scripts/sunlitmarket/meMerchants.js` — 中州示例商人
- 整合包 `kubejs/server_scripts/sunlitmarket/themedMerchants.js` — 匠魂/神秘/星辉示例商人
- 整合包 `kubejs/server_scripts/sunlitmarket/meSeasonPrices.js` — 季节价格示例
- 整合包 `kubejs/startup_scripts/me/meTrades.js` — 中州作物进入 Shipping Bin 价格表

## 4. 架构与数据流

```
右键 NPC
  → PlayerInteractEvent.EntityInteract（服务端）
  → MarketRegistry.resolve(entity)
      1. entity Tags 含 merchant.tag
      2. entity.serializeNBT().NpcData.Type == merchant.npcType
      3. ForgeData "sunlitmarket.merchant"
  → MarketService.open()
      → OpenMarketMessage(JSON) → 客户端 MarketScreen
  → 客户端按钮 → MarketActionMessage
      → MarketService.handleAction()
          - 声望门槛
          - 库存检查（StockData）
          - 价格（基础价 × PriceModifiers）
          - Currency.pay / give / removeItems / giveItem
      → MarketUpdateMessage → 刷新 GUI
```

- 交易全部服务端校验，客户端只发 offer 索引/数量。
- 库存按 `merchantId#offerIndex` 存 `StockData`，每个游戏日清空并按 `stock` 初始化。
- 声望存 `ReputationData`，faction 是任意字符串（`me:gondor`、`test:village` 都行）。
- 商人带 `sunlitmarket.` 标签时，`LivingChangeTargetEvent` 会阻止其主动索敌/被选为目标。

## 5. KubeJS API

脚本位置：整合包 `kubejs/server_scripts/**`，全局名 `SunlitMarket`。

```js
SunlitMarket.clear()                 // 清空脚本商人（/reload 脚本开头调用）
SunlitMarket.merchant({ ... })
SunlitMarket.offer("merchant_id", { ... })
SunlitMarket.open(player, "merchant_id")
SunlitMarket.buy(player, "merchant_id", 0, 1)
SunlitMarket.sell(player, "merchant_id", 0, 1)
SunlitMarket.setReputation(player, "me:gondor", 10)
SunlitMarket.addReputation(player, "me:gondor", 1)
SunlitMarket.getReputation(player, "me:gondor")
SunlitMarket.coinValue("me:copper_coin", 1)
SunlitMarket.priceMultiplier("merchant_id", "item:id", 1.25)
SunlitMarket.clearPriceMultipliers()
SunlitMarket.reload()               // 重新读取 config/sunlitmarket/merchants.json
SunlitMarket.merchants()            // 调试 JSON
SunlitMarket.view(player, "merchant_id")   // 服务端当前视图 JSON（余额/库存/限购/解锁），测试与对话入口用
SunlitMarket.countItems(player, "minecraft:wheat") // 背包内某物品数量
SunlitMarket.unlock(player, "theme.tinkers.adept") // 授予玩家解锁 id（任务阶段）
SunlitMarket.revoke(player, "theme.tinkers.adept")
SunlitMarket.isUnlocked(player, "theme.tinkers.adept")
```

商人 schema：

```js
{
  id: "me_shire_farmer",
  name: "夏尔农户",
  faction: "me:shire",
  tag: "sunlitmarket.me.shire_farmer", // 与 npcType 至少写一个
  npcType: "",                         // 可选：me:shire.peasant
  offers: [
    { item: "me:tomato", count: 1, buy: 16, sell: 10, reputation: 0, stock: 16, limit: 8,
      unlock: "", repGain: 1 }
  ]
}
```

> **脚本硬规则**：server 脚本里**任何地方**都不要写 `const`——KubeJS 的 Rhino 把它当作用域级声明，
> 循环体第二次执行、tick 回调第二次执行、脚本第二次求值都会 `redeclaration of var X`，一律用 `let`；
> `Java.loadClass` 的返回值别用类名同名变量，同一个类只在一个脚本里 load。详见 `TESTING.md` 的 Rhino 坑一节。

- `buy`：玩家支付；`sell`：玩家获得；0 = 不提供该方向
- `stock`：每日库存；不填/-1 = 无限
- `limit`：每玩家每日购买上限；不填/-1 = 无限
- `unlock`：需要玩家持有的解锁 id（任务阶段门槛）；不填 = 无门槛，未解锁时 GUI 隐藏买入按钮并显示「未解锁」
- `repGain`：每成交一次给的派系声望；不填/0 = 不给（声望门槛靠这个或任务涨）
- `priceMultiplier`：运行期倍率，不写盘；季节/声望脚本需自己重设
- JSON 配置：`config/sunlitmarket/merchants.json`（数组或 `{merchants:[...]}`）

## 6. 涉及 mod / 环境

| 用途 | 依赖 |
|---|---|
| 加载/构建 | Forge 47.4.23、Java 17、KubeJS 2001.6.5-build.16、Rhino 2001.2.2、Architectury 9.2.14 |
| 生产经济 | Create: Numismatics、numismatics_utils、society_trading、ShippingBin、Cozy Cafe、Bountiful |
| 中州 | Middle-earth 1.4.2-port.0（**Fabric via Sinytra Connector**）+ Fabric API |
| 对话/任务（未接） | SVDialog、FTB Quests |
| 价格/品质参考 | GTMFO、SereneSeasons、Quality Food |

开发运行时只有 KubeJS + 本 mod；测试货币用 emerald。生产端才验证 Numismatics 银行与 ME NPC。

## 7. 测试系统

### 最小 dev 客户端

```powershell
$p = Start-Process -FilePath 'H:\MinecraftMods\gradle-8.14.5\bin\gradle.bat' `
  -ArgumentList '--no-daemon','runClient','-PquickPlay=smtest' `
  -WorkingDirectory 'H:\MinecraftMods\SunlitMarket' -PassThru
```

**不要**在启动命令里长时间 Sleep；另开命令轮询：

```powershell
Select-String -Path 'H:\MinecraftMods\SunlitMarket\run-client\logs\latest.log' `
  -Pattern 'SunlitMarket|opened test market|buy=|ERROR|Exception' | Select-Object -Last 20
```

- `run-client/saves/smtest` 测试世界
- `sunlitmarket_test.js` 是**自检脚本**：进世界 60 tick 后跑完整场景，把 `[SM-TEST] PASS/FAIL` 打到
  `latest.log`，然后打开 GUI 供人工看同一组数字
- 覆盖：余额/库存初始值、价格倍率、限购（超量/填满/封顶）、解锁门槛、声望门槛与折扣、买卖与找零、
  纯库存商人售罄、跨日重置
- 手工命令：`/sunlitmarket open <id>`、`/sunlitmarket rep <faction> <v>`、`/sunlitmarket balance`、
  `/sunlitmarket unlock <id> [off]`、`/sunlitmarket locks`、`/sunlitmarket debug <id>`
  （`debug` 把服务端视图 JSON 打到聊天和 latest.log，是最省事的验收手段）

### 当铺核心自检

```mcfunction
execute as @p run sunlitmarket selftest
```

仅在隔离开发世界运行；会临时占用背包和头顶两格空气位置，结束后恢复。覆盖交易方向/负结盟/满背包、
库存限购迁移、柜台请求/还价/过期、赃物/没收/罚金。不要在生产世界执行。

### 生产/ME 测试

```mcfunction
/summon me:npc ~ ~ ~ {Tags:["sunlitmarket.me.shire_farmer"],InitializationData:{Type:"me:shire.peasant"}}
```

- 已验证：KubeJS 插件、商人注册、GUI 打开
- 未验证：ME `NpcData.Type` 解析、ME NPC 免战、Numismatics 银行反射、服务器 mod 加载
- 若 NPC 因派系攻击，先用同阵营城镇测试；不要长期用 `NoAI`

### Rosetta

```
H:\MinecraftMods\RainJava-work\RosettaRemoteDebugBridge\legacy\tools\rosetta_remote.py
--host <REMOTE_HOST> --port <REMOTE_PORT> --token <TOKEN_FROM_LOCAL_SECRET_STORE>
```

- 只能上传 kubejs/ftbquests；**mod jar 必须人工放到服务器 mods 并重启**
- `ping` / `ls` / `upload` / `console` / `tail`；`console` 可能超时但命令已下发
- 测试命令：`/sunlitmarket reload`、`/sunlitmarket open <id>`、`/sunlitmarket rep <faction> <value>`

## 8. Connector / Fabric ME 兼容（必须遵守）

- **禁止** `import net.jukoz.me.*`，禁止 Mixin 进 ME
- 只通过实体 Tags、`serializeNBT().NpcData.Type`、ForgeData 识别
- 不依赖 ME 的 SynchedEntityData 扩展；自己的同步走本 mod 的 `SimpleChannel`
- ME 是 Fabric/Connector，类名/成员经过 remap；不要对 `mods/Middle-earth-*.jar` 编译
- 已知风险：`serializeNBT` 字段结构可能随 ME 版本变；Tags 是兜底
- ME 的“派系”是静态外交表，不是本 mod 的声望；两者不要混为一谈

## 9. 已知限制 / 下一步（按优先级）

1. **季节/派系价格扩展（已完成，待生产端换季观察）**
   - `meSeasonPrices.js` 用 `SeasonHelper.getSeasonState` 读真实季节，按物品 4 季因子 + smoothstep
     跨季插值（不是硬跳），每 200 tick 刷新一次；`/reload` 后立即重设
   - 验收：换季价格变化、重进世界后脚本重设、不同玩家声望折扣正确
2. **每玩家限购（已实现 + dev 自检通过）**
   - `Offer.limit` + `LimitData`；购买前检查，GUI 显示 `used/limit`
   - dev 自检：`limit.overAsk` / `limit.fill` / `limit.blocked` / `day.limitReset` 全绿
3. **匠魂/神秘/星辉商人（已有解锁门槛）**
   - `themedMerchants.js` 按标签 `sunlitmarket.theme.*` 生成商人；高阶货品带 `unlock`
     （`theme.tinkers.adept` / `theme.thaumcraft.adept` / `theme.astral.attuned`）
   - 解锁来源：任务奖励命令 `/sunlitmarket unlock <id>` 或 KubeJS `SunlitMarket.unlock`
   - 待补：更多可验证材料、价格曲线；不要按工具 NBT/研究数据定价
   - 验收：JEI 能找到、价格不破坏 GT 主线、阶段限制有效
4. **发布 / 服务器**
   - GitHub 更新器已发布 `20261101-1442`；后续改代码/脚本后重新 `publish-all`
   - 服务器人工放 jar + 同步 kubejs；客户端重启
   - 验收：manifest sha256 与本地一致、服务器 `/sunlitmarket` 命令可用
5. **完整实测**
   - ME 商人（Tags + NpcData.Type 两种）
   - Numismatics 银行/硬币回退、找零、金币兑换
   - 重启后库存/声望持久化，跨日重置

## 10. 验证记录

- **dev 自检（`run-client`）24/24 通过**：库存/限购/解锁/声望门槛与折扣/买卖与找零/售罄/跨日重置
- **持久化**：`run-client/saves/smtest/data/` 下 4 个 `sunlitmarket_*.dat`（limits/reputation/stock/unlocks）；
  重启后 `restored view` 能读回上次的 used/stock/解锁
- **修掉的真 bug**：`Currency.giveCoins` 先扣面额再判断物品是否存在，dev 端没有 `numismatics:spur`
  时找零被吞（卖东西给 0 钱）；现在先解析物品、无面额时告警
- **-DEV 实机验证**：`6/6 KubeJS 脚本 0 错误`；`seasonal multipliers (load): spring@1% leek=0.9`（SereneSeasons 真季节 +
  曲线）；`theme_astral_merchant` 的 `starmetal_ingot` 未解锁 `locked:true`、解锁后 `false`（UnlockData 跨重启持久化）
- **Middle-earth 实机验证（2026-11-01，-DEV 装了 Connector beta.49 + ME 1.4.2-port + fabric-api）**：
  - `me:npc` 实际类是 `net.jukoz.me.entity.npcs.NpcEntity`（Connector 加载，本 mod 无编译依赖）
  - **Tags 路径**：`tags=[sunlitmarket.me.shire_farmer]` → `resolve=me_shire_farmer` → `opening me_shire_farmer`
  - **NpcData.Type 路径**：无标签 NPC `npcType=me:shire.peasant` → `resolve=dev_npc_type_test` → `opening dev_npc_type_test`
  - **右键链路**：`player.interactOn(npc, MAIN_HAND)`（等价真实右键）→ `PlayerInteractEvent.EntityInteract` 正常触发
  - **免战双向**：`npc.setTarget(player)` → `target=null`；`zombie.setTarget(npc)` → `target=null`
  - 复现脚本：`...-DEV/kubejs/server_scripts/sunlitmarket/_meprobe.js`
- **未验证**：Numismatics 银行/找零/中州币兑换（DEV 没有 Numismatics）、服务器端 jar

## 11. AI 操作规则

- 先 `git status`/读代码，再改；不要直接覆盖已部署 jar 之外的配置
- 改代码后 `gradle build`；客户端重启才算验证
- 不要自动上传 mod 到服务器、不要发布更新器，除非用户明确要求
- 启动客户端用非阻塞 `Start-Process`，只轮询日志
- 新增/修改商人优先写 KubeJS/JSON，不要把经济数值硬编码进 Java
- 任何 ME 相关结论必须标注来源（本机 jar / mapped jar / 源码仓）；源码仓版本可能与出货 jar 不同
