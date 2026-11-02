# SunlitMarket 测试手册

## 0. 三种测试环境

| 环境 | 位置 | 能测什么 |
|---|---|---|
| 最小 dev 客户端 | `gradle runClient`（本仓库 `run-client`） | 交易/库存/限购/解锁/声望/找零，**自检脚本自动出 PASS/FAIL** |
| 生产 DEV 客户端 | `G:\MinecraftGames\Sunlit Valley(BaopuEdition)-DEV` | 真实 60 mod 环境：SereneSeasons 季节曲线、Astral/TC4R 主题商人、Rosetta 桥 |
| 主客户端 / 服务器 | `...\Sunlit Valley(BaopuEdition)` | Middle-earth NPC（Tags + NpcData.Type）、Numismatics 银行、中州币兑换 |

## 1. 非阻塞启动（推荐）

```powershell
$p = Start-Process -FilePath 'H:\MinecraftMods\gradle-8.14.5\bin\gradle.bat' `
  -ArgumentList '--no-daemon','runClient','-PquickPlay=smtest' `
  -WorkingDirectory 'H:\MinecraftMods\SunlitMarket' -PassThru
```

启动后**不要**在同一个命令里长时间 Sleep。另开命令轮询：

```powershell
Select-String -Path 'H:\MinecraftMods\SunlitMarket\run-client\logs\latest.log' -Pattern 'SM-TEST|rejected|ERROR' | Select-Object -Last 30
```

关闭：`taskkill /PID <java 进程号>`（不带 `/F`，走正常退出会存档）。

## 2. 自检脚本（`run-client/kubejs/server_scripts/sunlitmarket_test.js`）

进世界 60 tick 后跑完整场景，输出 `[SM-TEST] PASS/FAIL`，最后打开 GUI 供人工看同一组数字。

覆盖项：

| 检查 | 含义 |
|---|---|
| `balance.read` / `day.resetAtStart` | 余额读取、跨日重置 |
| `limit.init/overAsk/fill/blocked` | 每玩家每日限购：超量拒绝、填满、封顶 |
| `stock.init/decrement/blocked` | 每日库存递减、售罄拒绝 |
| `price.multiplier` / `price.discount` | KubeJS 倍率、声望折扣（10% 上限） |
| `unlock.init/blocked/granted` | 任务阶段门槛（`Offer.unlock`） |
| `rep.gate` / `rep.gain` | 声望门槛、成交涨声望（`Offer.repGain`） |
| `sell.hasWheat/ok/change` | 卖出、找零（`Currency.giveCoins` 面额缺失 bug 的回归测试） |
| `day.stockReset/limitReset` | `time add 24000` 后库存/限购复位 |

最近一次结果：**24 passed, 0 failed**。

重启客户端后再跑，`restored view` 行会打印上次存档的 used/stock/解锁状态，用来确认 SavedData 持久化。

## 3. 生产 DEV 客户端（季节 / 主题商人）

```powershell
Start-Process -FilePath 'G:\MinecraftGames\Sunlit Valley(BaopuEdition)-DEV\launch-sunlitmarket-dev.bat' -WindowStyle Hidden
```

- 参数文件：`...-DEV\...\Society Sunlit Valley\_java_args_dev.txt`（离线账号 + `--quickPlaySingleplayer asverify`）
- 探针脚本：`kubejs/server_scripts/sunlitmarket/_devprobe.js`（仅 DEV 用，进世界 100 tick 后执行）
- 实测通过（2026-11-01）：`Loaded 5/5 KubeJS server scripts ... 0 errors`、
  `[SunlitMarket] seasonal multipliers (load): spring@1% leek=0.9 white_mushroom=1`、
  `theme_astral_merchant` 的 `starmetal_ingot` 在未解锁时 `"locked":true`、解锁后 `false`；
  `theme_tinkers_smith` 返回 `{}` 属正常（DEV 实例没装匠魂）
