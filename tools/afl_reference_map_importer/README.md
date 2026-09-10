# AFL Reference Map Importer V1

Python 3.10+, standard library only. Run from the project root:

```powershell
python -m tools.afl_reference_map_importer scan 'E:\Download\Modern Office Building V.zip' --radius 128 --y-min 0 --y-max 160
python -m tools.afl_reference_map_importer extract 'E:\Download\Modern Office Building V.zip' --bounds -23 3 3 15 118 43 --reference-id modern_office_building_v_reference
python -m tools.afl_reference_map_importer audit build/reference_imports/modern_office_building_v_reference/reference_structure.json --registry run/afl_authoring_bridge/target_registry_1_20_1.json --mapping tools/afl_reference_map_importer/mappings/mc_1_21_4_to_1_20_1.json --output build/reference_imports/modern_office_building_v_reference/audit-new.json
python -m tools.afl_reference_map_importer prepare build/reference_imports/modern_office_building_v_reference/reference_structure.json --registry run/afl_authoring_bridge/target_registry_1_20_1.json --mapping tools/afl_reference_map_importer/mappings/mc_1_21_4_to_1_20_1.json --output build/reference_imports/modern_office_building_v_reference/prepared-new.json
python -m unittest discover -s tools/afl_reference_map_importer/tests -v
```

Do not rerun extraction under an existing ID: outputs deliberately refuse overwrite. ZIP is unpacked into a unique data-only staging folder; folder input is read-only. Scan never selects or pastes automatically. `--bounds` is inclusive absolute X1 Y1 Z1 X2 Y2 Z2. Ground policy describes the reviewed bounds, not an automatic dirt/stone deletion algorithm. Include desired padding in the explicit bounds.

Schematic generation uses the actual WorldEdit serializer through `reference_write_schematic` after staging the prepared JSON; there is no guessed offline Sponge writer. Full details and current test boundaries: [reference_map_importer_v1.md](../../docs/dev/reference_map_importer_v1.md).
