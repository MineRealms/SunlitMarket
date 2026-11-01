// priority: 0
// ============================================================
// SunlitMarket dev harness + automated self-check.
//
// Runs once per world join in run-client/saves/smtest and logs
// [SM-TEST] PASS/FAIL lines to latest.log so the market can be
// verified head-lessly. The GUI is opened as well, so the same
// numbers can be eyeballed manually.
// ============================================================
SunlitMarket.clear()
SunlitMarket.coinValue('minecraft:emerald', 1)
SunlitMarket.priceMultiplier('test_farmer', 'minecraft:bread', 1.5)

SunlitMarket.merchant({
  id: 'test_farmer',
  name: 'Test Farmer',
  faction: 'test:village',
  tag: 'sunlitmarket.test',
  offers: [
    { item: 'minecraft:wheat', count: 1, buy: 2, sell: 1, stock: 5, limit: 3, repGain: 1 },
    { item: 'minecraft:bread', count: 1, buy: 4, sell: 2, reputation: 5, unlock: 'test:bakery' }
  ]
})

// Stock-only merchant: no per-player limit, 2 units of daily stock.
SunlitMarket.merchant({
  id: 'test_stock_only',
  name: 'Stock Only',
  faction: 'test:village',
  tag: 'sunlitmarket.stocktest',
  offers: [
    { item: 'minecraft:apple', count: 1, buy: 1, sell: 1, stock: 2 }
  ]
})

const results = []
const check = (name, ok, detail) => {
  results.push((ok ? 'PASS' : 'FAIL') + ' ' + name + (detail ? ' | ' + detail : ''))
}
const view = (player, id) => JSON.parse(SunlitMarket.view(player, id))

let opened = false
PlayerEvents.tick((event) => {
  if (opened) return
  if (event.player.age < 60) return
  opened = true
  const player = event.player

  // State restored from the previous session, then a clean day for assertions.
  console.info('[SM-TEST] restored view ' + SunlitMarket.view(player, 'test_farmer'))
  event.server.runCommandSilent('time add 24000')
  // Reputation persists across sessions and discounts prices, so reset it first.
  SunlitMarket.setReputation(player, 'test:village', 0)

  const startBalance = SunlitMarket.balance(player)
  event.server.runCommandSilent('give ' + player.username + ' minecraft:emerald 64')
  SunlitMarket.open(player, 'test_farmer')

  const v0 = view(player, 'test_farmer')
  check('balance.read', v0.balance === startBalance + 64, 'balance=' + v0.balance)
  check('day.resetAtStart', v0.offers[0].used === 0 && v0.offers[0].stock === 5,
    'used=' + v0.offers[0].used + ' stock=' + v0.offers[0].stock)
  check('limit.init', v0.offers[0].limit === 3)
  check('price.multiplier', v0.offers[1].buy === 6 && v0.offers[1].sell === 3,
    'bread buy=' + v0.offers[1].buy + ' sell=' + v0.offers[1].sell)
  check('unlock.init', v0.offers[1].locked === false) // granted by the previous run

  check('buy.one', SunlitMarket.buy(player, 'test_farmer', 0, 1))
  const v1 = view(player, 'test_farmer')
  check('stock.decrement', v1.offers[0].stock === 4, 'stock=' + v1.offers[0].stock)
  check('limit.counted', v1.offers[0].used === 1, 'used=' + v1.offers[0].used)
  check('rep.gain', v1.reputation === 1, 'rep=' + v1.reputation)

  check('limit.overAsk', SunlitMarket.buy(player, 'test_farmer', 0, 3) === false)
  check('limit.fill', SunlitMarket.buy(player, 'test_farmer', 0, 2)
    && view(player, 'test_farmer').offers[0].used === 3)
  check('limit.blocked', SunlitMarket.buy(player, 'test_farmer', 0, 1) === false
    && view(player, 'test_farmer').offers[0].used === 3)

  SunlitMarket.revoke(player, 'test:bakery')
  check('unlock.blocked', SunlitMarket.buy(player, 'test_farmer', 1, 1) === false
    && view(player, 'test_farmer').offers[1].locked === true)
  SunlitMarket.unlock(player, 'test:bakery')
  check('unlock.granted', SunlitMarket.isUnlocked(player, 'test:bakery')
    && view(player, 'test_farmer').offers[1].locked === false)

  check('rep.gate', SunlitMarket.buy(player, 'test_farmer', 1, 1) === false)
  SunlitMarket.setReputation(player, 'test:village', 200)
  const v2 = view(player, 'test_farmer')
  check('price.discount', v2.offers[1].buy === 5, 'bread buy=' + v2.offers[1].buy + ' rep=' + v2.reputation)
  check('buy.afterRep', SunlitMarket.buy(player, 'test_farmer', 1, 1))

  const wheat = SunlitMarket.countItems(player, 'minecraft:wheat')
  const before = SunlitMarket.balance(player)
  const sold = SunlitMarket.sell(player, 'test_farmer', 0, 1)
  const after = SunlitMarket.balance(player)
  check('sell.hasWheat', wheat >= 1, 'count=' + wheat)
  check('sell.ok', sold === true, 'returned=' + sold)
  check('sell.change', after === before + 1, 'before=' + before + ' after=' + after)

  check('stock.buy.all', SunlitMarket.buy(player, 'test_stock_only', 0, 2))
  check('stock.blocked', SunlitMarket.buy(player, 'test_stock_only', 0, 1) === false)

  // Daily reset: advancing one in-game day restores stock and clears limits.
  event.server.runCommandSilent('time add 24000')
  const vDay = view(player, 'test_farmer')
  check('day.stockReset', vDay.offers[0].stock === 5, 'stock=' + vDay.offers[0].stock)
  check('day.limitReset', vDay.offers[0].used === 0, 'used=' + vDay.offers[0].used)

  const failed = results.filter((line) => line.startsWith('FAIL')).length
  results.forEach((line) => console.info('[SM-TEST] ' + line))
  console.info('[SM-TEST] ===== ' + (results.length - failed) + ' passed, ' + failed + ' failed =====')
  console.info('[SM-TEST] final view ' + SunlitMarket.view(player, 'test_farmer'))
})