- Rosetta 桥：本机 DEV 客户端在 48790（`netstat -ano | grep 4879` 确认），关闭用桥的 `mc.stop()`
- 桥工具（本仓库 `tools/`）：
  - `python tools/bridge_exec.py <port> "<game dir>" <snippet-file>` 在游戏里跑一段 Java（需要
    `RosettaRemoteDebugBridge/remote-token.txt`）
  - 现成片段：`tools/reload_scripts.snippet`（热重载 server 脚本）、`tools/stop_client.snippet`（干净关客户端）
  - 热重载：`ServerScriptManager.instance.reload(mc.getResourceManager())` 放进 `server.execute(...)`。
    **注意**这条路会把脚本按「资源包 + 目录」各加载一次，含 `const` 的脚本必报 `redeclaration`；
    只用于验证改动，正式验证仍以重启为准
  - 桥会把 MCP 名改写成 SRG，`MinecraftServer#createCommandSourceStack` / `getCommands` /
    `getResourceManager` 会编译失败（`m_129892_() is undefined`）；用 `Minecraft#getInstance` /
    `getSingleplayerServer` / `getResourceManager`、`ServerScriptManager.instance` 这些能映射的名字
  - KubeJS 的 class filter 不允许 `java.nio.file.*`，别在脚本里读写文件

## 3b. Middle-earth 在 -DEV 里的实测（2026-11-01 通过）

-DEV 实例已装入 `Connector-1.0.0-beta.49+1.20.1.jar`、`Middle-earth-1.4.2-1.20.1-port.0.jar`、
`fabric-api-0.92.6+1.11.15+1.20.1.jar`（与主客户端同一批 jar），所以中州实体可以直接在这里测，不必开 435 mod 的主客户端。

探针 `kubejs/server_scripts/sunlitmarket/_meprobe.js` 进世界后自动：

1. 召唤两个 `me:npc`（一个带 `sunlitmarket.me.shire_farmer` 标签，一个只有 `InitializationData.Type`）
2. 打印类名 / tags / `NpcData.Type` / 解析出的商人 id
3. 用 `player.interactOn(npc, MAIN_HAND)` 走真实右键链路
4. 双向免战：`npc.setTarget(player)`、`zombie.setTarget(npc)`

期望日志：

```
[SM-ME] npc#1 class=net.jukoz.me.entity.npcs.NpcEntity tags=[sunlitmarket.me.shire_farmer] npcType=me:shire.peasant resolve=me_shire_farmer
[SM-ME] npc#2 class=net.jukoz.me.entity.npcs.NpcEntity tags=[] npcType=me:shire.peasant resolve=dev_npc_type_test
[SunlitMarket] opening me_shire_farmer for <player>
[SunlitMarket] opening dev_npc_type_test for <player>
[SM-ME] npc.setTarget(player) -> npc.target=null
[SM-ME] zombie.setTarget(npc) -> zombie.target=null
```

注意：DEV 没有整合包的 `globalRegistry.js`，所以 `startup_scripts/me/meTrades.js` 会打印
`skipped: global.trades missing`（已加守卫，不再报错）。

## 4. 生产整合包测试（Middle-earth / Numismatics）

已部署到主客户端 / `-DEV` / mirror 的 `mods` 与 `kubejs/server_scripts/sunlitmarket`。

生成一个带标签的中州商人（需要 op）：

```mcfunction
/summon me:npc ~ ~ ~ {Tags:["sunlitmarket.me.shire_farmer"],InitializationData:{Type:"me:shire.peasant"}}
```

右键打开交易；如果 NPC 因派系敌对而攻击你，先在同一城镇/阵营测试，或暂时用
`/data merge entity @e[type=me:npc,limit=1,sort=nearest] {NoAI:1b}` 仅做 GUI 验收。
**不要**把 `NoAI` 当作长期商人方案。

主题商人（匠魂/神秘/星辉）用同样方式加标签：

```mcfunction
/summon me:npc ~ ~ ~ {Tags:["sunlitmarket.theme.tinkers"],InitializationData:{Type:"me:dale.peasant"}}
/summon me:npc ~ ~ ~ {Tags:["sunlitmarket.theme.thaumcraft"],InitializationData:{Type:"me:dale.peasant"}}
/summon me:npc ~ ~ ~ {Tags:["sunlitmarket.theme.astral"],InitializationData:{Type:"me:dale.peasant"}}
```

高阶货品需要解锁：

```mcfunction
/sunlitmarket unlock theme.tinkers.adept
/sunlitmarket unlock theme.thaumcraft.adept
/sunlitmarket unlock theme.astral.attuned
/sunlitmarket locks          # 查看已解锁
/sunlitmarket debug theme_thaumcraft_curio   # 把服务端视图 JSON 打到聊天 + latest.log
```

