package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.*;
import java.util.concurrent.Executors;
import com.antaurora.apofirstlight.worldgen.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import static com.antaurora.apofirstlight.worldgen.spatial.SpatialClaimType.*;
import static com.antaurora.apofirstlight.worldgen.spatial.SpatialClaimStrength.*;
import static com.antaurora.apofirstlight.worldgen.spatial.ClaimQueryCompleteness.*;
import static com.antaurora.apofirstlight.worldgen.spatial.ClaimConflict.Kind.*;

/** WG-04 pure contract checks. Margin examples are the specification, not derived from expansion code. */
public final class ClaimContractTest {
    private static int checks;
    private static int shuffleRounds;
    private static int permutations;
    private static final ResourceLocation OWNER = new ResourceLocation("apocalypse_firstlight", "synthetic_test");
    private static final ResourceKey<Level> OVERWORLD = dimension("overworld");
    private static final ResourceKey<Level> NETHER = dimension("the_nether");
    private static final WorldgenIdentity IDENTITY = new WorldgenIdentity(42L, OVERWORLD, OWNER, "v1", "fixture-v1");
    private static final BoundsXZ AREA = new BoundsXZ(-100, -100, 100, 100);

    // Like WG-02: intern only the ResourceKey value without bootstrapping Minecraft registries/worlds.
    @SuppressWarnings("unchecked")
    private static ResourceKey<Level> dimension(String name) {
        try {
            var factory = ResourceKey.class.getDeclaredMethod("create", ResourceLocation.class, ResourceLocation.class);
            factory.setAccessible(true);
            return (ResourceKey<Level>) factory.invoke(null, new ResourceLocation("minecraft", "dimension"),
                    new ResourceLocation("minecraft", name));
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static final class Draft {
        String id = "a", version = "v1";
        ResourceLocation owner = OWNER;
        ResourceKey<Level> dimension = OVERWORLD;
        BoundsXZ bounds = new BoundsXZ(0, 0, 10, 10);
        Optional<YRange> y = Optional.empty();
        SpatialClaimType type = SITE;
        SpatialClaimStrength strength = HARD;
        int priority = 50, margin;
        List<String> edges = List.of();
        SpatialClaim build() { return new SpatialClaim(id, owner, dimension, version, bounds, y, type, strength, priority, margin, edges); }
    }

    private static SpatialClaim claim(String id, BoundsXZ bounds, SpatialClaimType type, SpatialClaimStrength strength,
                                       int margin, Optional<YRange> y) {
        return new SpatialClaim(id, OWNER, OVERWORLD, "v1", bounds, y, type, strength,
                ClaimPriorityPolicy.defaultPriorityFor(type, strength), margin, List.of("shared-edge"));
    }

    private static ClaimQueryResult complete(List<SpatialClaim> claims) { return new ClaimQueryResult(claims, COMPLETE, List.of(), 0); }
    private static InMemoryClaimProvider provider(List<SpatialClaim> claims) { return new InMemoryClaimProvider(IDENTITY, claims, true); }
    private static ClaimQueryResult query(InMemoryClaimProvider provider, int budget) { return provider.query(IDENTITY, AREA, new QueryBudget(budget)); }
    private static List<String> ids(List<SpatialClaim> claims) { return claims.stream().map(SpatialClaim::id).toList(); }
    private static boolean reason(ClaimQueryResult result, GenerationFailureReason reason) {
        return result.failures().stream().anyMatch(f -> f.reason() == reason);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(Class<? extends Throwable> type, Runnable action) {
        checks++;
        try { action.run(); }
        catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Unexpected failure instead of " + type, failure);
        }
        throw new AssertionError("Accepted invalid input instead of " + type);
    }

    private static void marginExamples() {
        var a = new BoundsXZ(0, 0, 10, 10);
        require(!ClaimConflictResolver.overlapsXZ(a, new BoundsXZ(10, 0, 20, 10), 0), "touching, margin zero");
        require(ClaimConflictResolver.overlapsXZ(a, new BoundsXZ(10, 0, 20, 10), 1), "touching violates one-cell gap");
        require(!ClaimConflictResolver.overlapsXZ(a, new BoundsXZ(11, 0, 20, 10), 1), "exact one-cell gap is enough");
        require(ClaimConflictResolver.overlapsXZ(a, new BoundsXZ(12, 0, 20, 10), 3), "two cells insufficient for margin three");
        require(!ClaimConflictResolver.overlapsXZ(a, new BoundsXZ(13, 0, 20, 10), 3), "exact three-cell gap is enough");
        require(!ClaimConflictResolver.overlapsXZ(a, new BoundsXZ(13, 13, 20, 20), 3), "rectangular, not Euclidean margin");
        require(ClaimConflictResolver.overlapsXZ(a, new BoundsXZ(12, 12, 20, 20), 3), "diagonal inside rectangular margin");
        require(!ClaimConflictResolver.overlapsXZ(new BoundsXZ(0, 0, 0, 5), a, 3), "empty bounds never acquire occupancy");
        var farLeft = new BoundsXZ(Integer.MIN_VALUE, -5, Integer.MIN_VALUE + 10, 5);
        var farRight = new BoundsXZ(Integer.MAX_VALUE - 10, -5, Integer.MAX_VALUE, 5);
        require(!ClaimConflictResolver.overlapsXZ(farLeft, farRight, Integer.MAX_VALUE), "full-domain distance does not wrap");
        require(ClaimConflictResolver.overlapsXZ(farRight, farRight, Integer.MAX_VALUE), "max-side dilation does not wrap");
        require(ClaimConflictResolver.overlapsXZ(farLeft, farLeft, Integer.MAX_VALUE), "min-side dilation does not wrap");
    }

    private static void values() {
        List<java.util.function.Consumer<Draft>> nulls = List.of(d -> d.id = null, d -> d.owner = null,
                d -> d.dimension = null, d -> d.version = null, d -> d.bounds = null, d -> d.y = null,
                d -> d.type = null, d -> d.strength = null, d -> d.edges = null,
                d -> d.edges = Arrays.asList("edge", null));
        for (var edit : nulls) { var d = new Draft(); edit.accept(d); rejects(NullPointerException.class, d::build); }
        List<java.util.function.Consumer<Draft>> invalid = List.of(d -> d.id = " \t", d -> d.version = "\n",
                d -> d.margin = -1, d -> d.priority = 9999, d -> d.priority = 70,
                d -> d.bounds = new BoundsXZ(0, 0, 0, 5), d -> d.y = Optional.of(new YRange(5, 5)));
        for (var edit : invalid) { var d = new Draft(); edit.accept(d); rejects(IllegalArgumentException.class, d::build); }
        for (var type : SpatialClaimType.values()) for (var strength : SpatialClaimStrength.values()) {
            int expected = strength == SOFT ? 10 : type == PROTECTED_SITE ? 100 : type == INFRASTRUCTURE ? 70 : 50;
            require(ClaimPriorityPolicy.defaultPriorityFor(type, strength) == expected, "fixed priority");
            var d = new Draft(); d.type = type; d.strength = strength; d.priority = expected;
            require(d.build().priority() == expected, "legal policy construction");
            d.priority++;
            rejects(IllegalArgumentException.class, d::build);
        }
        var d = new Draft(); var edges = new ArrayList<>(List.of("edge")); d.edges = edges;
        var claim = d.build(); edges.clear();
        require(claim.connectionEdges().equals(List.of("edge")), "claim defensive edges");
        rejects(UnsupportedOperationException.class, () -> claim.connectionEdges().clear());
        var claims = new ArrayList<>(List.of(claim)); var failures = new ArrayList<GenerationFailure>();
        var result = new ClaimQueryResult(claims, COMPLETE, failures, 1);
        var snapshot = provider(claims); claims.clear(); failures.add(ClaimSets.mismatch("late mutation"));
        require(result.claims().equals(List.of(claim)) && result.failures().isEmpty(), "result defensive copies");
        require(query(snapshot, 1).claims().equals(List.of(claim)), "provider defensive snapshot");
        rejects(UnsupportedOperationException.class, () -> result.claims().clear());
        rejects(UnsupportedOperationException.class, () -> result.failures().add(ClaimSets.mismatch("mutation")));
        rejects(IllegalArgumentException.class, () -> new ClaimQueryResult(List.of(), COMPLETE, List.of(), -1));
        rejects(NullPointerException.class, () -> new ClaimQueryResult(null, COMPLETE, List.of(), 0));
        rejects(NullPointerException.class, () -> new ClaimQueryResult(List.of(), null, List.of(), 0));
        rejects(NullPointerException.class, () -> new ClaimQueryResult(List.of(), COMPLETE, null, 0));
        rejects(NullPointerException.class, () -> new ClaimQueryResult(Arrays.asList(claim, null), COMPLETE, List.of(), 0));
        rejects(NullPointerException.class, () -> new ClaimQueryResult(List.of(), COMPLETE, Arrays.asList((GenerationFailure) null), 0));
        rejects(IllegalArgumentException.class, () -> ClaimConflictResolver.overlapsXZ(AREA, AREA, -1));
        rejects(NullPointerException.class, () -> ClaimConflictResolver.overlapsXZ(null, AREA, 0));
        rejects(NullPointerException.class, () -> ClaimConflictResolver.resolveWinner(claim, null));
        rejects(NullPointerException.class, () -> ClaimConflictResolver.resolveCandidates(null));
        rejects(NullPointerException.class, () -> new InMemoryClaimProvider(null, List.of(), true));
        rejects(NullPointerException.class, () -> new InMemoryClaimProvider(IDENTITY, null, true));
        rejects(NullPointerException.class, () -> snapshot.query(null, AREA, new QueryBudget(1)));
        rejects(NullPointerException.class, () -> snapshot.query(IDENTITY, null, new QueryBudget(1)));
        rejects(NullPointerException.class, () -> snapshot.query(IDENTITY, AREA, null));
        rejects(IllegalArgumentException.class, () -> new ClaimConflict(BLOCKING, Optional.empty(), List.of()));
        rejects(IllegalArgumentException.class, () -> new ClaimConflict(INVALID, Optional.empty(), List.of()));
        rejects(IllegalArgumentException.class, () -> new ClaimConflictResolver.Resolution(List.of(claim), List.of(), UNKNOWN, List.of()));
        var resolution = ClaimConflictResolver.resolveCandidates(result);
        rejects(UnsupportedOperationException.class, () -> resolution.accepted().clear());
    }

    /** Independent signed axis separation oracle, including negative distance for actual overlap. */
    private static long separation(int aMin, int aMax, int bMin, int bMax) {
        return (long) Math.max(aMin, bMin) - Math.min(aMax, bMax);
    }

    private static void mathProperties() {
        // Exhaustive short signed intervals, multiple widths, Z offsets and margins; no random luck.
        for (int x = -8; x <= 8; x++) for (int bx = -8; bx <= 8; bx++)
            for (int width = 1; width <= 3; width++) for (int bz = -2; bz <= 2; bz++) for (int m = 0; m <= 4; m++) {
                var a = new BoundsXZ(x, -1, x + width, 2);
                var b = new BoundsXZ(bx, bz, bx + 2, bz + 1);
                boolean expected = separation(a.minX(), a.maxXExclusive(), b.minX(), b.maxXExclusive()) < m
                        && separation(a.minZ(), a.maxZExclusive(), b.minZ(), b.maxZExclusive()) < m;
                require(ClaimConflictResolver.overlapsXZ(a, b, m) == expected, "exhaustive separation oracle");
                require(ClaimConflictResolver.overlapsXZ(a, b, m) == ClaimConflictResolver.overlapsXZ(b, a, m), "margin symmetry");
            }
        var random = new Random(0x57473034);
        for (int i = 0; i < 10_000; i++) {
            int x1 = random.nextInt(), x2 = random.nextInt(), z1 = random.nextInt(), z2 = random.nextInt();
            int bx1 = random.nextInt(), bx2 = random.nextInt(), bz1 = random.nextInt(), bz2 = random.nextInt();
            var a = new BoundsXZ(Math.min(x1,x2), Math.min(z1,z2), Math.max(x1,x2), Math.max(z1,z2));
            var b = new BoundsXZ(Math.min(bx1,bx2), Math.min(bz1,bz2), Math.max(bx1,bx2), Math.max(bz1,bz2));
            int m = random.nextInt(Integer.MAX_VALUE);
            boolean expected = !a.isEmpty() && !b.isEmpty()
                    && separation(a.minX(), a.maxXExclusive(), b.minX(), b.maxXExclusive()) < m
                    && separation(a.minZ(), a.maxZExclusive(), b.minZ(), b.maxZExclusive()) < m;
            require(ClaimConflictResolver.overlapsXZ(a,b,m) == expected, "full int-domain oracle");
            require(ClaimConflictResolver.overlapsXZ(a,b,m) == ClaimConflictResolver.overlapsXZ(b,a,m), "full-domain symmetric");
        }
        for (int ma = 0; ma <= 6; ma++) for (int mb = 0; mb <= 6; mb++) for (int gap = 0; gap <= 13; gap++) {
            var a = claim("a", new BoundsXZ(-10,-5,0,5), SITE,HARD,ma,Optional.empty());
            var b = claim("b", new BoundsXZ(gap,-5,gap+10,5), SITE,HARD,mb,Optional.empty());
            require((ClaimConflictResolver.resolveWinner(a,b).kind() == BLOCKING) == (gap < Math.max(ma,mb)), "max, NOT summed margins");
            require(ClaimConflictResolver.resolveWinner(a,b).equals(ClaimConflictResolver.resolveWinner(b,a)), "pair symmetric incl winner");
        }
        for (int lo = -20; lo <= 20; lo++) for (int other = -20; other <= 20; other++) {
            var a = claim("a", AREA,SITE,HARD,3,Optional.of(new YRange(lo,lo+5)));
            var b = claim("b", AREA,SITE,HARD,7,Optional.of(new YRange(other,other+4)));
            require((ClaimConflictResolver.resolveWinner(a,b).kind() == BLOCKING) == (Math.max(lo,other) < Math.min(lo+5,other+4)), "Y has no margin");
            var full = claim("full",AREA,SITE,HARD,0,Optional.empty());
            require(ClaimConflictResolver.resolveWinner(full,b).kind() == BLOCKING, "absent Y means full column");
        }
    }

    private static void stableIds() {
        String id = DeterministicClaimId.create(OWNER, OVERWORLD, "v1", "region:12,-4");
        require(id.equals("apocalypse_firstlight:synthetic_test|minecraft:dimension|minecraft:overworld|v1|region:12,-4"), "specified stable spelling");
        var unique = new HashSet<String>();
        for (int x = -30; x <= 30; x++) for (int z = -3; z <= 3; z++) {
            String key = "region:" + x + "," + z;
            String value = DeterministicClaimId.create(OWNER,OVERWORLD,"v1",key);
            require(value.equals(DeterministicClaimId.create(new ResourceLocation(OWNER.toString()),dimension("overworld"),"v1",new String(key))), "value not object identity");
            require(unique.add(value), "distinct coordinates");
        }
        require(!id.equals(DeterministicClaimId.create(OWNER,NETHER,"v1","region:12,-4")), "dimension scoped");
        require(!id.equals(DeterministicClaimId.create(new ResourceLocation("test","other"),OVERWORLD,"v1","region:12,-4")), "owner scoped");
        require(!id.equals(DeterministicClaimId.create(OWNER,OVERWORLD,"v2","region:12,-4")), "version scoped");
        unique.clear();
        for (String version : List.of("a", "a|b", "a%7Cb", "%", "中文"))
            for (String key : List.of("b|c", "c", "%7C", "|", "中文"))
                require(unique.add(DeterministicClaimId.create(OWNER,OVERWORLD,version,key)), "delimiter/escape ambiguity");
        rejects(IllegalArgumentException.class, () -> DeterministicClaimId.create(OWNER,OVERWORLD," ","key"));
        rejects(IllegalArgumentException.class, () -> DeterministicClaimId.create(OWNER,OVERWORLD,"v1",""));
        rejects(NullPointerException.class, () -> DeterministicClaimId.create(null,OVERWORLD,"v1","key"));
        rejects(NullPointerException.class, () -> DeterministicClaimId.create(OWNER,null,"v1","key"));
        rejects(NullPointerException.class, () -> DeterministicClaimId.create(OWNER,OVERWORLD,null,"key"));
        rejects(NullPointerException.class, () -> DeterministicClaimId.create(OWNER,OVERWORLD,"v1",null));
    }

    private static void arbitration() {
        var rural = claim("synthetic-rural",AREA,SITE,HARD,0,Optional.empty());
        var highway = claim("synthetic-highway",AREA,INFRASTRUCTURE,HARD,0,Optional.empty());
        var protectedSite = claim("synthetic-protected",AREA,PROTECTED_SITE,HARD,0,Optional.empty());
        require(ClaimConflictResolver.resolveWinner(rural,highway).winner().orElseThrow().equals(highway), "synthetic highway > rural");
        require(ClaimConflictResolver.resolveWinner(highway,protectedSite).winner().orElseThrow().equals(protectedSite), "protected > highway");
        var a = claim("a",new BoundsXZ(0,0,10,10),SITE,HARD,0,Optional.empty());
        var b = claim("b",new BoundsXZ(5,0,15,10),SITE,HARD,0,Optional.empty());
        var c = claim("c",new BoundsXZ(10,0,20,10),SITE,HARD,0,Optional.empty());
        require(ClaimConflictResolver.resolveWinner(a,b).winner().orElseThrow().equals(a), "lexical smaller ID wins tie");
        var resolution = ClaimConflictResolver.resolveCandidates(complete(List.of(c,b,a)));
        require(ids(resolution.accepted()).equals(List.of("a")), "ADR-02 no greedy refill of C after B loses");
        require(ids(resolution.rejected()).equals(List.of("b","c")), "stable rejected candidates");
        require(ClaimConflictResolver.resolveWinner(a,b).kind() == BLOCKING, "shared edge does not grant exemption");
        for (var strengthA : SpatialClaimStrength.values()) for (var strengthB : SpatialClaimStrength.values()) {
            var first = claim("a",AREA,CONNECTION,strengthA,0,Optional.empty());
            var second = claim("b",AREA,BUILDING,strengthB,0,Optional.empty());
            boolean hard = strengthA == HARD && strengthB == HARD;
            var pair = ClaimConflictResolver.resolveWinner(first,second);
            require(pair.kind() == (hard ? BLOCKING : SOFT_OVERLAP), "soft non-blocking, no scoring");
            require(pair.winner().isPresent() == hard, "no soft automatic winner");
            require(ClaimConflictResolver.resolveCandidates(complete(List.of(second,first))).accepted().size() == (hard?1:2), "soft does not eliminate hard");
        }
        var d = new Draft(); d.dimension = NETHER; d.id = "nether";
        require(ClaimConflictResolver.resolveWinner(a,d.build()).kind() == NONE, "dimension isolation");
        var unknown = new ClaimQueryResult(List.of(a),UNKNOWN,List.of(),1);
        require(ClaimConflictResolver.resolveCandidates(unknown).accepted().isEmpty(), "UNKNOWN cannot accept known prefix");
        require(ClaimConflictResolver.resolveCandidates(unknown).completeness() == UNKNOWN, "deferred arbitration stays unknown");
    }

    private static void duplicates() {
        var original = new Draft().build();
        require(complete(List.of(original,original)).claims().equals(List.of(original)), "identical idempotent query dedup");
        require(ClaimConflictResolver.resolveWinner(original,original).kind() == IDENTICAL, "idempotent pair");
        require(query(provider(List.of(original,original)),1).completeness() == COMPLETE, "budget charges normalized candidates");
        List<java.util.function.Consumer<Draft>> edits = List.of(d -> d.version="v2", d -> d.owner=new ResourceLocation("test","owner"),
                d -> d.dimension=NETHER, d -> d.bounds=new BoundsXZ(1,0,10,10), d -> d.y=Optional.of(new YRange(0,10)),
                d -> d.type=BUILDING, d -> {d.type=PROTECTED_SITE;d.priority=100;}, d -> {d.strength=SOFT;d.priority=10;},
                d -> d.margin=2, d -> d.edges=List.of("edge"));
        for (var edit : edits) {
            var d = new Draft(); edit.accept(d); var changed = d.build();
            var pair = ClaimConflictResolver.resolveWinner(original,changed);
            require(pair.kind() == INVALID && pair.failures().get(0).reason() == GenerationFailureReason.VERSION_MISMATCH, "any same-ID content change invalid");
            require(pair.equals(ClaimConflictResolver.resolveWinner(changed,original)), "invalid pair symmetric");
            for (var input : List.of(List.of(original,changed,original),List.of(changed,original,changed))) {
                var q = query(provider(input),Integer.MAX_VALUE);
                require(q.isUnknown() && q.claims().isEmpty() && reason(q,GenerationFailureReason.VERSION_MISMATCH), "quarantine ALL ID variants");
                require(!q.isKnownEmpty(), "invalid duplicate is not empty land");
                require(ClaimConflictResolver.resolveCandidates(complete(input)).completeness()==UNKNOWN, "malformed complete input cannot arbitrate");
            }
        }
        var v2 = new Draft(); v2.id="new-id"; v2.version="v2";
        var result = query(provider(List.of(original,v2.build())),2);
        require(result.isUnknown() && result.claims().equals(List.of(original)), "unsupported version excluded, matching partial retained");
        require(reason(result,GenerationFailureReason.VERSION_MISMATCH), "version diagnostic");
        // Query completeness and owner-local eligibility are separate since WG-05.1.
        // Profile rejection now has dedicated cross-module regression coverage.
        require(ClaimConflictResolver.resolveWinner(original,v2.build()).kind()==BLOCKING, "spatial primitive does not certify versions");
        require(!complete(List.of(original,v2.build())).isUnknown(), "raw coverage is not profile eligibility");
        var otherOwner = new Draft(); otherOwner.id="other-owner"; otherOwner.owner=new ResourceLocation("test:other");
        var foreign = query(provider(List.of(original,otherOwner.build())),2);
        require(foreign.isUnknown() && foreign.claims().equals(List.of(original)), "single-owner provider rejects foreign owner even at same version");
    }

    private static void queries() {
        var a = new Draft().build();
        require(complete(List.of()).isKnownEmpty(), "complete empty known");
        require(!complete(List.of(a)).isKnownEmpty(), "complete occupied");
        require(!new ClaimQueryResult(List.of(),UNKNOWN,List.of(),0).isKnownEmpty(), "unknown empty not known");
        require(!new ClaimQueryResult(List.of(a),UNKNOWN,List.of(),1).isKnownEmpty(), "unknown occupied not known");
        require(query(provider(List.of()),0).isKnownEmpty(), "available empty snapshot complete at zero budget");
        var unavailable = query(new InMemoryClaimProvider(IDENTITY,List.of(),false),100);
        require(unavailable.isUnknown() && reason(unavailable,GenerationFailureReason.MISSING_RESOURCE), "unavailable never known empty");
        for (var identity : List.of(new WorldgenIdentity(43,OVERWORLD,OWNER,"v1","fixture-v1"),
                new WorldgenIdentity(42,OVERWORLD,OWNER,"v2","fixture-v1"),
                new WorldgenIdentity(42,OVERWORLD,OWNER,"v1","other-snapshot"))) {
            var q=provider(List.of(a)).query(identity,AREA,new QueryBudget(100));
            require(q.isUnknown() && reason(q,GenerationFailureReason.VERSION_MISMATCH) && q.operationsUsed()==0, "scope mismatch fail closed");
        }
        var otherConsumer = new WorldgenIdentity(42,OVERWORLD,new ResourceLocation("test","consumer"),"v1","fixture-v1");
        require(provider(List.of(a)).query(otherConsumer,AREA,new QueryBudget(1)).claims().equals(List.of(a)), "query consumer need not own claims");
        var netherIdentity = new WorldgenIdentity(42,NETHER,OWNER,"v1","fixture-v1");
        var nether = provider(List.of(a)).query(netherIdentity,AREA,new QueryBudget(1));
        require(nether.isKnownEmpty() && nether.operationsUsed()==1, "dimension miss still charged");
        for (int size=1; size<=80; size++) {
            var claims=new ArrayList<SpatialClaim>();
            for(int i=0;i<size;i++) claims.add(claim("id-"+i,AREA,SITE,HARD,0,Optional.empty()));
            var p=provider(claims);
            var expected=complete(claims).claims();
            for(int budget : new int[]{0,1,size-1,size,size+1,Integer.MAX_VALUE}) {
                var q=query(p,budget);
                require(q.operationsUsed()==Math.min(size,budget), "exact operation count");
                require(q.claims().equals(expected.subList(0,Math.min(size,budget))), "deterministic known prefix");
                require(q.isUnknown()==(budget<size), "budget completeness");
                require(reason(q,GenerationFailureReason.BUDGET_EXCEEDED)==(budget<size), "budget failure only when work remains");
                require(!q.isKnownEmpty(), "occupied or unfinished never empty");
            }
        }
        var far=claim("far",new BoundsXZ(500,500,510,510),SITE,HARD,0,Optional.empty());
        require(query(provider(List.of(far)),0).isUnknown(), "cannot skip unvisited area miss for free");
        var miss=query(provider(List.of(far)),1);
        require(miss.isKnownEmpty() && miss.operationsUsed()==1, "complete area miss known empty");
        var crossing=claim("crossing",new BoundsXZ(-100,-1,1,1),SITE,HARD,0,Optional.empty());
        var narrow=new BoundsXZ(0,0,2,2);
        require(provider(List.of(crossing)).query(IDENTITY,narrow,new QueryBudget(1)).claims().equals(List.of(crossing)), "outside origin/center but intersecting bounds");
        var nearby=claim("nearby",new BoundsXZ(5,0,8,2),SITE,HARD,4,Optional.empty());
        require(provider(List.of(nearby)).query(IDENTITY,narrow,new QueryBudget(1)).claims().equals(List.of(nearby)), "margin reaches query without bounds intersection");
        var exact=new BoundsXZ(0,0,1,2);
        require(provider(List.of(nearby)).query(IDENTITY,exact,new QueryBudget(1)).isKnownEmpty(), "exact margin gap excluded");
        var candidate=claim("candidate",narrow,SITE,HARD,4,Optional.empty());
        var zeroMargin=claim("zero-margin",new BoundsXZ(5,0,8,2),SITE,HARD,0,Optional.empty());
        require(provider(List.of(zeroMargin)).query(IDENTITY,narrow.expand(candidate.exclusionMargin()),new QueryBudget(1)).hasKnownClaims(), "caller envelope accounts for candidate margin");
        var empty=provider(List.of(nearby)).query(IDENTITY,new BoundsXZ(0,0,0,2),new QueryBudget(1));
        require(empty.isKnownEmpty(), "empty query envelope inert");
        var max=claim("max",new BoundsXZ(Integer.MAX_VALUE-10,0,Integer.MAX_VALUE,10),SITE,HARD,Integer.MAX_VALUE,Optional.empty());
        require(provider(List.of(max)).query(IDENTITY,new BoundsXZ(0,0,10,10),new QueryBudget(1)).hasKnownClaims(), "query expansion safe at int limit");
        rejects(ArithmeticException.class, () -> max.boundsXZ().expand(1));
    }

    private static List<SpatialClaim> dataset(int variant) {
        int x=-60+variant*13;
        var a=claim("a",new BoundsXZ(x,0,x+10,10),SITE,HARD,variant%3,Optional.empty());
        var b=claim("b",new BoundsXZ(x+5,0,x+15,10),SITE,HARD,0,Optional.empty());
        var c=claim("c",new BoundsXZ(x+10,0,x+20,10),SITE,HARD,0,Optional.empty());
        var high=claim("infra",new BoundsXZ(x-5,5,x+5,15),INFRASTRUCTURE,HARD,2,Optional.of(new YRange(-10,0)));
        var upper=claim("upper",high.boundsXZ(),BUILDING,HARD,3,Optional.of(new YRange(0,10)));
        var soft=claim("soft",AREA,PROTECTED_SITE,SOFT,3,Optional.empty());
        var protectedSite=claim("protected",new BoundsXZ(x,12,x+12,22),PROTECTED_SITE,HARD,1,Optional.empty());
        var list=new ArrayList<>(List.of(a,b,c,high,upper,soft,protectedSite,a));
        if(variant>=4) { var d=new Draft(); d.id="broken"; list.add(d.build()); d.margin=2; list.add(d.build()); }
        if(variant==5) { var d=new Draft(); d.id="old"; d.version="v0"; list.add(d.build()); }
        return list;
    }

    private static void verifyOrder(List<SpatialClaim> original,List<SpatialClaim> shuffled,ClaimQueryResult baseline,
                                     ClaimConflictResolver.Resolution resolution,ClaimQueryResult partial) {
        var p=provider(shuffled);
        require(query(p,Integer.MAX_VALUE).equals(baseline), "shuffled query canonical result");
        require(query(p,3).equals(partial), "shuffled budget prefix/errors/count");
        require(ClaimConflictResolver.resolveCandidates(complete(shuffled)).equals(resolution), "shuffled accepted/rejected IDs");
        // Compare pair outcomes by value, not shuffled position; includes IDENTICAL and INVALID pairs.
        for(int i=0;i<shuffled.size();i++) {
            var a=shuffled.get(i); var b=shuffled.get((i+1)%shuffled.size());
            require(ClaimConflictResolver.resolveWinner(a,b).equals(ClaimConflictResolver.resolveWinner(b,a)), "pair permutation outcome");
        }
        require(complete(shuffled).equals(complete(original)), "result constructor canonicalizes independently of provider");
    }

    private static void permute(List<SpatialClaim> list,int start,List<SpatialClaim> original,ClaimQueryResult baseline,
                                ClaimConflictResolver.Resolution resolution,ClaimQueryResult partial) {
        if(start==list.size()) { verifyOrder(original,list,baseline,resolution,partial); permutations++; return; }
        for(int i=start;i<list.size();i++) {
            Collections.swap(list,start,i); permute(list,start+1,original,baseline,resolution,partial); Collections.swap(list,start,i);
        }
    }

    private static void orderProperties() throws Exception {
        for(int variant=0;variant<6;variant++) {
            var original=dataset(variant); var shuffled=new ArrayList<>(original); var p=provider(original);
            var baseline=query(p,Integer.MAX_VALUE); var partial=query(p,3);
            var resolution=ClaimConflictResolver.resolveCandidates(complete(original));
            var random=new Random(0x57473034L + variant*1_000_003L);
            Collections.reverse(shuffled); verifyOrder(original,shuffled,baseline,resolution,partial);
            for(int round=0;round<2_000;round++) {
                Collections.shuffle(shuffled,random);
                verifyOrder(original,shuffled,baseline,resolution,partial); shuffleRounds++;
            }
        }
        var small=List.copyOf(dataset(0).subList(0,6)); var p=provider(small);
        permute(new ArrayList<>(small),0,small,query(p,100),ClaimConflictResolver.resolveCandidates(complete(small)),query(p,3));
        // Concurrent reads of one immutable snapshot; submission/completion order carries no state.
        var executor=Executors.newFixedThreadPool(4);
        try {
            var tasks=new ArrayList<java.util.concurrent.Callable<ClaimQueryResult>>();
            for(int i=0;i<128;i++) tasks.add(() -> query(p,3));
            for(var future:executor.invokeAll(tasks)) require(future.get().equals(query(p,3)), "concurrent snapshot deterministic");
        } finally { executor.shutdownNow(); }
        require(shuffleRounds>=10_000 && permutations==720, "shuffle and exhaustive permutation coverage");
    }

    public static void main(String[] args) throws Exception {
        marginExamples();
        values(); mathProperties(); stableIds(); arbitration(); duplicates(); queries(); orderProperties();
        System.out.println("PASS WG-04 spatial claim checks=" + checks + "; shuffle rounds=" + shuffleRounds
                + "; exhaustive permutations=" + permutations + "; no worldgen providers registered or world bootstrap");
    }
}
