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
- Node 静态执行（完整价格数组）：650 条规则 / 635 个货池 / 15 条 NPC 规则；0 重复、0 个 `askPrice >= marketValue`；bread 22/14。
- FTBQ 重点商品覆盖：crops 89/89、fishing 75/75、pantry 12/12、minerals 44/44；AS/TC/BOT 材料 20 条；GT/机器/收藏品未作为顾客商品。
- `gradle --no-daemon test build` 通过，生成 jar 并同步主客户端 / -DEV / mirror（sha256 前缀 `68b67749`）。

待服务器验收：实际实体职业名、物品 ID、货池生成、柜台 UI、ME 商人收购条目、mastery 阶段同步。