FTBQ 奖励里直接用 `/sunlitmarket unlock <id>`（奖励命令以控制台身份执行，有权限）。

季节价格脚本只要求 `sereneseasons`（不依赖 ME，也不依赖 `global.getSeasonFromLevel`）；
换季或 `/reload` 后观察 GUI 价格和 `latest.log` 的 `seasonal multipliers applied`。

离线校验季节曲线（不用开游戏）：

```bash
node tools/season_check.js "<...>/kubejs/server_scripts/sunlitmarket/meSeasonPrices.js"
```

用桩替换 `Java`/`SunlitMarket`/`ServerEvents`，断言四季边界与 smoothstep 中间值。

> 三个 Rhino 坑（都会让**单个脚本**静默失效，日志里只有一行 ERROR，且
> `Loaded 4/5 ... 1 errors`）：
> 1. **不支持箭头函数参数里的数组解构**（`([a, b]) => ...`）→ `syntax error`。
> 2. **`const` 被当作作用域级声明**：循环体第二次执行、`PlayerEvents.tick` 回调第二次执行、
>    或脚本第二次求值（整合包会把脚本求值不止一次）都会 `TypeError: redeclaration of var X`。
>    **server 脚本里任何位置都别写 `const`，一律 `let`**（`let` 实测安全）。
> 3. **`Java.loadClass('a.b.C')` 也会在作用域里声明一个叫 `C` 的 var**，所以
>    `let C = Java.loadClass('a.b.C')` 会和它撞名，两个脚本各 load 同一个类也会撞。
>    换个变量名（`SM_SEASON_API`），并且只在一个脚本里 load（其它脚本读 `global.*`）。
>
> 写脚本后先 `node --check <file>`；`run-client/kubejs/server_scripts/sunlitmarket/` 里放了
> 这三个整合包脚本的副本，启动一次 dev 客户端就能看到
> `Loaded x/y KubeJS server scripts ... with N errors`（启动只要 2 分钟，比 -DEV 快）。

## 5. `/reload` 行为

- KubeJS `onServerReload` 会重新读取 `config/sunlitmarket/merchants.json`
- 脚本商人需在脚本开头调用 `SunlitMarket.clear()`；`clear()` 已放在 `meMerchants.js` 的 ME 判断之外，
  保证 ME 缺失时 `/reload` 不会复制主题商人
- 交易 UI 已打开时，`/reload` 不会自动重开；重新右键即可

## 6. 服务器

- mod jar **不能**通过 Rosetta 自动上传；由人工放入服务端 `mods` 后重启
- 只允许同步 kubejs/FTBQ 脚本
- 测试命令：`/sunlitmarket reload`、`/sunlitmarket open <id>`、`/sunlitmarket rep <faction> <value>`、
  `/sunlitmarket debug <id>`


---

## 7. S1 结盟值层验证记录（2026-11-02，-DEV 实例 63 mods）

构建 `sunlitmarket-1.20.1-0.1.0.jar`（74,924 B），三个实例 jar 哈希一致。

| 检查项 | 命令 / 探针 | 结果 |
|---|---|---|
| 配置文件生成 | 首次启动 | `config/sunlitmarket-common.toml`（1569 B），含 alignment / diplomacy / price 三段 |
| 派系表读取 | 启动日志 | `faction table: 16 factions, 16 with hostile entries` |
| 派系列表 | `/sunlitmarket factions` | 16 个，含子派系 `me:longbeards.erebor`、`me:hobgoblin_tribes.gundabad` |
| 无引号设置 | `/sunlitmarket align me:shire 100` | `me:shire = 100`（改用 `ResourceLocationArgument` 后不再需要引号） |
| 增量 + 双降 | `/sunlitmarket addalign me:shire 100` | shire 100→200，同时 brigand/goblin_town/hobgoblin_tribes/gundabad/isengard/mordor/moria/wild_goblins 各 −50 |
| 每日衰减 | `/time add 24000` × N | 每天 1 点向 0 靠拢：197→196，−50→−49 |
| 价格阶梯 | `/sunlitmarket debug me_shire_farmer` | 结盟值 0 时 leek 买 13；+120（常客档）时买 12、卖 7→7，cucumber 卖 10→11 |
| 称号透传 | 同上 | `"reputation":120,"title":"常客"`，界面标题行显示 `me:shire 120 (常客)` |
| 界面渲染 | `/sunlitmarket open me_shire_farmer` | `MarketScreen` 正常打开，无崩溃 |

