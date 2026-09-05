# Settlement Builders

Settlement Builders is a Necesse mod that adds a dedicated **Builder settler**, reusable **construction blueprints**, persistent construction projects, and optional hardcore building damage/weathering rules.

The goal is to make large-scale settlement construction practical without turning it into instant automation: the player still plans buildings, supplies materials, keeps Builders happy, and deals with damaged or neglected structures, while Builders handle the repetitive physical work.

## Main Features

- Dedicated **Builder** settlers with a Construction job category.
- Reusable **Blueprint** items that can copy a structure and place persistent construction projects.
- Multiple Builders can cooperate on the same blueprint.
- Builders clear incorrect objects/tiles, fetch materials from settlement storage and build them one by one.
- Construction speed scales strongly with Builder happiness.
- Missing materials and other blocked states are shown directly on the blueprint project.
- **Blueprint Workstation** for renaming, editing, copying, and sharing blueprints.
- **Project Eraser** for cancelling placed blueprint projects.
- Optional (enabled by default) **Hardcore Damage** mode where normal passive damage recovery is disabled,\
Builders must perform repairs, and rain causes weathering damage over time to placed objects.
- This mod is required on both the client and server.

---

# Builders

Builders are a custom settler type. Their special work category is **Construction**.

Construction covers both blueprint construction and repair work.
Normal settlement job priorities still apply, so the player can raise
or lower Construction priority just like other settler jobs.

Builders use their own small work inventory while gathering construction materials.
Material carrying is deliberately limited so large projects still involve
trips to settlement storage for collection.

## Happiness and work speed

Builder action duration scales linearly based on happiness:

- **0 happiness:** about **5 seconds per construction action**.
- **100 happiness:** about **0.5 seconds per construction action**.

This makes happiness especially important for a small skilled workforce,\
while still allowing a low-happiness settlement to compensate with (~~euphemism~~) more Builders.

---

# Blueprints

A Blueprint is a reusable item that stores a rectangular section of construction data.

A blueprint can contain:

- Tiles/floors.
- Objects.
- Walls and doors.
- Rotations.
- Multi-tile objects.
- Wires and logic gates.

The blueprint item itself is **not consumed** when a project is placed.

## Creating a blueprint

Blueprints can be crafted in a basic workstation with a **stack of paper** and a **quill and parchment**

Use an **empty Blueprint** to create a blueprint from an area in the world.
The captured blueprint stores the tile and object layout so it can later be placed elsewhere.

While creating a blueprint, floor tiles and/or objects can be excluded from the blueprint by ctrl+clicking
a particular cell. For example, if a blueprint area contains some walls, a desk and some stone floors,
and you don't want the stone floor to be part of the blueprint, ctrl+click one cell that contains a stone floor,
and all stone floors will be completely excluded from the blueprint. Note that if you want to only exclude the table,
and the table is over a stone floor, ctrl+clicking that cell will exclude both tables and stone floor types from your blueprint.
To be more precise in what you want to exclude, you can edit your blueprint after creation using a Blueprint Workstation. 

Blueprints can be reused any number of times, or cleared and recreated from scratch.

Right-clicking an empty blueprint will enter creation mode.

Right-clicking a filled blueprint will clear it.

Left-clicking a filled blueprint will place it in the world as a blueprint project, if conditions allow.
This project will create a construction job for the settlement's Builders.

## Rotating a blueprint

While holding a filled Blueprint:

- **Page Up** rotates it counter-clockwise.
- **Page Down** rotates it clockwise.

The placement preview updates to show the rotated version.

## Placing a blueprint project

Using a filled Blueprint places a persistent blueprint project at the selected position.

There are some rules for placement, as follows:

