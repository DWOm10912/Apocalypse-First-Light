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
- Hung tools are drawn into the chunk with the bench, not redrawn every frame
- Breaking the bench drops what is in the drawers and on the pegs; an axe breaks it quickest
- The drawers use the wooden cabinet sounds for now
- For now it comes only from the creative inventory
- <StatusTag s="planned" /> Crafting: the screen, recipes and crafting times are still being designed

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