### 已知问题 / 注意

- **控制台回读中文会丢字**：桥接 `console` 的返回值经 GB18030 → latin-1 解码，CJK 会被 `errors="ignore"` 丢掉。
  看中文（商人名、称号）要读 `latest.log` 再 `iconv -f GB18030`，不要信 console 回显。
- 命令参数 `faction` 必须用 `ResourceLocationArgument`；`StringArgumentType.string()` 不接受 `:`，
  会报「参数后应有空格分隔」。
- 强杀客户端会丢失未保存的 `AlignmentData`。测完先 `save-all` 再关。

---

## 8. Core 收尾验证（2026-11-02，-DEV 实例）

构建 `sunlitmarket-1.20.1-0.1.0.jar`（185,512 B，sha256 前缀 `1f36cd80`），仅部署到 -DEV；主实例 / mirror 未覆盖。

### 8.1 纯单元测试

`gradle --no-daemon test build`：**22 个测试通过**（Offer 5、OwnerKey 4、StockData 2、TradeMath 9、ReloadStaging 2）。
新增覆盖：显式 `legacyIndex` 迁移不会补满库存、已有 ID 值不被旧索引覆盖、暂存价格/币值在提交前不可见且失败时保留旧快照。

### 8.2 实机集成自检

`execute as @p run sunlitmarket selftest` → **PASS 22 integration assertions**：

- 交易：负结盟值交易、禁用方向拒绝、无效数量不改余额、满背包失败后余额不变、同报价不倒挂、结盟值序列化。
- 迁移：`legacyIndex` 把 `stock=6, used=2` 搬到新 ID 键；成交后 `stock=5, used=3`，未回满。
- 柜台：CIVILIAN 身份、请求生成、收购结算、重复请求拒绝、成交结盟、还价失败不变量、过期请求拒绝。
- 执法：漏斗面禁用、热度衰减/清除、按来源没收、柜台内赃物没收并扣罚金 2。

### 8.3 KubeJS 热重载边界（真实 `/reload`）

临时脚本同时设置 `priceMultiplier(me_shire_farmer, me:leek, 2.0)` 与 `coinValue(reloadtest:coin, 123)`；每次执行完整 `reload` 命令。

| 场景 | 日志 | 观察值 |
|---|---|---|
| 有效脚本 | `Loaded 6/6 ... 0 errors` | leek 买价 13→27，币值 123 |
| 脚本语法错误 | `Loaded 5/6 ... 1 errors` + `EvaluatorException` | **保持 27 / 123**（旧快照不被清空） |
| 删除脚本后再 reload | `Loaded 5/5 ... 0 errors` | 回到 13 / 0（旧脚本效果被移除） |

过程中修掉两个实际缺陷：

1. KubeJS 语法错误**不一定**进入 `console.errors`；现改用 `ScriptFileInfo.lines` 是否残留判断整批脚本成功，失败时整批保留旧快照。
2. `PriceModifiers` / `Currency` 原先读取也会命中暂存表；现改为「写暂存、读已提交」，提交前运行时不发生变化。

### 8.4 未验证 / 人工验收

- 柜台 GUI、商人 GUI 的视觉与点击流程：**由用户亲自打开**，本会话不再通过 Rosetta 操作 UI。
- FTB Teams 真实双人共享/退队/解散；当前无 FTB 运行环境。
- Numismatics 银行/找零：-DEV 未安装该 mod。
- 保存退出重进后的完整 GUI 行为；SavedData 的序列化已由单测与自检覆盖。

---

## 9. Sunlit Valley 深度联动（2026-11-02，仅静态）

按用户要求**没有启动游戏/服务器测试**（本地 400+ mod，单人世界不可进入）。已完成：

