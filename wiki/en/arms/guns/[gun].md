---
outline: [2, 2]
---

<GunHead :id="$params.gun" />

<!-- @content -->

## Compared with other guns

<GunStats :id="$params.gun" part="compare" />

## Full stats

<GunStats :id="$params.gun" part="table" />

## Damage falloff

<GunStats :id="$params.gun" part="falloff" />

## Attachments

<GunStats :id="$params.gun" part="attachments" />

## How to get it

<StatusTag s="creative" /> There is nowhere to find guns in survival yet: no recipes and no loot tables place them. A new gun comes with a full magazine.

## Value history

<RecentChanges :gun="$params.gun" />
