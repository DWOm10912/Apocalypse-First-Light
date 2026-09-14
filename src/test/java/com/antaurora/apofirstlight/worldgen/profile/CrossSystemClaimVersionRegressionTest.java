package com.antaurora.apofirstlight.worldgen.profile;

import java.util.*;
import java.io.*;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import com.antaurora.apofirstlight.worldgen.core.*;
import com.antaurora.apofirstlight.worldgen.spatial.*;
import static com.antaurora.apofirstlight.worldgen.spatial.ClaimQueryCompleteness.*;
import static com.antaurora.apofirstlight.worldgen.spatial.SpatialClaimStrength.*;
import static com.antaurora.apofirstlight.worldgen.spatial.SpatialClaimType.*;
import static com.antaurora.apofirstlight.worldgen.profile.ClaimProfileCompatibility.Status.*;
import static com.antaurora.apofirstlight.worldgen.profile.ProviderDescriptor.Capability.*;

/** B-01 blocker regression: real contract composition, entirely synthetic, no world or provider IO. */
public final class CrossSystemClaimVersionRegressionTest {
    private static int checks;
    private static final ResourceLocation RURAL = id("rural"), HIGHWAY = id("highway"), RADIO = id("radio"),
            PROTECTED = id("protected"), CAMP = id("camp"), CLAIMS = id("claims");
    private static final ResourceKey<Level> DIM = dimension(new ResourceLocation("minecraft:overworld"));
    private static final BoundsXZ AREA = new BoundsXZ(-100, -100, 100, 100), OVERLAP = new BoundsXZ(0, 0, 10, 10);
    private static final WorldgenProfile PROFILE = new WorldgenProfile("b01-synthetic", 1,
            Map.of(RURAL,"rural_v3", HIGHWAY,"highway_v7", RADIO,"radio_v2", PROTECTED,"protected_v2",
                    CAMP,"camp_v5", CLAIMS,"claims_v1"), "synthetic-resources");
    private static final Optional<WorldgenProfile> ACTIVE = Optional.of(PROFILE);
    private static final Map<ResourceLocation, Set<ProviderDescriptor.Capability>> REQUIRED = Map.of(
            RURAL,Set.of(CLAIM_QUERY,DETERMINISTIC_CANDIDATES), HIGHWAY,Set.of(CLAIM_QUERY,DETERMINISTIC_CANDIDATES),
            PROTECTED,Set.of(CLAIM_QUERY,PROTECTION_QUERY));