- `SUNLIT_VALLEY_INTEGRATION.md`：价格锚、三类价格面、职业/派系货池、AS/TC/BOT 材料、阶段桥和不受影响的 GT 配方边界。
- `svCustomers.js` / `svMeCustomers.js` / `svMastery.js` / `meMerchants.js`：`node --check` 全通过。
- Node 静态执行（完整价格数组）：646 条规则 / 631 个货池 / 15 条 NPC 规则；0 重复、0 个 `askPrice >= marketValue`；bread 22/14。
- FTBQ 重点商品覆盖：crops 89/89、fishing 75/75、pantry 12/12、minerals 44/44；AS/TC/BOT 材料 19 条；GT/机器/收藏品未作为顾客商品。

---

## 10. E1 动态货架定价（2026-11-02，单测级）

`trade.dynamicPricing`（默认开）：有库存的货架按稀缺度定价，`PriceEngine.scarcity = clamp((initial/max(stock,1))^e, min, max)` 同时作用于买/卖两个方向；玩家卖给商人会把货架补回（上限 `sellOverstockCapacity × initial`）， unlimited（stock:-1）与 fixed/coin 报价不变。日滚 StockData 全量重置即均值回归步。计划中的每笔冲击成本（k·sqrt）未实现——货架本身的库存变动已提供跨交易动态。

`gradle test`：31 个单测全过（新增 PriceEngineTest 8 + StockDataTest 1）。未做游戏内验证；上服后观察点：买空一个货架后 ask 上浮、当日倾销后 bid 下浮、次日复位。

---

## 11. E2 价格指数与蜡烛（2026-11-02，单测级）

- `IndexMath`：几何平均（1000 起步、单成员 ratio 钳制 0.05–20）+ 蜡烛 seed/update，7 个单测。
- `MarketData`（saved data `sunlitmarket_market`）：成员按 (merchantId, item) 绑定货架；1D/4H 两周期、各 240 根蜡烛环；每 100 tick 采样；停机跨过的 bucket 以最后收盘价平补；NBT 以扁平 int 数组持久化，6 个单测（含 save/load 往返）。
- KubeJS：`SunlitMarket.index(id, name, [[merchant, item], ...])`；整合包新增 `svIndices.js` 注册 sv.produce（7 成员）/ sv.metal（5）/ sv.delicacy（4），并给 meMerchants.js 的成员货架补了库存与孤山矿石在售条目（买价全部 ≥ Shipping 锚）。
- 命令 `/sunlitmarket index` 输出实时值；`gradle test` 共 **44 个单测全过**。
- 未做游戏内验证；服务器观察点：日志出现 `[SV-INTEGRATION] price indices registered`，交易后 `/sunlitmarket index` 数值偏离 1000，跨日出现新蜡烛。
- `gradle --no-daemon test build` 通过，生成 jar 并同步主客户端 / -DEV / mirror（sha256 前缀 `346db8b5`）。

待服务器验收：实际实体职业名、物品 ID、货池生成、柜台 UI、ME 商人收购条目、mastery 阶段同步。

启动时曾出现 `TypeError: redeclaration of const CROP`：`meTrades.js` 的 `const CROP` 与 `tfTrades.js` 同名，KubeJS startup 脚本共享作用域且会重复求值。现已改为唯一的 `let ME_CROP` / `ME_ARTISAN`，并去掉箭头参数数组解构；主客户端启动脚本恢复为 104/104、0 errors。

服务端 Rosetta 只读诊断：`me:shire.peasant` 共 33 个，`MarketRegistry.resolve` 全部返回 `me_shire_farmer`；说明 NPC 类型和商人注册正常。右键无反应的原因是 `EntityInteract` 可能先被其它模组取消，监听器现已改为 `EventPriority.LOWEST, receiveCancelled=true`。

专用服务端曾崩溃于 `NoClassDefFoundError: net/minecraft/client/gui/screens/Screen`：`AlignmentMessage` 在 common setup 类加载时直接引用客户端 `Minecraft`/`AlignmentScreen`。现已新增 `client/ClientPacketHandlers`，四个 S2C 包只引用该客户端处理器；扫描 jar 后确认 `net/minecraft/client` 只出现在 `client` 包。服务端 jar sha256 前缀 `346db8b5`，待服务器重启验收。

## 12. E3 行情面板 + 当铺 GUI（2026-11-02，静态 + 单测级）

