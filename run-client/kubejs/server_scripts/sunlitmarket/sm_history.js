// priority: 0
// Dev-only: seed real index history for the chart panel in run-client/saves/smtest.
//
// Every bar comes from an executed MarketService trade (buy/sell through the normal money
// path), never from a quote sample, and the bars are bucketed by EconomyClock (= game time),
// so the history is the same shape a live server would produce. The seeder walks game time
// forward one 4h bucket at a time and trades once per bucket. It is idempotent: once the index
// already holds bars it does nothing, so re-joining the world never re-seeds.
//
// Either way the script re-registers its merchants/indices and re-opens the chart panel on
// every join, because script merchants live in memory only.
//
// It lives in run-client only and is never shipped to Sunlit Valley.

if (!global.smHistory) global.smHistory = {ran: false}

let histFarmer = {
  id: 'hist_farmer',
  name: 'History Farmer',
  faction: 'test:village',
  tag: 'sunlitmarket.history.farmer',
  offers: [
    { id: 'wheat', item: 'minecraft:wheat', count: 1, buy: 12, sell: 7 },
    { id: 'carrot', item: 'minecraft:carrot', count: 1, buy: 16, sell: 9 },
    { id: 'potato', item: 'minecraft:potato', count: 1, buy: 20, sell: 12 },
    { id: 'bread', item: 'minecraft:bread', count: 1, buy: 28, sell: 16 }
  ]
}

let histSmith = {
  id: 'hist_smith',
  name: 'History Smith',
  faction: 'test:village',
  tag: 'sunlitmarket.history.smith',
  offers: [
    { id: 'coal', item: 'minecraft:coal', count: 1, buy: 24, sell: 14 },
    { id: 'copper', item: 'minecraft:copper_ingot', count: 1, buy: 40, sell: 24 },
    { id: 'iron', item: 'minecraft:iron_ingot', count: 1, buy: 60, sell: 36 }
  ]
}

PlayerEvents.tick((event) => {
  let state = global.smHistory
  if (state.ran) return
  if (event.player.age < 200) return
  state.ran = true

  let player = event.player
  let server = event.server
  let world = server.overworld()
  let MarketData = Java.loadClass('com.society.sunlitmarket.market.MarketData')
  let data = MarketData.get(server)

  // Emerald is the run-client coin; the pack ships its own mapping.
  SunlitMarket.coinValue('minecraft:emerald', 1)
  SunlitMarket.merchant(histFarmer)
  SunlitMarket.merchant(histSmith)
  SunlitMarket.index('hist.harvest', 'Harvest Basket', [
    ['hist_farmer', 'minecraft:wheat'],
    ['hist_farmer', 'minecraft:carrot'],
    ['hist_farmer', 'minecraft:potato'],
    ['hist_farmer', 'minecraft:bread']
  ])
  SunlitMarket.index('hist.metal', 'Metal Basket', [
    ['hist_smith', 'minecraft:coal'],
    ['hist_smith', 'minecraft:copper_ingot'],
    ['hist_smith', 'minecraft:iron_ingot']
  ])

  let seeded = data.candles('hist.harvest', 4000, 120).size()
  if (seeded >= 40) {
    console.info('[SM-HISTORY] already seeded: ' + seeded + ' 4h bars, ' + data.fills().size() + ' fills; skipping')
  } else {
    seed(player, server, world, data)
  }

  // Open the panel for the player: the command needs a player source, so run it as them.
  let opened = server.runCommandSilent('execute as ' + player.username + ' run sunlitmarket chart hist.harvest')
  console.info('[SM-HISTORY] chart request result=' + opened)
})

function seed(player, server, world, data) {
  // ServerLevel.getGameTime() is inherited from Level and KubeJS cannot see it, so read and
  // write the clock through ServerLevelData, which declares both members itself.
  let clock = world.getLevelData()
  let now = -1
  try { now = clock.getGameTime() } catch (err) { console.info('[SM-HISTORY] clock read failed: ' + err) }
  if (now < 0) {
    console.info('[SM-HISTORY] no readable game clock; aborting')
    return
  }
  try {
    clock.setGameTime(now + 20)
    now = clock.getGameTime()
    console.info('[SM-HISTORY] clock writable, now=' + now)
  } catch (err) {
    console.info('[SM-HISTORY] clock not writable: ' + err)
    return
  }

  let farmerItems = ['minecraft:wheat', 'minecraft:carrot', 'minecraft:potato', 'minecraft:bread']
  let smithItems = ['minecraft:coal', 'minecraft:copper_ingot', 'minecraft:iron_ingot']
  let bars = 60
  let start = now + 380
  let ok = 0
  let failed = 0
  let sold = 0

  for (let k = 0; k < bars; k++) {
    // One trade per 4h bucket, ten in-game days in total.
    clock.setGameTime(start + k * 4000)
    let farmer = k % 3 !== 2
    let merchant = farmer ? 'hist_farmer' : 'hist_smith'
    let pool = farmer ? farmerItems : smithItems
    let slot = k % pool.length
    let item = pool[slot]
    // A slow deterministic wave so the basket walks up and down instead of flatlining.
    let mult = 1.0 + 0.30 * Math.sin(k * 0.55) + 0.12 * Math.sin(k * 2.30 + 1.10)
    SunlitMarket.priceMultiplier(merchant, item, Math.max(0.5, Math.min(2.0, mult)))
    // Top the purse up so a bucket can never fail on funds; the purse is not the subject.
    server.runCommandSilent('clear ' + player.username + ' minecraft:emerald')
    server.runCommandSilent('give ' + player.username + ' minecraft:emerald 256')
    if (SunlitMarket.buy(player, merchant, slot, 1 + (k % 2))) ok++
    else failed++
    // A sale every fourth bucket, so the tape carries both sides of the book.
    if (k % 4 === 3 && SunlitMarket.sell(player, merchant, slot, 1)) sold++
  }

  server.runCommandSilent('clear ' + player.username + ' minecraft:emerald')
  console.info('[SM-HISTORY] fills=' + data.fills().size() + ' buys=' + ok + ' sells=' + sold + ' failed=' + failed)
  console.info('[SM-HISTORY] harvest 1d=' + data.candles('hist.harvest', 24000, 120).size()
      + ' 4h=' + data.candles('hist.harvest', 4000, 120).size() + ' value=' + data.value('hist.harvest'))
  console.info('[SM-HISTORY] metal 1d=' + data.candles('hist.metal', 24000, 120).size()
      + ' 4h=' + data.candles('hist.metal', 4000, 120).size() + ' value=' + data.value('hist.metal'))
  if (failed > 0) console.info('[SM-HISTORY] view ' + SunlitMarket.view(player, 'hist_farmer'))
}
