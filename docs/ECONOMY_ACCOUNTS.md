# 资金账户、NPC需求、成交K线与P2P期货

本层解决S0观察到的五个缺口：系统付款凭空铸币、没有C/R/NPC预算、绿宝石默认是钱、K线来自报价、期货未实现。原则：**不限制玩家能生产多少，只限制系统愿意为这些东西支付多少货币**。

## 经济时钟

`core/EconomyClock`：`day = overworld.getGameTime() / 24000`。gameTime每tick只加一，`/time set|add`和睡觉都改不动它。资金日结、NPC预算、接受仓日额度、K线分桶、期货到期全部用它。货架补货（StockData/LimitData）和柜台顾客配额（CustomerLedger）仍按可见日期，不在本次范围。

旧存档的接受仓日额度原来按dayTime记录，升级后第一次读取会把当天额度重置一次，不会多发钱。

## 账户

| 账户 | 含义 | 来源 | 去向 |
|---|---|---|---|
| C 当铺现金 | 系统收购用的钱柜 | 货架卖给玩家的货款；R每日补足 | 接受仓兑付、货架收购 |
| R 发行准备金 | 唯一的新钱来源 | 初始额度、管理员注资、玩家付给NPC的钱、罚金、贿赂、C的超额回扫 | 补C、NPC求购预算 |
| burned | 销毁计数（不是账户） | 钱包兑换手续费、期货手续费 | — |
| E 期货托管 | 见下文期货 | 双方保证金与挂单手续费 | 到期分配、作废退款、撤单 |

系统净发行 ≤ R初值 + 管理员净注资；R不会变负，C不足就拒绝付款。

`TreasuryData`（`<world>/data/sunlitmarket_treasury.dat`）记录C、R、burned、当日NPC已花、管理员注资和按来源的流入/流出。世界第一次创建时写入初值。

### 资金流向

| 流程 | 方向 | 不足时 |
|---|---|---|
| 接受仓兑付 `DEPOSIT_BIN_PAYOUT` | C → 玩家 | 整单拒绝“当铺现金不足”，货物不动；原每日/每人额度仍然有效 |
| 货架收购 `MARKET_SELL` | C → 玩家 | 拒绝，物品不动 |
| 货架售货 `MARKET_BUY` | 玩家 → C | — |
| 柜台向NPC收购 `COUNTER_PURCHASE` | 玩家 → R | — |
| NPC求购 `NPC_DEMAND` | R → 玩家，受NPC日预算限制 | 不生成请求；接受时预算不足则拒绝 |
| 罚金 `FINE`、贿赂 `BRIBE` | 玩家 → R | — |
| 钱包兑换手续费、期货手续费 | 销毁 | — |

付款顺序：先从账户扣出，再执行原结算；结算失败就退回账户。若结算过程中玩家被标记为`bankUncertain`（银行写入结果不确定），不退回并写日志——宁可系统少付，不会重复付。

### 日结

每个经济日第一次访问时（另外每200 tick检查一次）：

1. C低于`cashTarget`时，从R转入`min(差额, dailyRefill, R)`；
2. C高于`cashCeiling`时，多出部分转回R；
3. 当日NPC已花清零。

停服期间错过的天数只结算一次，不补发。

### 配置 `sunlitmarket-core.toml` `[treasury]`

| 键 | 默认 | 说明 |
|---|---|---|
| `enabled` | true | false回到旧的“系统付款直接铸币”，仅作应急回退 |
| `initialCash` / `initialReserve` | 200000 / 5000000 | 世界第一次创建时写入 |
| `cashTarget` / `cashCeiling` | 200000 / 400000 | 日结补足目标、回扫上限 |
| `dailyRefill` | 100000 | 每日最多从R补给C |
| `npcDailyBudget` | 50000 | NPC每日最多为求购付出的钱 |

`[trade] indexOwnerCapPerCandle`（默认64）：单个owner（玩家或FTB队伍）在一根K线里最多计入的有效手数。