    private static ResourceLocation id(String path) { return new ResourceLocation("wg051_test", path); }
    @SuppressWarnings("unchecked")
    private static ResourceKey<Level> dimension(ResourceLocation id) {
        try {
            var factory = ResourceKey.class.getDeclaredMethod("create",ResourceLocation.class,ResourceLocation.class);
            factory.setAccessible(true);
            return (ResourceKey<Level>) factory.invoke(null,new ResourceLocation("minecraft:dimension"),id);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static WorldgenIdentity scope(ResourceLocation owner, String version) {
        return new WorldgenIdentity(42, DIM, owner, version, PROFILE.resourceSnapshot());
    }
    private static SpatialClaim claim(ResourceLocation owner, String key, SpatialClaimType type, BoundsXZ bounds) {
        var version = PROFILE.systemVersions().get(owner);
        return new SpatialClaim(DeterministicClaimId.create(owner,DIM,version,key),owner,DIM,version,bounds,
                Optional.empty(),type,HARD,ClaimPriorityPolicy.defaultPriorityFor(type,HARD),0,List.of());
    }
    private static SpatialClaim changed(SpatialClaim c, String claimId, ResourceLocation owner, String version, BoundsXZ bounds) {
        return new SpatialClaim(claimId,owner,c.dimension(),version,bounds,c.yRange(),c.type(),c.strength(),
                c.priority(),c.exclusionMargin(),c.connectionEdges());
    }
    private static ClaimQueryResult complete(List<SpatialClaim> claims) {
        return new ClaimQueryResult(claims,COMPLETE,List.of(),claims.size());
    }
    private static ClaimProfileCompatibility.ValidatedClaims validate(ClaimQueryResult query) {
        return ClaimProfileCompatibility.validateClaimsForProfile(ACTIVE,query);
    }
    private static ClaimConflictResolver.Resolution resolve(List<SpatialClaim> claims) { return validate(complete(claims)).resolveCandidates(); }
    private static void check(boolean condition,String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void rejects(Class<? extends Throwable> type,Runnable action) {
        checks++;
        try { action.run(); } catch(Throwable e) { if(type.isInstance(e)) return; throw new AssertionError("Unexpected failure",e); }
        throw new AssertionError("Invalid input accepted; expected " + type);
    }
    private static void deferred(ClaimProfileCompatibility.ValidatedClaims value) {
        var result = value.resolveCandidates();
        check(value.query().isUnknown() && !value.query().isKnownEmpty(),"ineligible/unknown is not known-empty");
        check(result.completeness()==UNKNOWN && result.accepted().isEmpty() && result.rejected().isEmpty(),
                "arbitration deferred for entire set, no partial winner or rejection");
    }

    private static void blockerB01CrossSystemArbitration() {
        var rural=claim(RURAL,"site",SITE,OVERLAP); var highway=claim(HIGHWAY,"route",INFRASTRUCTURE,OVERLAP);
        for(var c:List.of(rural,highway)) check(ClaimProfileCompatibility.check(ACTIVE,c).status()==COMPATIBLE,"owner-local compatibility");
        check(!rural.generationVersion().equals(highway.generationVersion()),"fixture must expose original blocker");
        var result=resolve(List.of(rural,highway));
        check(result.completeness()==COMPLETE && result.failures().isEmpty(),"multi-version arbitration runs");
        check(result.accepted().equals(List.of(highway)) && result.rejected().equals(List.of(rural)),"highway v7 beats rural v3");
        check(result.equals(resolve(List.of(highway,rural))),"pair order independent");
        var radio=claim(RADIO,"site",SITE,OVERLAP);
        var tieWinner=rural.id().compareTo(radio.id())<0?rural:radio;
        check(resolve(List.of(rural,radio)).accepted().equals(List.of(tieWinner)),"same-priority multi-version stable ID winner");
        check(resolve(List.of(radio,rural)).equals(resolve(List.of(rural,radio))),"same-priority reverse order");
        // A finite Highway-owned site can also tie at priority 50; not a persisted route corridor.
        var highwaySite=claim(HIGHWAY,"maintenance-site",SITE,OVERLAP);
        var tieV7=rural.id().compareTo(highwaySite.id())<0?rural:highwaySite;
        check(resolve(List.of(rural,highwaySite)).accepted().equals(List.of(tieV7)),"rural v3/highway v7 lexical tie");
        check(resolve(List.of(highwaySite,rural)).equals(resolve(List.of(rural,highwaySite))),"v3/v7 tie symmetric");
        var protectedSite=claim(PROTECTED,"protected",PROTECTED_SITE,OVERLAP);
        var triple=resolve(List.of(rural,highway,protectedSite));
        check(triple.accepted().equals(List.of(protectedSite)) && triple.rejected().equals(List.of(highway,rural)),
                "protected v2 > highway v7 > rural v3");
        check(!rural.id().equals(DeterministicClaimId.create(RURAL,DIM,"rural_v2","site")),"stable ID still includes version");
    }

    private static void eligibilityAndDuplicates() {
        var rural=claim(RURAL,"site",SITE,OVERLAP); var highway=claim(HIGHWAY,"route",INFRASTRUCTURE,OVERLAP);
        for(var c:List.of(rural,highway)) {
            var old=changed(c,c.id()+"-old",c.owner(),"old-version",c.boundsXZ());
            check(ClaimProfileCompatibility.check(ACTIVE,old).status()==VERSION_MISMATCH,"stale owner version rejected");
            var q=validate(complete(List.of(rural,highway,old))); deferred(q);
            check(q.query().claims().containsAll(List.of(rural,highway)) && !q.query().claims().contains(old),"retain compatible evidence only");
            check(q.query().failures().stream().allMatch(f->f.reason()==GenerationFailureReason.VERSION_MISMATCH),"hard mismatch, not warning");
            check(old.generationVersion().equals("old-version"),"no automatic upgrade");
        }
        var stranger=changed(rural,"stranger",id("absent"),rural.generationVersion(),OVERLAP);
        check(ClaimProfileCompatibility.check(ACTIVE,stranger).status()==MISSING_OWNER_VERSION,"missing owner cannot borrow matching string");
        deferred(validate(complete(List.of(stranger))));
        check(ClaimProfileCompatibility.check(Optional.empty(),rural).status()==PROFILE_UNAVAILABLE,"missing profile explicit");
        for(var input:List.of(complete(List.of()),complete(List.of(rural))))
            deferred(ClaimProfileCompatibility.validateClaimsForProfile(Optional.empty(),input));
        check(validate(complete(List.of())).query().isKnownEmpty(),"compatible complete empty retains semantics");
        var budgetFailure=new GenerationFailure(GenerationFailureReason.BUDGET_EXCEEDED,"synthetic budget");
        var partial=new ClaimQueryResult(List.of(rural),UNKNOWN,List.of(budgetFailure),1);
        var checked=validate(partial); deferred(checked);
        check(checked.query().equals(partial),"compatible UNKNOWN retains evidence, failure and operation count");
        deferred(validate(new ClaimQueryResult(List.of(),UNKNOWN,List.of(),0)));
        deferred(validate(new ClaimQueryResult(List.of(rural),UNKNOWN,List.of(),1)));
        check(resolve(List.of(rural,rural)).accepted().equals(List.of(rural)),"identical ID idempotent");
        var edited=changed(rural,rural.id(),RURAL,rural.generationVersion(),new BoundsXZ(1,0,10,10));
        var oldSameId=changed(rural,rural.id(),RURAL,"rural_v2",OVERLAP);
        var ownerCollision=changed(rural,rural.id(),HIGHWAY,"highway_v7",OVERLAP);
        for(var bad:List.of(edited,oldSameId,ownerCollision)) {
            for(var input:List.of(List.of(rural,bad,highway),List.of(bad,highway,rural))) {
                var q=validate(complete(input)); deferred(q);
                check(q.query().claims().equals(List.of(highway)),"ALL collided ID variants quarantined before validation");
                check(!q.query().failures().isEmpty(),"duplicate diagnostic preserved");
            }
            check(ClaimConflictResolver.resolveWinner(rural,bad).kind()==ClaimConflict.Kind.INVALID,"defensive pair duplicate invalid");
        }
        rejects(UnsupportedOperationException.class,()->validate(complete(List.of(rural))).query().claims().clear());
        rejects(NullPointerException.class,()->ClaimProfileCompatibility.check(null,rural));
        rejects(NullPointerException.class,()->ClaimProfileCompatibility.check(ACTIVE,null));
        rejects(NullPointerException.class,()->ClaimProfileCompatibility.validateClaimsForProfile(ACTIVE,null));
    }

    private static ProviderActivationGate.Evidence evidence(ResourceLocation owner, String version, SpatialClaim claim, boolean available) {
        var identity=scope(owner,version);
        var query=new InMemoryClaimProvider(identity,List.of(claim),available).query(identity,AREA,new QueryBudget(10));
        return new ProviderActivationGate.Evidence(new ProviderDescriptor(owner,version,REQUIRED.get(owner),true,COMPLETE,true),identity,AREA,query);
    }
    private static ProviderActivationGate.Result gate(WorldgenProfile profile,List<ProviderActivationGate.Evidence> evidence) {
        return ProviderActivationGate.evaluate(Optional.of(profile),profile,scope(CLAIMS,"claims_v1"),AREA,REQUIRED,evidence);
    }
    /** Test aggregation preserves every provider's UNKNOWN/errors; Gate remains a separate scoped preflight. */
    private static ClaimQueryResult aggregate(List<ProviderActivationGate.Evidence> evidence) {
        var claims=new ArrayList<SpatialClaim>(); var failures=new ArrayList<GenerationFailure>();
        int used=0; boolean unknown=false;
        for(var e:evidence) { var q=e.query(); claims.addAll(q.claims()); failures.addAll(q.failures()); used+=q.operationsUsed(); unknown|=q.isUnknown(); }
        return new ClaimQueryResult(claims,unknown?UNKNOWN:COMPLETE,failures,used);
    }
    private static void providerGateComposition() {
        var rural=claim(RURAL,"site",SITE,OVERLAP); var highway=claim(HIGHWAY,"route",INFRASTRUCTURE,OVERLAP);
        var protectedSite=claim(PROTECTED,"protected",PROTECTED_SITE,new BoundsXZ(40,40,50,50));
        var r=evidence(RURAL,"rural_v3",rural,true); var h=evidence(HIGHWAY,"highway_v7",highway,true);
        var p=evidence(PROTECTED,"protected_v2",protectedSite,true); var ready=List.of(r,h,p);
        check(gate(PROFILE,ready).status()==ProviderActivationGate.Status.READY,"distinct matching versions gate READY");
        var resolved=validate(aggregate(ready)).resolveCandidates();
        check(resolved.completeness()==COMPLETE && resolved.accepted().equals(List.of(protectedSite,highway)),
                "real provider query -> READY gate -> validated set -> spatial arbitration");
        var old=changed(rural,rural.id(),RURAL,"rural_v2",OVERLAP);
        var oldEvidence=List.of(evidence(RURAL,"rural_v2",old,true),h,p);
        check(gate(PROFILE,oldEvidence).status()==ProviderActivationGate.Status.INCOMPATIBLE,"provider wrong owner version incompatible");
        deferred(validate(aggregate(oldEvidence)));
        var missing=new HashMap<>(PROFILE.systemVersions()); missing.remove(HIGHWAY);
        var missingProfile=new WorldgenProfile(PROFILE.profileId(),1,missing,PROFILE.resourceSnapshot());
        check(gate(missingProfile,ready).status()==ProviderActivationGate.Status.INCOMPATIBLE,"required Highway profile version missing");
        var unknown=List.of(r,evidence(HIGHWAY,"highway_v7",highway,false),p);
        check(gate(PROFILE,unknown).status()==ProviderActivationGate.Status.UNKNOWN,"UNKNOWN provider cannot READY");
        deferred(validate(aggregate(unknown)));
        check(gate(PROFILE,List.of(r,p)).status()==ProviderActivationGate.Status.MISSING_PROVIDER,"missing provider cannot READY");
        check(ProfileCompatibility.check(ACTIVE,missingProfile,REQUIRED.keySet()).status()==ProfileCompatibility.Status.INCOMPATIBLE,"freeze not relaxed");
        var changedVersions=new HashMap<>(PROFILE.systemVersions()); changedVersions.put(RURAL,"rural_v2");
        check(ProfileCompatibility.check(ACTIVE,new WorldgenProfile(PROFILE.profileId(),1,changedVersions,PROFILE.resourceSnapshot()),
                REQUIRED.keySet()).status()==ProfileCompatibility.Status.INCOMPATIBLE,"owner version freeze remains exact");
    }

    private static ClaimIndexEntry entry(SpatialClaim claim) { return new ClaimIndexEntry(claim,ClaimStage.ACCEPTED_PLAN,"digest-"+claim.id(),"source-"+claim.id(),1); }
    private static void multiSystemIndexRoundTrip() throws IOException {
        var rural=claim(RURAL,"accepted",SITE,OVERLAP);
        var finite=List.of(rural,claim(RADIO,"accepted",SITE,OVERLAP),claim(CAMP,"accepted",SITE,OVERLAP),
                claim(HIGHWAY,"finite-maintenance-site",SITE,OVERLAP));
        var index=new LimitedClaimIndex(42,PROFILE,10);
        for(var c:finite) check(index.upsert(PROFILE,entry(c)).status()==LimitedClaimIndex.UpdateStatus.INSERTED,"owner-local accepted index entries coexist");
        var encoded=ClaimIndexCodec.encode(index.snapshot());
        var bytes=new ByteArrayOutputStream();
        NbtIo.write(encoded,new DataOutputStream(bytes));
        var binary=NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),new NbtAccounter(4_000_000));
        var decoded=ClaimIndexCodec.decode(binary,CrossSystemClaimVersionRegressionTest::dimension);
        check(decoded.profile().equals(PROFILE),"roundtrip exact per-system profile map");
        check(decoded.equals(index.snapshot()),"multi-version full snapshot roundtrip");
        for(var c:finite) check(decoded.entries().stream().anyMatch(v->v.entry().claim().equals(c)),"owner-local claim version not normalized");
        check(decoded.entries().stream().allMatch(v->v.verification()==ClaimIndexVerification.UNVERIFIED),"decoded evidence remains unverified");
        check(decoded.query(scope(CLAIMS,"claims_v1"),AREA,new QueryBudget(10)).completeness()==UNKNOWN,"mirror never becomes COMPLETE candidate authority");
        check(index.upsert(PROFILE,entry(rural)).status()==LimitedClaimIndex.UpdateStatus.IDEMPOTENT,"index repeat idempotent");
        var bad=changed(rural,rural.id(),RURAL,"rural_v2",OVERLAP);
        check(index.upsert(PROFILE,entry(bad)).status()==LimitedClaimIndex.UpdateStatus.REJECTED,"index same-ID version cannot overwrite");
        check(index.snapshot().entries().stream().anyMatch(v->v.entry().claim().equals(rural) && v.verification()==ClaimIndexVerification.MISMATCH),"index retains old geometry and mismatch");
        var collision=changed(rural,rural.id(),RADIO,"radio_v2",OVERLAP);
        check(index.upsert(PROFILE,entry(collision)).status()==LimitedClaimIndex.UpdateStatus.REJECTED,"index cross-owner ID collision rejected");
        var corrupt=encoded.copy(); ((ListTag)corrupt.get("entries")).getCompound(0).putString("generation_version","global-v1");
        rejects(IllegalArgumentException.class,()->ClaimIndexCodec.decode(corrupt,CrossSystemClaimVersionRegressionTest::dimension));
        rejects(IllegalArgumentException.class,()->entry(claim(HIGHWAY,"route",INFRASTRUCTURE,OVERLAP)));
    }

    private static void multiVersionOrderIndependence() {
        var rural=claim(RURAL,"site",SITE,OVERLAP); var radio=claim(RADIO,"site",SITE,OVERLAP);
        var highway=claim(HIGHWAY,"route",INFRASTRUCTURE,OVERLAP); var protectedSite=claim(PROTECTED,"protected",PROTECTED_SITE,OVERLAP);
        var far=claim(CAMP,"remote",SITE,new BoundsXZ(40,40,50,50));
        var soft=new SpatialClaim(DeterministicClaimId.create(RADIO,DIM,"radio_v2","soft"),RADIO,DIM,"radio_v2",OVERLAP,
                Optional.empty(),SITE,SOFT,10,0,List.of());
        var shuffled=new ArrayList<>(List.of(rural,radio,highway,protectedSite,far,soft,rural));
        var baseline=resolve(shuffled);
        check(baseline.accepted().equals(List.of(protectedSite,far,soft)),"independent expected accepted IDs, not only self comparison");
        var canonical=validate(complete(shuffled)).query(); var tie=resolve(List.of(rural,radio));
        var random=new Random(0x5747303531L);
        for(int round=0;round<12_000;round++) {
            Collections.shuffle(shuffled,random);
            var validated=validate(complete(shuffled));
            check(validated.query().equals(canonical),"multi-version canonical query/failures/order");
            check(validated.resolveCandidates().equals(baseline),"multi-version accepted/rejected deterministic");
            check(resolve(round%2==0?List.of(rural,radio):List.of(radio,rural)).equals(tie),"cross-version tie stable");
        }
    }

    public static void main(String[] args) throws IOException {
        blockerB01CrossSystemArbitration(); eligibilityAndDuplicates(); providerGateComposition();
        multiSystemIndexRoundTrip(); multiVersionOrderIndependence();
        System.out.println("PASS WG-05.1 B-01 cross-system version regression checks="+checks
                +"; multi-version shuffles=12000; synthetic provider/profile/index composition; no world bootstrap");
    }
}
