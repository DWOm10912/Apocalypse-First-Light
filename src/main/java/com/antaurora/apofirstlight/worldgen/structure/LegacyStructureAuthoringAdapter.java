package com.antaurora.apofirstlight.worldgen.structure;

import java.util.*;
import com.google.gson.JsonObject;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import static com.antaurora.apofirstlight.worldgen.structure.StructureValidationIssue.*;
import static com.antaurora.apofirstlight.worldgen.structure.StructureDefinitionLoader.*;

/**
 * Explicit, offline City authoring-v1 mapping. Does not call City/Rural or export resources.
 * Old "road/surface plane" wording does not prove WG-01 first-cell-above-ground semantics:
 * confirmation must be supplied from an asset audit, never inferred from offset=1.
 * Category and revision are explicit caller decisions, not guessed from City building classes.
 */
public final class LegacyStructureAuthoringAdapter {
    private LegacyStructureAuthoringAdapter() {}
    public enum OffsetSemantics { UNCONFIRMED, CONFIRMED_FIRST_CELL_ABOVE_GROUND }
    public record Mapping(Optional<StructureDefinition> definition, StructureValidationResult validation,
                          Map<String,String> fieldDisposition) {
        public Mapping {
            Objects.requireNonNull(definition); Objects.requireNonNull(validation);
            fieldDisposition=Map.copyOf(fieldDisposition);
            if (definition.isPresent()!=validation.valid()) throw new IllegalArgumentException("Invalid mapping publication");
        }
    }
    public static Mapping map(ResourceLocation metadataId, String legacyJson, ResourceLocation category,
                              String revision, OffsetSemantics offsetSemantics, StructureNbtReader.Inspection nbt) {
        Objects.requireNonNull(metadataId); Objects.requireNonNull(category); Objects.requireNonNull(revision);
        Objects.requireNonNull(offsetSemantics); Objects.requireNonNull(nbt);
        var disposition=new LinkedHashMap<String,String>();
        var issues=new ArrayList<StructureValidationIssue>();
        try {
            JsonObject j=object(parseJson(legacyJson),"$");
            if(integer(j,"authoring_version")!=1) throw new Invalid(Code.UNSUPPORTED_SCHEMA,"authoring_version","Only legacy authoring v1");
            var id=identifier(string(j,"structure"),"structure");
            disposition.put("structure","MAPPED_TO_STRUCTURE_NBT");
            disposition.put("id","NEW_METADATA_ID_SUPPLIED_EXPLICITLY");
            disposition.put("category","NEW_CATEGORY_SUPPLIED_EXPLICITLY");
            for (String key : j.keySet()) {
                if(key.equals("city_zones")) disposition.put(key,"BELONGS_TO_CITY_POLICY");
                else if(Set.of("weight","minCount","maxCount","min_count","max_count","unique").contains(key))
                    disposition.put(key,"BELONGS_TO_SETTLEMENT_RECIPE");
                else if(Set.of("spacing","separation","salt","biomes","terrainThreshold").contains(key))
                    disposition.put(key,"BELONGS_TO_PLACEMENT_POLICY");
                else if(!Set.of("id","structure","category","footprint","height","front","surface_offset_y",
                        "allowed_rotations","road_facing","authoring_version").contains(key))
                    disposition.put(key,"NOT_MAPPED");
            }
            var footprint=object(required(j,"footprint"),"footprint");
            int width=integer(footprint,"width"), depth=integer(footprint,"depth"), height=integer(j,"height");
            disposition.put("footprint","ASSERTION_ONLY_NBT_SIZE_IS_AUTHORITY");
            disposition.put("height","ASSERTION_ONLY_NBT_SIZE_IS_AUTHORITY");
            if(nbt.info().isPresent()) {
                var b=nbt.info().orElseThrow().localBounds();
                if(width!=b.width()||height!=b.height()||depth!=b.depth())
                    issues.add(error(Code.LEGACY_DIMENSION_MISMATCH,"footprint/height","Legacy="+width+"x"+height+"x"+depth+" NBT="+b.width()+"x"+b.height()+"x"+b.depth()));
                else disposition.put("footprint/height","MATCHES_DERIVED_NBT_BOUNDS");
            }
            int offset=integer(j,"surface_offset_y");
            if(offsetSemantics==OffsetSemantics.UNCONFIRMED) {
                disposition.put("surface_offset_y","NOT_MAPPED_PENDING_SEMANTIC_CONFIRMATION");
                issues.add(error(Code.LEGACY_OFFSET_REQUIRES_CONFIRMATION,"surface_offset_y","Confirm first cell ABOVE ground, not road block Y"));
            } else disposition.put("surface_offset_y","MAPPED_TO_GROUND_ANCHOR_OFFSET_Y_EXPLICITLY_CONFIRMED");
            disposition.put("front","MAPPED_TO_FRONT_NOT_SOCKET");
            disposition.put("allowed_rotations","MAPPED_DECLARATION_NOT_GAME_QA");
            disposition.put("road_facing","NOT_MAPPED_TO_SOCKET");
            if(bool(j,"road_facing"))
                issues.add(warning(Code.MISSING_REQUIRED_SOCKET_INFORMATION,"road_facing","No entrance coordinates; no socket generated"));
            var definition=new StructureDefinition(metadataId,id,category,enumeration(Direction.class,string(j,"front"),"front"),
                    offset,rotations(j),List.of(),Set.of(),revision,1);
            issues.addAll(StructureDefinitionValidator.validate(definition,nbt).issues());
            var report=new StructureValidationResult(issues);
            return new Mapping(report.valid()?Optional.of(definition):Optional.empty(),report,disposition);
        } catch(Invalid invalid) {
            issues.add(invalid.issue());
            return new Mapping(Optional.empty(),new StructureValidationResult(issues),disposition);
        }
    }
}
