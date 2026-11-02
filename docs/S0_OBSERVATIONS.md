# S0：本模经济观察，不是全服货币总量

本层只旁路记录现有行为，不修改价格、买卖方向、日预算、物品交割、钱包/银行选择或原有失败恢复；不会为审计补扣、补发或重放交易。它不实现新账户体系、NPC需求经济体或期货。

## 覆盖

| 来源 | 记录内容 |
|---|---|
| `MARKET_BUY` / `MARKET_SELL` | 普通货架的资金结算步骤 |
| `COUNTER_PURCHASE` | 玩家向NPC收购物品的付款步骤 |
| `DEPOSIT_BIN_PAYOUT` | 接受仓的实际钱包增量，或现金/银行支付 |
| `PAY` / `PAY_CASH` / `GIVE` / `SETTLE` | 兼容Java入口，无法确定业务目的时保留通用来源 |
| `FINE` / `BRIBE` | 既有罚金／贿赂付款步骤 |
| `WALLET_EXCHANGE_IN` / `WALLET_EXCHANGE_OUT` | 现金与钱包两条资金变化，共享operationId，各自只计一次 |
| `KJS_WALLET_ADD` / `KJS_WALLET_TAKE` | 直接脚本钱包操作的实际本地NBT变化 |
| `VALUATION_MAPPING_CHANGED` | 已生效面额映射及SHA-256版本，不计净发行 |
| `SESSION_START` / `SESSION_STOP` | 观察生命周期，不产生资金 |

`eventId`唯一标识一条观察；`operationId`用于关联兑换的两条记录，并不防止或重做业务。原结算实现直接给出已选择的COINS/BANK/WALLET后端，审计不额外调用银行。Wallet.add达到long上限时，requestedDelta可能大于confirmedDelta，记录实际变化而不是假定全额入账。

COINS的grossCoinCreated/grossCoinRemoved只来自成功结算中已计算的找零步骤。例如扣100找95：grossRemoved=100、grossCreated=95、confirmedDelta=-5。失败预演不会被当作真铸币。

**observedNet是已覆盖资金步骤的合计，不是全服净发行或M0。** 币作为交易商品时，商品本身的面值变化不属于payment leg；直接Wallet.set、命令/其他mod造币、玩家丢币/箱子转账、离线账户和外部银行写入均不在自动覆盖中。开关关闭、未启动、非服务端线程的调用也不保证记录。完整的业务结果（例如付款后另一个模块是否成功修改库存）不能从单条settlement记录推断。

当前`M0=UNKNOWN`，`C/R=NOT_IMPLEMENTED`；已有接受仓日预算仍按原规则工作，S0不把它当作准备金。

## 文件与生命周期

每个世界写在：

```
<world>/sunlitmarket/audit/events-<epoch>.jsonl
<world>/data/sunlitmarket_audit.dat
```

JSONL schema 2含UTC时间、UTC日期、gameTime/dayTime、来源、后端、结果、请求/确认金额、估值版本和序号。SavedData是统计checkpoint，含UTC日报、来源计数和最近64个明确丢失序号。

后台单writer使用有界队列（2048，包括尚未消费的写入回执），经济线程只非阻塞入队；磁盘、序列化失败不改变原交易。第一次I/O错误后该writer不再尝试追加可能存在半行的文件，后续事件明确丢弃，等待正常重启新epoch。状态分开报告pending、ioErrors、observerErrors、dropped；不会把同一丢失计数相加两次。

服务端tick消费回执，只在服务端线程修改SavedData。停服最多等待writer两秒，并报告超时/错误；不会等待无限时间。文件flush不是磁盘事务保证，JSONL与SavedData也不原子。异常退出可能出现checkpoint和日志不一致，或无法识别的末尾缺失；不会自动扫日志重放、补账或纠正余额。恢复时保留累计统计而不把旧JSONL再次累加。

## 命令与开关

需要权限2：

```
/sunlitmarket audit status
/sunlitmarket audit gaps
/sunlitmarket audit day
/sunlitmarket audit day 2026-11-02
```

前三种查询不制造经济事件；`day`只读已有UTC桶，没有数据时显示NO_OBSERVATIONS，绝不生成假K线或补齐空白日。UTC日期可能与本地时间不同，查询不是新的经济日历，也不会改变原来的dayTime预算重置。

开发/诊断可用JVM参数 `-Dsunlitmarket.audit.disabled=true` 关闭事件采集；交易仍走完全相同的支付实现。重新启用会留下真实观察空档，不回填期间事件。

## 验证记录与限制

本次验证：

- 全套JUnit：76项，0失败（其中20项审计相关），包括实际文件写入、不可写目录、注入磁盘故障、队列满、慢writer限时关闭、失败/UNKNOWN分类、BigInteger累计、UTC日报保存加载、正常/非正常epoch以及兑换差额。
- 单次隔离minimal专服会话调用真实Currency：审计开启/关闭各7项，共14项通过；钱包优先支付、现金专用扣款、失败不改变余额、商品交割、双向兑换、钱包封顶。
- JSONL实际检查：12条唯一连续序号记录；兑换100现金→95钱包净-5，取50钱包→48现金净-2（沿用原整数手续费规则）；封顶请求+10实际+2；测试支付步骤observedNet=-535，包含正常停止标记。
- 正常保存停服后再次启动：同一UTC日仍为observedNet=-535，旧JSONL未被重复计入；cleanStop只在本次进程正常结束后写回，因此运行中状态显示false。
- 此前DEV客户端曾被强制终止；那次不能算S0正常停服、持久化或七天观测验证。
- 生产Numismatics银行未实机测试；单元状态测试不代表银行API/持久化已验收。
- 七天连续观察尚未完成；只出现七个日期或启动时间过去七天不能算通过，必须检查实际覆盖、空档、UNKNOWN、I/O错误及运行版本。

隔离自检命令仅用 `-PcoreMinimal -PauditSmoke runServer` 才注册：

```
/sunlitmarket audit smoketest
```

它拒绝有真实在线玩家或安装银行的环境，以独立FakePlayer临时库存/钱包执行并恢复fixture。不是生产观察命令，不自动执行。fixture的原始初始化/恢复不在观察范围，因此测试净流量不能当作存档总货币变化。

## 非目标

不扫描在线/离线全服资产，不自动配置ME/村民pool，不修钱包/接受仓原有经济政策，不修改K线，不发布到主实例、DEV或mirror。客户端与界面均未用于本次S0修正验收。