### 命令与脚本

```
/sunlitmarket treasury status
/sunlitmarket treasury inject cash|reserve <±amount>     # 管理员增发/回收，单独计入injected
```

KubeJS：`SunlitMarket.treasury()`返回JSON；`SunlitMarket.treasuryInject('cash'|'reserve', delta)`。`/sunlitmarket audit status`中的C、R、burned、E、futuresClaims现在是真实值，M0仍为UNKNOWN。

## 绿宝石

`minecraft:emerald`不再是默认货币。原版村民和战利品中的绿宝石曾按1 spur计算，是一个不受控的造币来源。需要绿宝石当钱的环境必须显式调用`SunlitMarket.coinValue('minecraft:emerald', 1)`（开发测试脚本已经这样做）。

## NPC求购（消费预算）

商品池新增两个可选字段：

```js
SunlitMarket.rule({ type: 'pool', id: 'shire_wants_bread', faction: 'me:shire', item: 'minecraft:bread', count: 4,
  baseValue: 8, marketValue: 10, direction: 'buy', bidPrice: 10 })
```

- `direction`：`sell`（默认，NPC卖给玩家）、`buy`（NPC向柜台买）、`both`（每次随机一种）。
- `bidPrice`：NPC出价，默认等于`marketValue`。

求购请求只在当日NPC预算够付出价时生成。柜台按钮为“出售／+10%／+20%／拒绝”。出售时货物必须已放进柜台存储，物品和NBT必须完全一致（带赃物标记的物品不算），之后被顾客带走消耗，成为物品出口；钱从R和当日预算支付。加价的成功率公式与还价对称，失败则顾客离开并扣少量结盟。出价不超过市价110%给公平奖励，超过140%扣结盟。

## K线：只记录真实成交

- 货架成交后记一条`TradeFill`：实际单价，以及去掉个人结盟折扣和通缉加价后的市场比率（价格倍率×库存稀缺度）。个人折扣不会拉低指数。币类或固定价商品不计入。
- 只有指数成员(merchant,item)发生成交时指数才变化；没成交过的成员按1.0计。
- 每根K线：`[bucket, open, high, low, close, volume, weight, traders]`。weight是按owner封顶后的有效手数，traders是独立成交方数。没有成交的时段没有K线，不再补平线。
- 旧版（报价采样）K线在加载时丢弃，成员定义保留。
- 行情界面另外显示“报价参考”（按当前货架报价计算），只供对照，不参与结算。
- JSON导出：停服时以及执行`/sunlitmarket market export`时，写入`<world>/sunlitmarket/export/indices.json`（K线+最近512笔成交）和`futures-history.json`。权威数据仍是SavedData。

## P2P期货

交易所不当对手方，只保管托管金并销毁手续费。

### 开放

```js
SunlitMarket.futures({ index: 'sv.metal', multiplier: 10, tenorDays: 7, band: 0.2, feeBps: 50,
  maxLots: 20, minTraders: 2, minBuckets: 2, windowTicks: 24000, unlock: '' })
```

除`index`外都有默认值。也可以写在`config/sunlitmarket/futures.json`（数组，脚本优先）。没有定义规格的指数不开放期货。挂单和合约会保存开仓时的条款，之后修改或删除规格不影响已有持仓。

### 规则

