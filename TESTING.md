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
