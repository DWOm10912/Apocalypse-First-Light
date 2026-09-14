package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.*;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import com.antaurora.apofirstlight.worldgen.profile.WorldgenProfile;

/**
 * Strict schema-1 in-memory NBT codec. No disk IO, SavedData loading, registration or dirty flags.
 * Missing/corrupt profile is an error; only an ABSENT world profile outside this codec is LEGACY.
 * Verification is intentionally NOT persisted: decoding always yields UNVERIFIED entries.
 * CompoundTag duplicate keys are already collapsed by NBT; duplicate entry/system IDs are retained
 * in LISTS and rejected here. Future file adapters must impose byte/decompression quotas as well.
 */
public final class ClaimIndexCodec {
    private ClaimIndexCodec() {}
    private static void putText(CompoundTag tag, String key, String value) {
        WorldgenProfile.requireText(value);
        if (value.length() > 4096) throw new IllegalArgumentException("Text exceeds codec limit: " + key);
        tag.putString(key, value);
    }
    public static CompoundTag encodeProfile(WorldgenProfile profile) {
        var tag = new CompoundTag(); tag.putInt("schema", profile.schemaVersion());
        putText(tag, "profile_id", profile.profileId()); putText(tag, "resource_snapshot", profile.resourceSnapshot());
        if (profile.systemVersions().size() > 128) throw new IllegalArgumentException("Too many systems");
        var systems = new ListTag();
        profile.systemVersions().forEach((id, version) -> {
            var entry = new CompoundTag(); putText(entry,"id",id.toString()); putText(entry,"version",version); systems.add(entry);
        });
        tag.put("systems",systems); return tag;
    }
    public static WorldgenProfile decodeProfile(CompoundTag tag) {
        fields(tag, Set.of("schema","profile_id","resource_snapshot","systems"));
        int schema = integer(tag,"schema");
        if (schema != 1) throw new IllegalArgumentException("Unsupported profile schema");
        var systems = new LinkedHashMap<ResourceLocation,String>();
        for (var value : list(tag,"systems",Tag.TAG_COMPOUND,128)) {
            var entry = (CompoundTag) value; fields(entry,Set.of("id","version"));
            if (systems.putIfAbsent(identifier(text(entry,"id")),text(entry,"version")) != null)
                throw new IllegalArgumentException("Duplicate profile system ID");
        }
        return new WorldgenProfile(text(tag,"profile_id"),schema,systems,text(tag,"resource_snapshot"));
    }
    public static CompoundTag encode(ClaimIndexSnapshot snapshot) {
        var root = new CompoundTag(); root.putInt("schema",1); root.putLong("world_seed",snapshot.worldSeed());
        root.putLong("index_revision",snapshot.indexRevision()); root.putInt("capacity",snapshot.capacity());
        root.put("profile",encodeProfile(snapshot.profile()));
        var entries = new ListTag();
        for (var value : snapshot.entries()) {
            var e = value.entry(); var c = e.claim(); var t = new CompoundTag();
            putText(t,"id",c.id()); putText(t,"owner",c.owner().toString());
            if (!c.dimension().registry().toString().equals("minecraft:dimension")) throw new IllegalArgumentException("Not a dimension key");
            putText(t,"dimension",c.dimension().location().toString()); putText(t,"generation_version",c.generationVersion());
            t.putIntArray("bounds_xz",new int[]{c.boundsXZ().minX(),c.boundsXZ().minZ(),c.boundsXZ().maxXExclusive(),c.boundsXZ().maxZExclusive()});
            c.yRange().ifPresent(y -> t.putIntArray("y_range",new int[]{y.minY(),y.maxYExclusive()}));
            putText(t,"type",c.type().name()); putText(t,"strength",c.strength().name()); t.putInt("priority",c.priority());
            t.putInt("margin",c.exclusionMargin()); var edges = new ListTag();
            if (c.connectionEdges().size() > 1024) throw new IllegalArgumentException("Too many edges");
            for (String edge : c.connectionEdges()) {
                if (edge.length()>4096) throw new IllegalArgumentException("Edge too long");
                edges.add(StringTag.valueOf(edge));
            }
            t.put("edges",edges); putText(t,"stage",e.stage().name()); putText(t,"plan_digest",e.planDigest());
            putText(t,"source_id",e.authoritativeSourceId()); t.putLong("revision",e.revision()); entries.add(t);
        }
        root.put("entries",entries); return root;
    }
    /** Public value-key factory; future callers need no world lookup to reconstruct dimension identity. */
    public static ClaimIndexSnapshot decode(CompoundTag root) {
        return decode(root, id -> ResourceKey.create(Registries.DIMENSION,id));
    }
    /** Explicit key factory permits headless tests without Minecraft registry bootstrap; it must do no IO. */
    public static ClaimIndexSnapshot decode(CompoundTag root, Function<ResourceLocation, ResourceKey<Level>> dimensionKeys) {
        Objects.requireNonNull(dimensionKeys);
        fields(root,Set.of("schema","world_seed","index_revision","capacity","profile","entries"));
        if (integer(root,"schema") != 1) throw new IllegalArgumentException("Unsupported claim index schema");
        var profile = decodeProfile(compound(root,"profile"));
        var entries = new ArrayList<ClaimIndexSnapshot.VerifiedEntry>(); var ids = new HashSet<String>();
        for (var value : list(root,"entries",Tag.TAG_COMPOUND,LimitedClaimIndex.MAX_ENTRIES)) {
            var t = (CompoundTag) value;
            fields(t,Set.of("id","owner","dimension","generation_version","bounds_xz","y_range","type","strength",
                    "priority","margin","edges","stage","plan_digest","source_id","revision"));
            String id = text(t,"id");
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate claim ID");
            int[] xz = array(t,"bounds_xz",4); Optional<YRange> y = Optional.empty();
            if (t.contains("y_range")) { int[] range = array(t,"y_range",2); y = Optional.of(new YRange(range[0],range[1])); }
            var edges = new ArrayList<String>();
            for (var edge : list(t,"edges",Tag.TAG_STRING,1024)) {
                String s = edge.getAsString(); if (s.length()>4096) throw new IllegalArgumentException("Edge too long"); edges.add(s);
            }
            var dimensionId = identifier(text(t,"dimension")); var dimension = Objects.requireNonNull(dimensionKeys.apply(dimensionId));
            if (!dimension.location().equals(dimensionId) || !dimension.registry().toString().equals("minecraft:dimension"))
                throw new IllegalArgumentException("Dimension key factory returned another key");
            var claim = new SpatialClaim(id,identifier(text(t,"owner")),dimension,text(t,"generation_version"),
                    new BoundsXZ(xz[0],xz[1],xz[2],xz[3]),y,SpatialClaimType.valueOf(text(t,"type")),
                    SpatialClaimStrength.valueOf(text(t,"strength")),integer(t,"priority"),integer(t,"margin"),edges);
            var entry = new ClaimIndexEntry(claim,ClaimStage.valueOf(text(t,"stage")),text(t,"plan_digest"),text(t,"source_id"),number(t,"revision"));
            entries.add(new ClaimIndexSnapshot.VerifiedEntry(entry,ClaimIndexVerification.UNVERIFIED));
        }
        return new ClaimIndexSnapshot(number(root,"world_seed"),profile,number(root,"index_revision"),integer(root,"capacity"),entries);
    }
    private static void fields(CompoundTag tag, Set<String> allowed) {
        for (String key : tag.getAllKeys()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown field: " + key);
    }
    private static void require(CompoundTag tag,String key,int type) {
        if (!tag.contains(key,type)) throw new IllegalArgumentException("Missing/wrong field type: " + key);
    }
    private static String text(CompoundTag tag,String key) {
        require(tag,key,Tag.TAG_STRING); String value=tag.getString(key); WorldgenProfile.requireText(value);
        if (value.length()>4096) throw new IllegalArgumentException("Text exceeds codec limit: " + key); return value;
    }
    private static int integer(CompoundTag tag,String key) { require(tag,key,Tag.TAG_INT); return tag.getInt(key); }
    private static long number(CompoundTag tag,String key) { require(tag,key,Tag.TAG_LONG); return tag.getLong(key); }
    private static CompoundTag compound(CompoundTag tag,String key) { require(tag,key,Tag.TAG_COMPOUND); return tag.getCompound(key); }
    private static int[] array(CompoundTag tag,String key,int length) {
        require(tag,key,Tag.TAG_INT_ARRAY); int[] value=tag.getIntArray(key);
        if(value.length!=length) throw new IllegalArgumentException("Wrong array size: "+key); return value;
    }
    private static ListTag list(CompoundTag tag,String key,int elementType,int limit) {
        require(tag,key,Tag.TAG_LIST); var list=(ListTag)tag.get(key);
        if ((!list.isEmpty() && list.getElementType()!=elementType) || list.size()>limit)
            throw new IllegalArgumentException("Wrong list elements/limit: "+key); return list;
    }
    private static ResourceLocation identifier(String value) {
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Expected explicit resource identifier");
        var id=ResourceLocation.tryParse(value); if(id==null) throw new IllegalArgumentException("Invalid resource identifier"); return id;
    }
}
