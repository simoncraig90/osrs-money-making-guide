# Money Making Guide

A RuneLite plugin that browses the [OSRS Wiki money making guides](https://oldschool.runescape.wiki/w/Money_making_guide),
hides the ones your account cannot do, and reprices every method against live Grand
Exchange data.

**641 methods**, filtered against your real skill levels, quest states, membership and
bankroll. Click any method to open its wiki guide.

## How it works

### The data does not come from scraping

The wiki runs the [Bucket](https://oldschool.runescape.wiki/w/Special:Version) extension
and exposes it through `api.php`, so every guide is available as structured JSON in one
request — no HTML parsing:

```
GET https://oldschool.runescape.wiki/api.php?action=bucket&format=json&query=
    bucket('money_making_guide').select('page_name','json','value','recurring').limit(5000).run()
```

`tools/build_dataset.py` turns that into `mmg-data.json`: machine-readable skill
requirements, quest names, and item ids resolved against the price API's `/mapping`
endpoint (1313 distinct items, all of which resolve). It runs weekly in CI and publishes
to GitHub Pages; the plugin fetches from there and caches for 12 hours, falling back to
a copy bundled in the jar. That keeps the guide data current without waiting on Plugin
Hub releases.

### Prices come from the real-time API

`prices.runescape.wiki/api/v1/osrs/latest` reports the last instant-buy (`high`) and
instant-sell (`low`) for every tradeable item, refreshed every 5 minutes.

Those are two different numbers, and which applies depends on which side of the trade
you are on. The default **Instant** price basis pays `high` for inputs and takes `low`
for outputs. Collapsing the spread to a single "price", as most calculators do, quietly
overstates profit on every line. Two other bases are available: **Midpoint** (assumes
your offers fill) and **Wiki guide price** (matches the published table).

Grand Exchange tax is applied to outputs only: 2% of the sale price, floored, capped at
5m, skipped below 100gp and for the 41 exempt items. This mirrors `Module:Mmgtable` on
the wiki exactly — `ProfitCalculatorTest` pins each rule.

> **Note on the wiki's stored numbers.** The `value` field in the bucket is *pre-tax* —
> the wiki applies tax when it renders the table, not when it stores the figure. The
> plugin ignores it and always computes from live prices. Reproducing the wiki's stored
> value from its own baked prices matches on **641/641** methods, which is what confirms
> the scaling formula below is right.

### Per-kill scaling

Guides quote quantities either per hour or per kill. Per-kill guides carry a rate
(`Kills per hour`, `Trips per hour`, `Casts per hour`); lines not already marked hourly
are multiplied by it. The rate is not always a whole number — 14 guides run at rates like
2.5/hr.

## The skill filter

Requirements are machine-readable on the wiki (`data-skill="Runecraft" data-level="44"`)
and are compared against `client.getRealSkillLevel()`. Anything you do not meet is hidden
by default.

The subtlety is that roughly half of all skill lines carry a qualifier, and they do not
all mean the same thing:

| Wiki text | Meaning | Behaviour |
|---|---|---|
| `76 Fishing` | hard gate | hidden below 76 |
| `44 Runecraft (91 recommended)` | gate is 44; 91 is aspirational | shown at 44+, warns below 91 |
| `21 Mining (70+ recommended — optional)` | not a gate | never hidden |
| `Skill: None` | no requirement | always shown |

Reading *recommended* as a gate would hide methods from exactly the players who most need
them. Across the dataset: 445 methods are hard-gated, 195 have no gate at all, and 104
carry a separate recommended level.

Three entries in the same field are not real skills — `Combat level`, `Quest points` and
`Skills`. Combat level is enforced against the player's actual combat level; the other
two are shown on the card but never enforced.

## Quests and bankroll

**Quests** are warnings, not filters, by default. The wiki frequently needs only a quest
*started* or *partially done* — "Partial completion of Troll Stronghold, must have
defeated Dad" — and `QuestState` cannot express that. Turn on *Treat quests as hard
requirements* if you would rather over-filter than under-filter.

**Levels without logging in.** Set a username under *Filter by hiscores* and the panel
pulls levels from the OSRS hiscores, so the filter works before you are in game — which
is exactly when you are deciding what to go and do. Live client levels always take
precedence while logged in. Quests are never enforced on this path, since the hiscores do
not report them.

**Bankroll** counts coins in your inventory plus your bank, but the client can only read
the bank after you have opened it once in a session; until then the panel says so. Set a
*Bankroll override* to skip the whole business.

Worth knowing: the binding constraint is often the **4-hour GE buy limit** rather than
your cash. Limits are in the dataset (`buyLimit`) but are not yet enforced — see below.

## Building

```sh
./gradlew build      # compiles and runs the tests
./gradlew test
```

## Running it

```sh
./run-dev.sh         # builds and launches RuneLite with the plugin loaded
```

Two things make this less obvious than it should be, both worth knowing before trying
something simpler.

**The official app cannot load it.** The client only enables developer mode when
`runelite.launcher.version` is unset, and `RuneLite.app` always sets it:

```java
developerMode = options.has("developer-mode") && RuneLiteProperties.getLauncherVersion() == null;
```

So `run-dev.sh` starts `net.runelite.client.RuneLite` directly, off the jars the launcher
has already downloaded to `~/.runelite/repository2`.

**Side-loading does not work for this plugin.** Dropping a jar in
`~/.runelite/sideloaded-plugins` does get it loaded, but every `@Subscribe` method then
fails to register:

```
LambdaConversionException: Invalid caller: MoneyMakingGuidePlugin
```

`ReflectUtil.privateLookupIn` only returns a full-privilege lookup for classloaders
implementing `PrivateLookupableClassLoader`. `PluginHubClassLoader` does;
`PluginClassLoader` — the one used for side-loading — does not. Since each classloader
owns a separate unnamed module, the lookup has to teleport across modules, loses MODULE
access, and `LambdaMetafactory` rejects it.

`run-dev.sh` therefore uses `./gradlew devJar`, which rebuilds the plugin under
`net.runelite.client.plugins.moneymakingguide` and puts it on the client's own
classpath, where the core plugin scan finds it and the lookup has full privilege. The
Plugin Hub artifact from `./gradlew jar` keeps the normal package and is unaffected.

To install it permanently the way Quest Helper is installed, it has to go through the
[Plugin Hub](https://github.com/runelite/plugin-hub) — that is the only route to a
`PluginHubClassLoader`.

To regenerate the dataset by hand:

```sh
python3 tools/build_dataset.py mmg-data.json
cp mmg-data.json src/main/resources/com/moneymakingguide/mmg-data.json
```

## Layout

```
tools/build_dataset.py                  wiki Bucket API -> mmg-data.json
src/main/java/com/moneymakingguide/
  MoneyMakingGuidePlugin.java           lifecycle, events, player state snapshots
  MoneyMakingGuideConfig.java           settings
  data/                                 Gson models for the dataset
  service/DatasetService.java           fetch + cache + bundled fallback
  service/PriceService.java             real-time GE prices
  service/ProfitCalculator.java         GE tax, per-kill scaling
  service/RequirementService.java       the eligibility filter
  service/HiscoreService.java           levels for when you are not logged in
  ui/                                   the side panel
```

Player state is snapshotted on the client thread and handed to Swing as an immutable
`PlayerState`, so the panel never reads live client state off-thread. Game events set a
dirty flag drained once per tick, so a burst of `StatChanged` events costs one rebuild.

## Not done yet

- **Buy limits are not enforced.** A method needing 10k pure essence per 4 hours is
  capped by the limit long before it is capped by your cash. The data is there; the check
  is not.
- **Statically valued lines are snapshots.** A handful of outputs (drop-table expected
  values, spell costs) are baked in by the wiki at page-render time rather than priced
  live. They are used as-is.
- **Ironman mode** is not modelled — the panel will happily suggest methods that require
  buying inputs. (The hiscores lookup does support ironman tables; the *filtering* does
  not know what an ironman cannot buy.)
- **Recurring methods** (farm runs, birdhouses) are off by default. Their profit is per
  run, not per hour, so they sort misleadingly against hourly methods.

## Licence

BSD 2-Clause. Guide data is from the OSRS Wiki, [CC BY-NC-SA
3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/).