- 服务端：`ChartView` / `ChartService`（`market` 包，不引用任何客户端类）把指数列表、当前值、`lastBucket` 和最多 120 根蜡烛序列化成 JSON；`ChartRequestMessage`(C2S, id 6) / `ChartDataMessage`(S2C, id 7) 注册在 common setup。请求是只读的，不需要交易会话。
- 客户端：`client/ChartScreen` 自绘蜡烛（`GuiGraphics.fill`，无第三方图表库），y 轴按可见最高/最低自动缩放并留 5% 边距，悬停十字线显示该 bucket 的开/高/低/收；指数页签和 1日/4时 页签都是重新请求而不是本地过滤，保证客户端不持有第二份定价逻辑。关闭面板回到商店而不是掉回世界。
- 入口：商店窗口的「行情」按钮（无需权限）；`/sunlitmarket chart [index] [period]` 供管理员从控制台打开。
- GUI：`MarketScreen` 改为固定尺寸窗框（全部由 `client/Ui` 用 `GuiGraphics.fill` 自绘，无贴图、无原版 Button），搜索框按物品 ID 和本地化名过滤（`Ctrl+F` 聚焦），翻页按钮换成滚动条（滚轮 / 点击轨道 / 拖动滑块），物品悬停显示 tooltip，状态行新增「货架溢价 ±N%」（`baseBuy`/`baseSell` = 不含 E1 稀缺系数的同一条报价）。
- 客户端/服务端隔离：S2C 一律经 `client/ClientPacketHandlers`；构建后扫描 jar，`net/minecraft/client` 只出现在 `client` 包（70 个非 client 类，0 泄漏）。
- `gradle --no-daemon test build` 通过：**48 个单测全过**（新增 ChartViewTest 4）。jar sha256 前缀 `661d2b8d`，已同步主客户端 / -DEV / mirror / server-pack 暂存目录。
- **DEV 实测发现并修掉一个 E2 遗留 bug**：KubeJS 服务端脚本在资源重载阶段求值，客户端此时集成服务器还没建好，`ServerLifecycleHooks.getCurrentServer()` 是 null，`svIndices.js` 的三次 `SunlitMarket.index(...)` 全部被丢弃（日志里三条 `SunlitMarket.index needs a running server`，但脚本自己的 `[SV-INTEGRATION] price indices registered` 照常打印，极具误导性）。现在定义先进 `PendingIndices` 缓冲，`ServerStartedEvent` 时回放（换世界重进也会重放），提交 `a786346`。`SunlitMarket.index` 改成返回 boolean，另加 `clearIndices()`。
- **DEV 实测又抓到一个 E3 bug**：`MarketData` 存的是裸 OHLC 四元组，而 `ChartView.fromJson` 要求 5 个整数（`{bucket,o,h,l,c}`），于是每根蜡烛都被静默丢掉，面板只会画出空网格。已把加 bucket 的逻辑抽成 `ChartService.withBuckets` 并补 `ChartServiceTest` 4 条（提交 `82d3683`）。单测原来测的是「手写的 5 元组」，所以完全没覆盖生产端。
- DEV 实测证据（2026-11-02 20:04）：日志 `index sv.produce buffered until a world is loaded` ×3 → `applied 3 buffered price indices`；桥接读取 `MarketData` 得到 `sv.produce=948 / sv.metal=1000 / sv.delicacy=987`，1D 与 4H 各 1 根；`ChartService.json` 输出 `candles:[[0,987,987,987,987]]`（5 元组）。
- 观察点：点「行情」后出现 3 个指数页签、蜡烛随交易变化、切 4时 不卡；搜索框输入中文/英文都能过滤；滚动条可拖动且松手后按钮仍可点。蜡烛要跑满一个 bucket 才会出现第二根（4时 = 4000 tick ≈ 3.3 现实分钟），开局只有一根横线是数据量问题。
- 已知小瑕疵：指数页签顺序在重启后会变——`MarketData.load` 用 `CompoundTag.getAllKeys()`（哈希序），未持久化声明顺序。只影响观感，未修。
- 已知限制：蜡烛要在服务器跑满一个 bucket 才会出现第二根，第一天只有一根会显示为一条横线——这是数据量问题，不是渲染问题。

## 13. 接受仓（deposit_bin）

