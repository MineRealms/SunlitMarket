// Offline smoke test for meSeasonPrices.js (no Minecraft needed).
// Loads the real script with stubbed KubeJS globals and prints the multiplier
// table for every season and a few in-season progress values.
//
//   node tools/season_check.js "<path to meSeasonPrices.js>"
const fs = require('fs')

const path = process.argv[2]
if (!path) {
  console.error('usage: node tools/season_check.js <meSeasonPrices.js>')
  process.exit(1)
}
const source = fs.readFileSync(path, 'utf8')

const applied = []
let loadedHook = null
let tickHook = null

const SEASON = { value: 'winter', progress: 0.0 }
const sandbox = {
  console: { info: () => {}, warn: (m) => console.error('WARN', m) },
  Platform: { isLoaded: (id) => id === 'sereneseasons' },
  Java: {
    loadClass: () => ({
      getSeasonState: () => ({
        getSeason: () => SEASON.value.toUpperCase(),
        getSeasonDuration: () => 100,
        getSeasonCycleTicks: () => Math.round(SEASON.progress * 100),
      }),
    }),
  },
  SunlitMarket: {
    priceMultiplier: (merchant, item, value) => applied.push([merchant, item, value]),
  },
  ServerEvents: {
    tick: (fn) => { tickHook = fn },
    loaded: (fn) => { loadedHook = fn },
  },
}

new Function(...Object.keys(sandbox), source)(...Object.values(sandbox))

if (!loadedHook || !tickHook) {
  console.error('FAIL: script did not register ServerEvents hooks')
  process.exit(1)
}

const sample = (season, progress) => {
  SEASON.value = season
  SEASON.progress = progress
  applied.length = 0
  loadedHook({ server: { overworld: () => ({}) } })
  const pick = (item) => {
    const hit = applied.find((entry) => entry[1] === item)
    return hit ? hit[2] : NaN
  }
  return { leek: pick('me:leek'), mushroom: pick('me:white_mushroom'), count: applied.length }
}

let failed = 0
const expect = (label, actual, target) => {
  const ok = Math.abs(actual - target) < 0.011
  if (!ok) failed++
  console.log((ok ? 'PASS' : 'FAIL') + ' ' + label + ' expected=' + target + ' actual=' + actual)
}

console.log('items registered: ' + sample('spring', 0).count)
expect('spring start leek', sample('spring', 0).leek, 0.90)
expect('summer start leek', sample('summer', 0).leek, 0.95)
expect('autumn start leek', sample('autumn', 0).leek, 1.10)
expect('winter start leek', sample('winter', 0).leek, 1.40)
// Season cycle ticks never reach the full duration, so probe just before the edge.
expect('winter end leek (->spring)', sample('winter', 0.99).leek, 0.90)
expect('winter mid leek (smoothstep)', sample('winter', 0.5).leek, 1.15)
expect('winter mushroom', sample('winter', 0).mushroom, 1.10)
expect('autumn mushroom', sample('autumn', 0).mushroom, 0.90)
console.log(failed === 0 ? 'ALL PASS' : failed + ' FAILED')
process.exit(failed === 0 ? 0 : 1)