- Project must be entirely contained inside a settlement you belong to,
not overlapping other projects, and with no invalid placements.
- Project can't be placed over an object that cannot safely be removed/replaced by construction (like a dungeon entrance).
- Example of an invalid placement: A blueprint that has one particular cell containing
a wall and no floor tile can't be placed in a location where that cell will be
over a water tile, because that would create an invalid placement
(walls can't be put over water directly), but if that cell contains a floor and a wall, it is allowed,
since the builders will be instructed to place a floor before placing the wall.

The game displays a translucent preview of the intended structure while the project exists.
Builders work from tiles around the outside of the blueprint, so leave at least a few empty spaces around the blueprint
so builders may reach them.

---

# How Construction Works

## 1. Materials

Construction will only start when all required materials can be found in settlement storage.

## 2. Clearing

Builders first remove any ground tiles and objects that are not in the blueprint.

Ground tiles will only be removed if they conflict with blueprint ground tiles, for example,
if a stone floor has to be placed where a wood floor is currently, the wood floor will be removed.

Likewise, if an object is already placed correctly to the corresponding blueprint, it will not be removed.

Items dropped by clearing are moved outside the construction area so
they do not obstruct construction and can still be collected.

## 3. Building

After clearing is complete, Builders will place all tiles/floors, objects, wires and logic gates in that order.

## 4. Completion and cleanup

When the project is complete, Builders return any remaining
construction materials they are still carrying to settlement storage.

---

# Blocked Blueprint Projects

A blueprint project can become blocked when construction cannot
currently continue, for example if required materials run out.
An error message will be displayed so the problem can be fixed.

A blocked project is shown with a **red blueprint-area background** instead of the normal blue project color.

When the condition is corrected, the project can resume automatically; the player does not normally need to replace the blueprint.

---

# Blueprint Workstation

The **Blueprint Workstation** can be crafted in a basic workstation with **15 logs**, **3 tungsten bars** and a **stack of paper**

The Blueprint Workstation is used to edit Blueprint items.

It has a single slot that accepts a Blueprint.

From the workstation the player can:

- Rename a blueprint.
- Inspect the different tile/object types contained in it.
- Remove a selected tile or object type from the entire blueprint
(not from a specific cell, all the objects of the same type will be deleted).
- Copy the blueprint data as JSON to the system clipboard.
- Paste compatible blueprint JSON from the clipboard. This provides a simple way to share blueprints between players or keep blueprint definitions outside the game.

Please note that all actions are irreversible. Removed components cannot be restored, and pasting a blueprint will replace it forever.

---

# Project Eraser

The **Project Eraser** can be crafted in a basic workstation with a **quill and parchment** and an **iron bar**

The Project Eraser cancels an already placed blueprint project.

Cancelling a project removes the blueprint project and stops its associated construction jobs, but it does **not** undo construction that has already happened.

---

# Recruiting Builders

To recruit builders, you must first craft a **Builder Job Request Bulletin**, which requires
a **stack of paper** and a **quill and parchment** in a basic workstation.

After placing a bulletin on a settlement wall, you will start getting Builder visitors
periodically (every 5 to 10 minutes) until the bulletin is removed.

Effects don't stack, placing extra bulletins do not increase visitor rate.

The cost of hiring a Builder is determined by the number of Builders you have in your current employment.
For every 3 Builders you hire, the cost of recruiting subsequent Builders will increase, in terms of the items requested.

Likewise, if you fire enough builders the hiring cost will go down again.

The requested items are always rocks and metal bars, with the specific materials changing according to biome progression.
\(stone -> swamp stone -> sandstone -> etc )
\(iron,copper,gold -> demonic -> ivy -> etc )

---

# Hardcore Damage

The **Settlement Builders Mod™** includes an optional setting called **Hardcore Damage**.

This setting is enabled by default. To disable it, run the game with the mod installed once,
and the configuration file will be automatically created in the directory: **%APPDATA%\Necesse\cfg\mods**
(or wherever the game is saving its configuration settings in your particular machine)

**Hardcore Damage** features:

- Damaged tiles and objects (by the player, explosions, etc) no longer recover naturally over time.
- Damage remains until repaired, and players cannot do repairs, save for breaking it and replacing it.
- Builders create and perform repair jobs using the Construction work category.

## Repair jobs

When a tile or object takes damage, the mod waits approximately **10 seconds after the most recent damage** before creating a repair job.

This is a delay period. If the object is damaged again during that period, the countdown effectively restarts.
This exists so players can break blocks as normal without spam-triggering repair jobs as they damage objects. If a player starts
breaking a wall for example, and changes their mind and leaves the block half broken, after 10 seconds that block will become
eligible for repairs.

A Builder then travels to the damaged location and fixes the damaged objects.
The time to repair a single block is the same as the blueprint block placing time, scaling with happiness. 

---

# Object weathering

With Hardcore Damage enabled, many player-placed (or Builder-placed)
objects will slowly deteriorate when exposed to rain.

Naturally generated world structures will not deteriorate.

## Exposure

Normal objects accumulate weathering only while they are exposed to the outdoors during rain.

Being "exposed" is based on Necesse's outside/inside state.
Meaning objects inside a properly built house will not deteriorate.

Weather damage is invisible, and only causes real visible damage after a set time.

### Walls and doors

Walls and doors will deteriorate 3 times slower than other objects. Furthermore,
walls that are completely contained inside will never deteriorate.

A wall/door is considered externally exposed when at least one neighboring tile
(considering only 4 cardinal directions, not diagonals) is outdoors.
In other words, outside perimeters of buildings will deteriorate with time. 

Weather damage happens at once, after an average period of **~20 minutes** (60 min for walls/doors) of rain exposure.

Intermittent rain will accumulate weathering exposure. Weathering pauses when the rain stops.
So, if it rains 20 times for one minute over a long period,
the damage event will occur only after those 20 minutes of total rain exposure have accumulated.

Sleeping won't skip weather damage. If it rains all night, the result will be the same whether the player sleeps or not.

When the weathering threshold is reached for a block, that block will take massive damage (60% of total health).
At that moment jobs will be created for repairs.

If no repairs are done, the blocks will continue to take weather damage, same as before, every rainfall.
After a second cycle of weathering (same time period), the second damage trigger will destroy the block.
Blocks will not be obliterated, they will be dropped as items.

Exposed Chests can and will be damaged as well, and any items within will spill on the ground if it breaks.

## Repairing weathered objects

Builder repair clears the object's normal damage and also resets its accumulated weathering progress.

This means maintaining a building genuinely renews it rather than immediately leaving it on the edge of another weathering event.

Note that builders cannot work on repairs until the first damage cycle is complete.
Partial weather damage is invisible after all.

## Material resistance tiers

Every material has an inherent resistance to weathering, grouped in tiers.

Every tier has a resistance based on tier 0 (wood). So tier 1 lasts twice as long as tier 0, tier 2 lasts 3 times as long, and so on.

Finally, the last tier is completely immune to weather damage.

Below is a non-exhaustive list of tiers and some examples of materials included in that group. 

| Tier | Average Weathering Time |
| --- | --- |
| **Tier 0 — Wood**<br><br>Wooden furniture, floors, paths, walls, doors, fences, columns | **Objects:** ~20 min<br>**Walls/Doors:** ~60 min |
| **Tier 1 — Surface Masonry**<br><br>Stone, sandstone, swampstone, snowstone, granite, brick | **Objects:** ~40 min<br>**Walls/Doors:** ~120 min |
| **Tier 2 — Reinforced**<br><br>Dungeon stone, deepstone, ice, obsidian, iron fences | **Objects:** ~60 min<br>**Walls/Doors:** ~180 min |
| **Tier 3 — Advanced Biome Stone**<br><br>Deep snowstone, basalt, deep swampstone, deep sandstone | **Objects:** ~80 min<br>**Walls/Doors:** ~240 min |
| **Tier 4 — Late-Game Materials**<br><br>Spider Castle, dawn/dusk, ancient ruins, raven, arcanic, factory | **Objects:** Weatherproof<br>**Walls/Doors:** Weatherproof |

<br><br>

---

# Fence Batch Repairs

Large fence networks can contain hundreds or thousands of individual segments,
so Builder will not fix them one by one. They will instead repair fences lines.

Every time a Builder fixes a fence (fence gates included) the closest 20 damaged fences
connected to it will be repaired as well.

Fences across a gap will not be considered connected.

Intact fences will be traversed, but not count as repaired. So in the case below (D for Damaged, i for intact):
DDiDiDDiDDDiiDDDiDiDDiDDiDiiiiiDDD: Fixing a fence on one end will fix all of them,
because there are 20 damaged connected by intact fences. 

However, intact fences in that repaired line will not have their weather damage reset.

Fence gates are treated as part of the same connected fence network, whether open or closed.

This makes repairing large perimeter fences a bit more manageable.

---

# Wilderness Constructions

Blocks created in world generation are immune to weather damage,
but blocks placed by players will suffer weather damage the same as blocks in a settlement.

A player might construct a temporary shack to spend the night, and it will with time break appart.

If a player wants to build anything permanent off settlement, be it a road or a building or anything else,
he needs to hire a Builder.

Builders that join a player in an adventure party have a new dialogue option to do improvements on the road.
Turning that option on will have Builders look for blocks around
a 6 tile radius around them for player-placed weatherable blocks. If they find any
they will use their skills to reinforce those blocks so they become immune to weather damage.

This doesn't apply to blocks inside a settlement. Also new settlements (or settlements that grow into an area)
that contains previously reinforced blocks will convert those blocks into weatherable again.

A way to reinforce blocks in settlements will be added in a future release.

---

# Inspection Glass

The **Inspection Glass** is an item that can be crafted in a basic workstation with **two glass** and an **iron bar**.

While holding and selecting this item in the toolbar any player-placed weatherable objects will show an icon
that displays the current reinforcement level, from one to four, four being immune to weather damage.

Wilderness reinforcements are equivalent to a level 4 reinforcement level. In the current version of the mod,
settlement blocks cannot be reinforced. When this feature is released, each reinforcement level will act
as an added tier to the blocks base material tier. For example, reinforcing basalt blocks by one level
will make them immune to weather damage.

Right-clicking with the glass in hand will toggle the visibility of project previews on the world.
The previews are visible by default and don't require an Inspection Glass to see.

---

# Design Notes

Settlement Builders is not supposed to be a creative-mode construction system.
It intentionally tries to preserve Necesse's resource and settlement gameplay features.

- Blueprints do not create free materials.
- Builders physically retrieve resources from settlement storage.
- Builder happiness has a large effect on throughput.
- Damage and weathering create ongoing maintenance work, but settlements of any size can be properly managed given
enough builders, and their work quality can be vastly improved by managing their happiness levels.