- 方块 + 方块实体 + 菜单 + 估值全部在 `deposit` 包（不引用任何 `net.minecraft.client.*`），只有 `client/DepositBinScreen` 是客户端类。
- 五路入口：物品 `IItemHandler`（1 格）、流体 `IFluidHandler`（8000 mB）、FE `IEnergyStorage`（2M）、TC4R 源质 `EssentiaCapabilities.TRANSPORT`（128 点）、Botania 魔力 `MANA_RECEIVER`（1M）。
- 物品价格默认取当铺自己的收购表（`Offer.sell / count` 取各商人最大值），没有第二份定价。未在货单里的物品（例如金锭）价值为 0，拒绝支付而不是给个随手价；`deposit_values.json` 的 `items` 可以逐物品覆盖（见 §14）。
- 其余四路默认低汇率：流体 1–10 spur/桶（汽油 10、柴油 8、油 6、硫酸 3，其余 1）；FE 每 5 万换 1；源质每点 1；魔力每 2000 换 1。电给得最差是刻意的——GT 的电力没有产能上限，任何有意义的电价都是印钞机。这些数字现在全在配置文件里（§14）。
- `collect()` 先把金额记入虚拟钱包，成功后才清空五路存储；`wallet.enabled=false` 时回落到 `Currency.settle`，付款失败（背包满）时物品不会被销毁。
- 可选模组在运行期才解析：TC4R 用 `libs/` 里的 20748 api jar 编译（与客户端版本一致），只在 `ModList.isLoaded("thaumcraft")` 分支里实例化；Botania 用动态代理，不引入 12 MB 编译依赖，缺模组时不会 NoClassDefFoundError。
- 状态同步：`DepositStateMessage`(S2C id 8) 每 10 tick 与打开时下发；「回收」按钮走 `clickMenuButton(0)`，没有额外的 C2S 包。协议版本从 2 提到 3。
- 贴图由 GTCEu 机壳拼合（`machine_casing_solid_steel` + `machine_casing_gearbox_steel`），生成脚本在 `build/gen_textures.py`，输出 `deposit_bin_{top,side,front}.png`。
- `gradle --no-daemon test build` 通过，构建后扫描 jar：88 个非 client 类，0 个引用 `net/minecraft/client`。
- DEV 实测证据（2026-11-02 22:34）：把漏斗放在接受仓上方，`item replace` 灌 4 个金锭 → 漏斗清空、`data get block` 显示 `Items:[{Slot:0,id:"minecraft:gold_ingot",Count:4}]`，证明 `IItemHandler` 能力确实挂上了；估值函数实测 `4 铁锭=120 / 10 粗铁=90 / 8 桶柴油=64 / 2M FE=40 / 128 源质=128 / 1M 魔力=500`。
- 观察点：右键（空手/潜行）开界面，界面显示五路存量与各自折算值，底部「合计 N spur」+「回收」按钮；手持物品右键直接放入（不进界面）。
- 已知限制：还没有每日回收预算（`ECONOMY_MODEL_V2.md` §7 的 `BuybackBudgetManager`），现在只要塞满就能换钱；源质/魔力的汇率尚未经过实测调参。

## 14. 价格表配置化 + 虚拟钱包（2026-11-02，-DEV 实例实测）