- **系列**：`<index>@<到期日>`，到期日是tenor的整数倍；到期前`windowTicks`为结算窗口，窗口开始后该系列停止挂单和吃单。
- **有界全额托管**：合约价P对应价带L=⌊P(1−band)⌋、U=⌈P(1+band)⌉。多头每手托管(P−L)×multiplier，空头每手托管(U−P)×multiplier。到期价S截到[L,U]，多头得(S−L)×mult，空头得(U−S)×mult，两者之和正好等于托管总额；不需要追加保证金，也没人会欠款。
- **熔断**：挂单和吃单都要求最近一个经济日内有成交形成的参考价，并且合约价在参考价±band以内；否则暂停交易。
- **禁止自成交和关联账户**：吃单方的UUID和owner（FTB队伍）都必须与挂单方不同。
- **持仓上限**：每个owner在每个系列中，挂单与合约合计不超过maxLots。
- **手续费**：双方各付合约价值的feeBps。挂单方的手续费随挂单一起托管，成交时销毁，未成交部分退回；吃单方的手续费在成交时销毁。
- **结算**：用窗口内4小时K线的收盘价，按weight加权平均（TWAP）；先剔除偏离窗口中位数超过band的K线。只有剩余K线数≥minBuckets、独立成交方≥minTraders时才算可信；否则作废，双方托管金全额退回（手续费不退）。到期时还没成交的挂单退回托管金和未用手续费。结算历史保留最近256条。
- **领款**：收益和退款先记入claims账本。在线玩家立即发放（启用钱包时进钱包，否则给现金），离线玩家登录时补发，也可以手动执行`claim`。银行写入不确定时，这笔款视为已发并写日志，由管理员核对。

### 命令

```
/sunlitfutures list [index]
/sunlitfutures offer <index> long|short <lots> <price>
/sunlitfutures take <id> <lots>
/sunlitfutures cancel <id>
/sunlitfutures mine
/sunlitfutures claim
/sunlitmarket futures status|history        # 权限2
```

KubeJS：`SunlitMarket.futuresJson(index)`返回只读挂单簿JSON。本轮没有期货界面。

数据文件：`<world>/data/sunlitmarket_futures.dat`。状态中的`consistent`检查托管不变式：E = 剩余挂单（托管金+手续费）+ 全部合约价带价值。

## 验证记录（2026-11-03，隔离minimal专服，未开客户端）

- JUnit 109项全部通过：资金日结、补足、回扫、不补发、NPC预算、注资；成交K线、owner封顶、空档、v1丢弃、NBT往返；TWAP剔除异常值和可信度；期货零和、价带截断、手续费、到期日、托管不变式、作废退款、NBT往返；商品池方向校验；默认无绿宝石。
- `/sunlitmarket audit econsmoketest`（需`-PcoreMinimal -PauditSmoke`）25项通过：两个FakePlayer覆盖货架买入记入C、收购扣C、C为0时拒绝；接受仓扣C、C为0时拒绝并保留货物；NPC求购消耗柜台货物并从R预算付款、超预算拒绝；柜台收购钱回到R；成交生成K线；期货挂单托管、自成交拒绝、超量吃单拒绝、持仓上限、熔断价带、可信结算（S=1100）与作废退款同时发生、托管归零、双方净变化等于销毁的手续费。连续运行两次后资金状态不变；`/sunlitmarket audit smoketest` 14项回归通过，并恢复资金快照。
- 持久化：`econsmoketest keep`保留已结算的K线和期货历史，再用`treasury inject cash -1234`改动C，正常`save-all flush`后停服并重启：C=198766、销毁计数、两条期货历史、指数值都正确恢复；等待多个tick后历史仍为2条，没有重复结算。之后已把C补回原值。隔离世界中留下了测试用指数`__econ_smoke*`和对应的期货历史。

## 尚未验证与已知限制

- 柜台“NPC求购”按钮、K线“报价参考”和悬停信息只做到编译通过，界面效果需要用户亲自检查。
- Numismatics银行实机、FTB真实队伍、多名真人玩家的期货流程均未实测。单人服务器默认minTraders=2，期货会一直作废；小服务器可在规格中设为1，但这样操纵风险更高。
- owner封顶以单根K线为单位；一个账户在每根K线都交易，仍能占窗口内较多权重，靠minTraders和中位数剔除来限制。
- 货架价格由库存决定，所以“真实成交”反映的是玩家以库存定价实际成交的价格，不是自由撮合价。
- `econsmoketest`不会还原货架库存/限购计数、接受仓日额度账本，以及测试中产生的审计事件；这些都只出现在隔离世界里。
