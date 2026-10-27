# Industry

AFL measures power in FE (Forge Energy). Generators turn diesel, coal or lava into power, distribution panels hand it out to the lights and outlets in a building, and machines use it to process ores and materials

<StatusTag s="creative" /> Machines, electrical parts and timber have no crafting recipes yet and are creative only; ores don't generate in the world yet

## Pages

| Page | Covers |
|---|---|
| [Generators](./generators) | Diesel generator, portable generator, thermal generator |
| [Building Power](./power) | Distribution panel, hidden wiring, outlets and cords, lights |
| [Materials and Processing](./materials) | Ores, processing chains, machine power use, timber |

## Workbench

<StatusTag s="test" /> <ItemIcon id="wooden_workbench" /> A wooden workbench 2 blocks long, 1 deep and 2 high: a vise on the left, two drawers under the top on the right, a tool board at the back and a shelf on top

- Right click a drawer front and the drawer slides out; right click it again to push it in. With a drawer out, right click inside it to open it. Each drawer holds <V j="WorkbenchBlockEntity.DRAWER_SLOTS" /> slots of its own
- The tool board has <V j="Workbench.PEGS" /> pairs of pegs, one tool to a pair: right click a peg pair with a tool to hang it, with an empty hand to take it down, as on the wooden tool rack. The felling axe is too long to hang there
- <StatusTag s="test" /> The crowbar is too long to hang free on the bench: its hook rides one peg and its chisel end stands on the top, leaning
- Hung tools are drawn into the chunk with the bench, not redrawn every frame
- Breaking the bench drops what is in the drawers and on the pegs; an axe breaks it quickest
- The drawers use the wooden cabinet sounds for now
- For now it comes only from the creative inventory
- <StatusTag s="test" /> Crafting screen: right click the top of the bench. Two tabs at the top, Inventory and Crafting; the inventory key switches between them. Learned recipes are listed by category on the left, the item and its description in the middle, materials, tool, quantity and the craft button on the right (click once and wait for the ring to fill; click again to stop). F adds a recipe to the to-do list, which shows the missing materials at the top right of the screen
- <StatusTag s="test" /> Recipes live in data files, one folder per category, each with its materials, time and how it is learned. The crafting page lists only the recipes your current survivor knows; learned recipes stay with that survivor, and a new survivor learns them again
- <StatusTag s="test" /> There is one test recipe for now: 1 white oak log, with a <ItemIcon id="hand_saw" /> hand saw on you, makes <ItemIcon id="white_oak_beam" /> 1 white oak beam, under Building. To test the book, you now learn it by reading the bow drill section of the <ItemIcon id="field_survival_guide" /> [Field Survival Guide](../arms/equipment#field-survival-guide) (in survival)
- <StatusTag s="test" /> The beam is made on the top of the bench: stand at the front of the workbench and click Craft once; the screen closes, you step up to the bench and rip the log on its top along the grain with the hand saw, the kerf creeping in and sawdust falling. Above the hotbar: the item, which one of how many, and the progress. Each one goes straight onto you, and a larger quantity is sawn one after another. Moving, jumping, a left click or opening any screen stops it. Too far away or behind the bench, you are told to stand at its front
- <StatusTag s="test" /> The saw sounds on each push and pull, in step with your hands, and fades out at <V j="WorkbenchCrafting.SAW_RADIUS" /> blocks. The infected within that range hear it too
- <StatusTag s="test" /> What the saw takes out comes off as little pieces of wood: dust drifting down from the kerf, chips and shavings flying off in arcs, settling flat on the top or the floor and fading after a while. They take the colour of the inside of that species' wood
- <StatusTag s="test" /> The <ItemIcon id="wooden_knob" /> wooden knob needs both the hand saw and the <ItemIcon id="folding_knife" /> folding knife on you. A bench hook with a strip on it goes on the right end of the bench: you saw off the end, then whittle off the corners, round it and cut the neck and the tenon with the knife, turn it before your eyes, blow the chips off and set it down; one round of 10 seconds makes 6. The infected within <V j="WorkbenchCrafting.WHITTLE_RADIUS" /> blocks hear the whittling
- Materials are taken only from what you carry for now, not from the drawers; too far from the bench, no tool or too few materials and it will not craft, with the reason at the bottom of the screen
- Put the beams on a house with the [building hammer](../arms/equipment#building-hammer)
- The other categories are still greyed out; recipes not yet learned are never shown
- <StatusTag s="planned" /> Recipes: learned from life experience, trial and error, books and blueprints; books will turn up in towns. Crafting will take materials from the bench's drawers, from what you carry and from nearby chests

### Wooden furniture materials

<StatusTag s="test" /> The materials for the first wooden furniture are in, all in the creative Materials tab for now. Furniture is made at the workbench step by step, see Making furniture below.

| Item | Where it comes from | Mass |
|---|---|---|
| <ItemIcon id="white_oak_board" /> Boards (6 species: white oak, white pine, red oak, shagbark hickory, red maple, eastern hemlock) | Sawn from logs at the workbench | White oak <V d="item_mass/core_v1#items.apocalypse_firstlight:white_oak_board.unit_mass_kg" u="kg" /> |
| <ItemIcon id="wood_finish_can" /> Walnut Wood Finish | Found in towns | <V d="item_mass/core_v1#items.apocalypse_firstlight:wood_finish_can.unit_mass_kg" u="kg" /> |
| <ItemIcon id="nail_box" /> Box of Nails | Found in towns | <V d="item_mass/core_v1#items.apocalypse_firstlight:nail_box.unit_mass_kg" u="kg" /> |
| Box of Wood Screws (100) | Found in towns, not in hardware boxes | <V d="item_mass/core_v1#items.apocalypse_firstlight:screw_box.unit_mass_kg" u="kg" /> |
| <ItemIcon id="cabinet_hinge" /> Hinge | Found in towns; hardware box | <V d="item_mass/core_v1#items.apocalypse_firstlight:cabinet_hinge.unit_mass_kg" u="kg" /> |
| <ItemIcon id="iron_knob" /> Iron Knob | Found in towns; hardware box | <V d="item_mass/core_v1#items.apocalypse_firstlight:iron_knob.unit_mass_kg" u="kg" /> |
| <ItemIcon id="wooden_knob" /> Wooden Knob | Made from a board at the workbench, with the hand saw and the folding knife | <V d="item_mass/core_v1#items.apocalypse_firstlight:wooden_knob.unit_mass_kg" u="kg" /> |
| <ItemIcon id="keyhole_escutcheon" /> Keyhole Escutcheon | Found in towns; hardware box | <V d="item_mass/core_v1#items.apocalypse_firstlight:keyhole_escutcheon.unit_mass_kg" u="kg" /> |
| <ItemIcon id="furniture_hardware_box" /> Furniture Hardware Box | Found in towns | <V d="item_mass/core_v1#items.apocalypse_firstlight:furniture_hardware_box.unit_mass_kg" u="kg" /> |

- <StatusTag s="test" /> A board is a long 1.2 m plank, not the planks block. Rip logs into boards with the hand saw at the workbench: <V d="workbench_recipes/woodwork/white_oak_boards#materials.0.count" /> log of a species gives <V d="workbench_recipes/woodwork/white_oak_boards#result.count" /> boards of it, <V d="workbench_recipes/woodwork/white_oak_boards#seconds" u="s" /> each, sawn on the bench top like the beam, with the log you are sawing lying on the bench. Forest residents know it from the start
- <StatusTag s="test" /> Wooden knob: <V d="workbench_recipes/woodwork/wooden_knob#materials.0.count" /> board of any species and the hand saw make <V d="workbench_recipes/woodwork/wooden_knob#result.count" />, in <V d="workbench_recipes/woodwork/wooden_knob#seconds" u="s" />, with only the progress bar for now. Furniture takes an iron or a wooden knob alike, and looks the same either way
- Hinges and keyhole escutcheons cannot be made, only found; later they will also come from taking old furniture apart
- A can of finish holds 1 litre and a box of nails 180; what is used comes off, the rest shows in the tooltip, with a bar under the slot once some is gone. Neither stacks
- <StatusTag s="test" /> Furniture hardware box: hold it and press use to open it; it gives <V d="loot_tables/gameplay/furniture_hardware_box#pools.0.rolls.min" /> to <V d="loot_tables/gameplay/furniture_hardware_box#pools.0.rolls.max" /> hinges, iron knobs and keyhole escutcheons at random, and the box is gone. It opens with a vanilla sound for now
- <StatusTag s="planned" /> Taking old furniture apart

### Making furniture

<StatusTag s="test" /> The 7 wooden pieces (chair, table, tool rack, gun rack, nightstand, storage chest, wardrobe) are made at the workbench with the hand saw and the claw hammer on you; the nightstand, the storage chest and the wardrobe also need a flat screwdriver (<V d="item_mass/core_v1#items.apocalypse_firstlight:screwdriver.unit_mass_kg" u="kg" />). Press Make and you step up to the bench; a piece is made in steps:

- **Crosscut:** slide the board over the end of the bench, saw a length off and lay it at the back right, 6.5 s a cut
- **Nailing:** set the nail with a light tap, then a ring comes up around the crosshair: a needle goes round clockwise from 12 o'clock, 1.2 s a turn, and the hammer rises with it. Press Space while the needle is in the green arc to strike (rebindable in Controls); the brighter middle of the arc counts as two blows. A miss only costs time, nothing breaks. You drive as many nails as you can see on the bench; the box gives up the real count
- **Staining:** before the first coat the can is pried open and stirred; a flat brush is dipped and drawn along the grain in three strokes, and the piece turns walnut band by band
- **Hardware:** set the hinge, knob or escutcheon in place, then drive its screws one by one on the same ring; a slip only costs time

- A novice strikes a nail 4 times and turns a screw 4 times; carpentry level 4 makes it 3, level 7 makes it 2, and each level widens the green and quickens the work. Each nail, each screw and each finished piece give carpentry experience
- While working, right click does nothing, and at the furniture Space does not jump
- Moving on to the next action (the saw put down for the hammer, the next nail), you move smoothly to the new spot and the view follows
- Stopping halfway (Shift, left click, walking off, opening a screen) takes no materials; what is done stays on the bench, saved with the world. Anyone who knows the recipe can go on, from the start of that nail or screw. With another recipe's piece on the bench, the page needs a second click to throw it away
- The materials come off when a piece is done, and the can is closed before you stop. Furniture is made one piece at a time
- The finished piece stays on the bench: standing if it fits, laid down if not (the table legs up; the wardrobe, the chair and the racks on their backs). While it is there no new piece can be started; press use on the top to take it into your inventory. Carrying it in both arms comes later

## Machines

| Machine | Storage | Power while working |
|---|---|---|
| <ItemIcon id="crusher" /> Crusher | <V d="machine_balance/crusher#capacity_fe" u="FE" /> | <V d="machine_balance/crusher#work_fe_per_tick" u="FE/t" /> |
| <ItemIcon id="industrial_furnace" /> Industrial furnace (3 lanes) | <V d="machine_balance/industrial_furnace#capacity_fe" u="FE" /> | <V d="machine_balance/industrial_furnace#work_fe_per_tick_per_lane" u="FE/t" /> per lane |
| <ItemIcon id="alloy_furnace" /> Alloy furnace | <V d="machine_balance/alloy_furnace#capacity_fe" u="FE" /> | <V d="machine_balance/alloy_furnace#work_fe_per_tick" u="FE/t" /> |
| <ItemIcon id="chemical_reactor" /> Chemical reactor | <V d="machine_balance/chemical_reactor#capacity_fe" u="FE" /> | <V d="machine_balance/chemical_reactor#work_fe_per_tick" u="FE/t" /> |
| <ItemIcon id="compressor" /> Compressor | <V d="machine_balance/compressor#capacity_fe" u="FE" /> | <V d="machine_balance/compressor#work_fe_per_tick" u="FE/t" /> (no recipes yet) |
| <ItemIcon id="intake_pump" /> Intake pump | <V d="machine_balance/intake_pump#capacity_fe" u="FE" /> | <V d="machine_balance/intake_pump#work_fe_per_tick" u="FE/t" />, <V d="machine_balance/intake_pump#pump_mb_per_tick" u="L" /> per tick |
| <ItemIcon id="charging_station" /> Charging station | <V d="machine_balance/charging_station#capacity_fe" u="FE" /> | Charges at <V d="machine_balance/charging_station#charge_fe_per_tick" u="FE/t" /> |
| <ItemIcon id="energy_battery" /> Energy battery | <V d="machine_balance/energy_battery#capacity_fe" u="FE" /> | In / out <V d="machine_balance/energy_battery#max_extract_fe_per_tick" u="FE/t" /> |
| <ItemIcon id="energy_cell" /> Energy cell | <V d="machine_balance/energy_cell#capacity_fe" u="FE" /> | In / out <V d="machine_balance/energy_cell#max_extract_fe_per_tick" u="FE/t" /> |

<ItemIcon id="precision_fabrication_station" />The Precision Fabrication Station is decorative for now