- `config/sunlitmarket/deposit_values.json` 首次启动自动生成默认值，字段：`accept.{item,fluid,energy,essentia,mana}`、`items`、`fluids.default` + 逐流体、`energy.fePerSpur`、`essentia.pointsPerSpur` + `essentia.aspects`、`mana.manaPerSpur`、`wallet.{enabled,exchangeFeePercent,minWithdraw}`。
- 取值优先级：配置 > 当铺收购表 > 0。实测 `iron=30`（配置未覆盖，来自当铺表）、`gold=0`（DEV 没有商人收金锭）、`water=1`（走 `fluids.default`）、`diesel=8`。
- KubeJS 覆盖实测（桥接直接调 `SunlitMarketBinding`）：`depositPrice("minecraft:gold_ingot",250)` → `itemUnit=250`；`depositFluidRate("minecraft:water",3)` → 8000 mB = 24 spur；`depositAccept("mana",false)` → `manaValue(1e6)=0`。之后 `dirty=true`，下一个保存点写盘（日志 `deposit_values.json updated by script`），文件里出现 `"minecraft:gold_ingot": 250` / `"minecraft:water": 3` / `"mana": false`。
- 重启后 `_devprobe.js` 读到 `gold price=250`（上一次脚本覆盖的持久化结果）与 `iron price=999`（本次脚本设定）：「脚本 → 配置 → 磁盘 → 重启 → 生效」整条链路通了。
- 钱包存在 ForgeData 的 `sunlitmarket.wallet`（long），跨死亡/换维度/重载都不丢。`Currency.balance()` = 现金/银行 + 钱包，`cashBalance()` 只算现金/银行。
- 扣款规则：钱包能**整笔**覆盖才用钱包，否则完全回落到硬币/银行，不做跨后端拆分。实测 `pay(500)` 钱包 1044→544 而现金 960 不动；`pay(900)` 钱包不够整笔 → 硬币 960→60。
- 兑换实测（`/execute as @p run sunlitmarket wallet ...`）：`wallet=2044 cash=10` → `withdraw 1000` → 钱包 −1000、取出 950（5% 手续费）→ `wallet=1044 cash=960`，`/sunlitmarket balance` = 2004（总量少 50，手续费直接销毁不铸币）。
- 接受仓回收实测：铁锭 1 个（脚本价 999）→ `回收 +999 → 钱包 2044`，`value()` 归零；证明「先入账再清空」的顺序。
- 踩到的坑：`WalletExchange.deposit` 原来用 `Currency.pay`，钱包一接入就变成「钱包给自己充值」再烧手续费，凭空销毁货币。已拆出 `Currency.payCash`（`settle(..., useWallet=false)`）只走现金。
- GUI：五行入口下多了一条钱包带（余额 / 手续费 / 最低取出 + 存入、取出按钮），按钮走 `clickMenuButton(1|2)`，不需要额外 C2S 包。**排版未做界面验收**，需要人工开一次看。
- 未验证：`wallet.enabled=false` 时接受仓回落 `Currency.settle` 的路径（代码有分支，DEV 没跑）。
- 已知瑕疵：小额兑换会被整数除法吃掉手续费（10 spur 存进去仍是 10，5% 取整为 0），只有 ≥20 spur 才真正扣到 1。

## 15. 每日回收预算（2026-11-03，-DEV 实例实测）

- `BuybackLedger`（SavedData `sunlitmarket_buyback`）记 `{Day, Spent, Players:{uuid:amount}}`，日界线取 `Level.getDayTime() / 24000`，**不用**墙钟——重启、时区、改时间都开不出第二个预算。
- 配置在 `deposit_values.json` 的 `buyback`：`enabled` / `dailyCap`（世界级，默认 100000）/ `playerCap`（每人，默认 20000），0 表示不设上限。
- 实测：初始 `budget=20000`（世界 100000 与每人 20000 取小）；`setBuyback(100,100)` 后 `collect` 被整笔拒绝 → `今日额度只剩 100，本次需 999；明日再来`，钱包 591 不变、仓内仍剩 999；`setBuyback(2000,2000)` 后同一笔成功 → 钱包 1590、仓清零、剩余额度 1001；恢复默认后 `budget=19001`（今天已花 999）。
- 整笔拒绝而不是按剩余额度部分支付：部分支付要么销毁货物，要么留下半格残料，而玩家明天再来就行。界面在「合计」右侧显示 `今日剩 N`，额度不够时按钮置灰。
- 存档往返实测（反射调 `save`/`load`）：`{Day:40L, Players:{<uuid>:999L}, Spent:999L}` → 重新 load 后 `remaining=19001`（与在线实例一致），`today+1` 时 `remaining=20000`（跨日归零）。
- 落盘实测：正常退出客户端后 `saves/asverify/data/sunlitmarket_buyback.dat` 生成（121 B）；重启后 `budget=19001`（= 20000 上限 − 上一局已花 999），证明当日累计跨重启保留。`/save-all` 要权限 4，单人 op（2 级）和控制台桥都跑不了，验证靠正常退出。
- 未验证：跨日真实滚动（需要 `time add 24000` 且预算已花掉），只验证了 `load` 后 `today+1` 的逻辑分支。
- 重启后再测一遍额度：`budget=19001` → 上限 100 时 `budget=0`、拒绝并提示「今日回收额度已用尽」；上限 2000 时 `budget=1001` → 成交 +999、剩 2；恢复默认后 18002。
