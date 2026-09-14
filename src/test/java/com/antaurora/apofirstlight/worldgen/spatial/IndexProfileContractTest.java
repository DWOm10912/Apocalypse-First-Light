package com.antaurora.apofirstlight.worldgen.spatial;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import com.antaurora.apofirstlight.worldgen.core.*;
import com.antaurora.apofirstlight.worldgen.profile.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;
import static com.antaurora.apofirstlight.worldgen.spatial.ClaimStage.*;
import static com.antaurora.apofirstlight.worldgen.spatial.ClaimIndexVerification.*;
import static com.antaurora.apofirstlight.worldgen.profile.ProviderDescriptor.Capability.*;
import static com.antaurora.apofirstlight.worldgen.profile.ProviderActivationGate.Status.*;

/** WG-05 finite mirror/profile/gate tests, no world/bootstrap, real generator provider or filesystem writes. */
public final class IndexProfileContractTest {
    private static int checks;
    private static final ResourceLocation SITE = new ResourceLocation("test:site"), HIGHWAY = new ResourceLocation("test:highway"),
            PROTECTED = new ResourceLocation("test:protected"), COORDINATOR = new ResourceLocation("test:claims");
    private static final ResourceKey<Level> DIM = dimension(new ResourceLocation("minecraft:overworld"));
    private static final ResourceKey<Level> OTHER_DIM = dimension(new ResourceLocation("minecraft:the_nether"));
    private static final WorldgenProfile PROFILE = new WorldgenProfile("fixture-v1",1,
            Map.of(SITE,"site-v1",HIGHWAY,"route-v1",PROTECTED,"protect-v1",COORDINATOR,"contract-v1"),"resources-A");
    private static final BoundsXZ AREA = new BoundsXZ(-100,-100,100,100);
    private static final WorldgenIdentity IDENTITY = identity(COORDINATOR);
    private static final Map<ResourceLocation,Set<ProviderDescriptor.Capability>> REQUIRED = Map.of(
            SITE,Set.of(CLAIM_QUERY,DETERMINISTIC_CANDIDATES), HIGHWAY,Set.of(CLAIM_QUERY,DETERMINISTIC_CANDIDATES),
            PROTECTED,Set.of(CLAIM_QUERY,PROTECTION_QUERY));

    // WG-02's intern-only key fixture avoids Forge's registry bootstrap during pure codec tests.
    @SuppressWarnings("unchecked")
    private static ResourceKey<Level> dimension(ResourceLocation id) {
        try {
            var factory=ResourceKey.class.getDeclaredMethod("create",ResourceLocation.class,ResourceLocation.class);
            factory.setAccessible(true);
            return (ResourceKey<Level>)factory.invoke(null,new ResourceLocation("minecraft:dimension"),id);
        } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static WorldgenIdentity identity(ResourceLocation system) {
        return new WorldgenIdentity(42,DIM,system,PROFILE.systemVersions().get(system),PROFILE.resourceSnapshot());
    }
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    private static void rejects(Class<? extends Throwable> type,Runnable action) {
        checks++;
        try { action.run(); } catch(Throwable e) { if(type.isInstance(e)) return; throw new AssertionError("Unexpected failure",e); }
        throw new AssertionError("Invalid input accepted; expected "+type);
    }
    private static ClaimIndexEntry entry(String id, ClaimStage stage,long revision) {
        return entry(id,stage,revision,DIM,Optional.empty(),SpatialClaimType.SITE);
    }
    private static ClaimIndexEntry entry(String id,ClaimStage stage,long revision,ResourceKey<Level> dimension,
            Optional<YRange> y,SpatialClaimType type) {
        var claim=new SpatialClaim(id,SITE,dimension,"site-v1",new BoundsXZ(-20,-10,-5,7),y,type,
                SpatialClaimStrength.HARD,ClaimPriorityPolicy.defaultPriorityFor(type,SpatialClaimStrength.HARD),2,List.of("edge-A","edge-B"));
        return new ClaimIndexEntry(claim,stage,"digest-"+id,"source-"+id,revision);
    }
    private static LimitedClaimIndex index(int capacity) { return new LimitedClaimIndex(42,PROFILE,capacity); }
    private static LimitedClaimIndex.UpdateResult put(LimitedClaimIndex index,ClaimIndexEntry entry) { return index.upsert(PROFILE,entry); }
    private static ClaimIndexSnapshot.QueryResult query(ClaimIndexSnapshot snapshot,int budget) { return snapshot.query(IDENTITY,AREA,new QueryBudget(budget)); }
    private static List<ClaimIndexEntry> contents(ClaimIndexSnapshot snapshot) { return snapshot.entries().stream().map(ClaimIndexSnapshot.VerifiedEntry::entry).toList(); }
    private static ClaimIndexSnapshot decode(CompoundTag tag) { return ClaimIndexCodec.decode(tag,IndexProfileContractTest::dimension); }
    private static CompoundTag first(CompoundTag root) { return (CompoundTag)((ListTag)root.get("entries")).get(0); }
    private static void mismatch(LimitedClaimIndex.UpdateResult result) {
        check(result.status()==LimitedClaimIndex.UpdateStatus.REJECTED && result.failures().get(0).reason()==GenerationFailureReason.VERSION_MISMATCH,"reject mismatch");
    }

    private static void valuesAndProfiles() {
        for (var stage:List.of(ACCEPTED_PLAN,COMMITTED,PARTIAL)) check(entry("a",stage,0).stage()==stage,"persisted stage");
        rejects(IllegalArgumentException.class,()->entry("a",CANDIDATE_RESERVED,0));
        rejects(IllegalArgumentException.class,()->entry("a",PARTIAL,-1));
        rejects(IllegalArgumentException.class,()->entry("a",PARTIAL,1,DIM,Optional.empty(),SpatialClaimType.INFRASTRUCTURE));
        var base=entry("a",PARTIAL,1);
        rejects(IllegalArgumentException.class,()->new ClaimIndexEntry(new SpatialClaim("soft",SITE,DIM,"site-v1",AREA,Optional.empty(),
                SpatialClaimType.SITE,SpatialClaimStrength.SOFT,10,0,List.of()),PARTIAL,"digest","source",1));
        rejects(NullPointerException.class,()->new ClaimIndexEntry(null,PARTIAL,"d","s",0));
        rejects(NullPointerException.class,()->new ClaimIndexEntry(base.claim(),null,"d","s",0));
        rejects(NullPointerException.class,()->new ClaimIndexEntry(base.claim(),PARTIAL,null,"s",0));
        rejects(IllegalArgumentException.class,()->new ClaimIndexEntry(base.claim(),PARTIAL," ","s",0));
        rejects(IllegalArgumentException.class,()->new ClaimIndexEntry(base.claim(),PARTIAL,"d"," ",0));
        rejects(IllegalArgumentException.class,()->index(0));
        rejects(IllegalArgumentException.class,()->index(LimitedClaimIndex.MAX_ENTRIES+1));
        var map=new HashMap<>(PROFILE.systemVersions());
        var copy=new WorldgenProfile(PROFILE.profileId(),1,map,PROFILE.resourceSnapshot()); map.clear();
        check(copy.equals(PROFILE),"profile map copied");
        rejects(UnsupportedOperationException.class,()->copy.systemVersions().clear());
        for(String id:List.of(" ","LEGACY","legacy")) rejects(IllegalArgumentException.class,()->new WorldgenProfile(id,1,PROFILE.systemVersions(),"r"));
        rejects(NullPointerException.class,()->new WorldgenProfile(null,1,PROFILE.systemVersions(),"r"));
        rejects(IllegalArgumentException.class,()->new WorldgenProfile("p",2,PROFILE.systemVersions(),"r"));
        rejects(IllegalArgumentException.class,()->new WorldgenProfile("p",1,Map.of(),"r"));
        rejects(IllegalArgumentException.class,()->new WorldgenProfile("p",1,Map.of(SITE," "),"r"));
        rejects(IllegalArgumentException.class,()->new WorldgenProfile("p",1,PROFILE.systemVersions()," "));
        check(ProfileCompatibility.check(Optional.of(PROFILE),copy,REQUIRED.keySet()).status()==ProfileCompatibility.Status.COMPATIBLE,"exact freeze");
        check(ProfileCompatibility.check(Optional.empty(),PROFILE,REQUIRED.keySet()).status()==ProfileCompatibility.Status.LEGACY,"missing profile legacy");
        var changedVersions=new HashMap<>(PROFILE.systemVersions()); changedVersions.put(SITE,"site-v2");
        for(var changed:List.of(new WorldgenProfile("other",1,PROFILE.systemVersions(),PROFILE.resourceSnapshot()),
                new WorldgenProfile(PROFILE.profileId(),1,PROFILE.systemVersions(),"resources-B"),
                new WorldgenProfile(PROFILE.profileId(),1,changedVersions,PROFILE.resourceSnapshot()))) {
            var result=ProfileCompatibility.check(Optional.of(PROFILE),changed,REQUIRED.keySet());
            check(result.status()==ProfileCompatibility.Status.INCOMPATIBLE,"no newest/profile/resource fallback");
            check(result.failures().get(0).reason()==GenerationFailureReason.VERSION_MISMATCH,"typed profile failure");
            var index=index(2); mismatch(index.upsert(changed,base)); check(index.snapshot().entries().isEmpty(),"wrong plan profile not inserted");
        }
        check(ProfileCompatibility.check(Optional.of(PROFILE),PROFILE,Set.of(new ResourceLocation("test:missing"))).status()==ProfileCompatibility.Status.INCOMPATIBLE,"required version missing");
    }

    private static void indexAndVerification() {
        var index=index(3); var accepted=entry("a",ACCEPTED_PLAN,1); var partial=entry("a",PARTIAL,2); var committed=entry("a",COMMITTED,3);
        check(query(index.snapshot(),0).completeness()==ClaimQueryCompleteness.UNKNOWN && !query(index.snapshot(),0).isKnownEmpty(),"empty index not empty world");
        check(ClaimIndexVerification.compare(Optional.empty(),Optional.of(accepted))==UNVERIFIED,"crash: source exists index missing");
        check(index.verify("a",Optional.of(accepted))==UNVERIFIED,"no automatic missing-source import");
        check(put(index,accepted).status()==LimitedClaimIndex.UpdateStatus.INSERTED,"accepted insertion");
        var before=index.snapshot();
        check(put(index,accepted).status()==LimitedClaimIndex.UpdateStatus.IDEMPOTENT && before.equals(index.snapshot()),"same digest/version idempotent");
        check(index.verify("a",Optional.empty())==UNVERIFIED,"unavailable source");
        check(index.verify("a",Optional.of(accepted))==CONFIRMED,"matching authoritative source");
        check(index.verify("a",Optional.of(partial))==STALE,"index lags authoritative stage/revision");
        check(query(index.snapshot(),3).entries().size()==1,"stale retains occupancy");
        check(put(index,partial).status()==LimitedClaimIndex.UpdateStatus.ADVANCED,"partial advancement");
        var q=query(index.snapshot(),3);
        check(q.entries().get(0).entry().stage()==PARTIAL && !q.isKnownEmpty(),"partial cannot release protection");
        check(contents(before).equals(List.of(accepted)),"old snapshot unchanged after mutation");
        check(put(index,accepted).status()==LimitedClaimIndex.UpdateStatus.IDEMPOTENT && contents(index.snapshot()).equals(List.of(partial)),"old event cannot downgrade partial");
        check(put(index,committed).status()==LimitedClaimIndex.UpdateStatus.ADVANCED,"complete after partial");
        mismatch(put(index,entry("a",PARTIAL,4)));
        check(contents(index.snapshot()).equals(List.of(committed)),"no committed regression");
        var altered=new ClaimIndexEntry(committed.claim(),COMMITTED,"different-digest",committed.authoritativeSourceId(),3);
        check(ClaimIndexVerification.compare(Optional.of(committed),Optional.of(altered))==MISMATCH,"digest mismatch evidence");
        mismatch(put(index,altered));
        check(contents(index.snapshot()).equals(List.of(committed)),"reject does not overwrite envelope/digest");
        check(index.snapshot().entries().get(0).verification()==MISMATCH,"mismatch retained as bounded entry flag");
        check(query(index.snapshot(),3).entries().size()==1,"mismatch retains protection");
        check(index.verify("a",Optional.of(committed))==CONFIRMED,"explicit source comparison can reconfirm");
        var otherClaim=new SpatialClaim("a",SITE,DIM,"site-v2",committed.claim().boundsXZ(),Optional.empty(),SpatialClaimType.SITE,SpatialClaimStrength.HARD,50,0,List.of());
        mismatch(put(index,new ClaimIndexEntry(otherClaim,COMMITTED,committed.planDigest(),committed.authoritativeSourceId(),3)));
        var moved=entry("a",COMMITTED,3,OTHER_DIM,Optional.empty(),SpatialClaimType.SITE);
        mismatch(put(index,moved));
        mismatch(put(index,new ClaimIndexEntry(committed.claim(),COMMITTED,committed.planDigest(),"other-source",3)));
        var sameRevision=index(2); put(sameRevision,accepted); mismatch(put(sameRevision,entry("a",PARTIAL,1)));
        put(index,entry("z",PARTIAL,2,OTHER_DIM,Optional.of(new YRange(-60,-40)),SpatialClaimType.BUILDING));
        put(index,entry("b",ACCEPTED_PLAN,1)); // Same geometry, different authoritative plan ID: not first-write arbitration.
        check(contents(index.snapshot()).stream().map(ClaimIndexEntry::claimId).toList().equals(List.of("a","b","z")),"stable IDs, overlapping accepted plans not arbitrated by index");
        check(put(index,entry("full",PARTIAL,1)).failures().get(0).reason()==GenerationFailureReason.BUDGET_EXCEEDED,"finite capacity rejects, no eviction");
        check(index.snapshot().entries().size()==3,"partial retained at capacity");
        for(int budget:new int[]{0,1,2,3,100}) {
            var result=query(index.snapshot(),budget);
            check(result.operationsUsed()==Math.min(budget,3),"count visited incl dimension misses");
            check(result.completeness()==ClaimQueryCompleteness.UNKNOWN,"mirror never asserts world completeness");
            check(result.failures().stream().anyMatch(f->f.reason()==GenerationFailureReason.BUDGET_EXCEEDED)==(budget<3),"budget exhaustion");
        }
        var nether=new WorldgenIdentity(42,OTHER_DIM,COORDINATOR,"contract-v1",PROFILE.resourceSnapshot());
        check(index.snapshot().query(nether,AREA,new QueryBudget(3)).entries().size()==1,"dimension separation");
        var edgeArea=new BoundsXZ(-4,-3,0,2);
        check(index.snapshot().query(IDENTITY,edgeArea,new QueryBudget(3)).entries().size()==2,"margin reaches query without origin inside");
        var unknownScope=new WorldgenIdentity(43,DIM,COORDINATOR,"contract-v1",PROFILE.resourceSnapshot());
        check(index.snapshot().query(unknownScope,AREA,new QueryBudget(3)).failures().get(0).reason()==GenerationFailureReason.VERSION_MISMATCH,"world scope frozen");
        var list=new ArrayList<>(index.snapshot().entries());
        var copied=new ClaimIndexSnapshot(42,PROFILE,5,3,list); list.clear();
        check(copied.entries().size()==3,"snapshot defensive copy");
        rejects(UnsupportedOperationException.class,()->copied.entries().clear());
        rejects(UnsupportedOperationException.class,()->query(copied,3).entries().clear());
        rejects(IllegalArgumentException.class,()->new ClaimIndexSnapshot(42,PROFILE,0,3,List.of(before.entries().get(0),before.entries().get(0))));
        var exhausted=new LimitedClaimIndex(new ClaimIndexSnapshot(42,PROFILE,Long.MAX_VALUE,3,List.of()));
        rejects(ArithmeticException.class,()->put(exhausted,accepted));
        check(exhausted.snapshot().entries().isEmpty(),"revision overflow atomic before mutation");
    }

    private static ProviderActivationGate.Evidence evidence(ResourceLocation id) {
        var caps=new HashSet<>(REQUIRED.get(id));
        var descriptor=new ProviderDescriptor(id,PROFILE.systemVersions().get(id),caps,true,ClaimQueryCompleteness.COMPLETE,true);
        return new ProviderActivationGate.Evidence(descriptor,identity(id),AREA,new ClaimQueryResult(List.of(),ClaimQueryCompleteness.COMPLETE,List.of(),0));
    }
    private static List<ProviderActivationGate.Evidence> ready() { return List.of(evidence(SITE),evidence(HIGHWAY),evidence(PROTECTED)); }
    private static ProviderActivationGate.Result gate(List<ProviderActivationGate.Evidence> list) {
        return ProviderActivationGate.evaluate(Optional.of(PROFILE),PROFILE,IDENTITY,AREA,REQUIRED,list);
    }
    private static ProviderActivationGate.Evidence descriptor(ProviderActivationGate.Evidence old,ProviderDescriptor d) {
        return new ProviderActivationGate.Evidence(d,old.scope(),old.area(),old.query());
    }
    private static void gates() {
        check(gate(ready()).status()==READY,"all required contracts pass");
        check(index(2).snapshot().entries().isEmpty() && gate(ready()).status()==READY,"infinite highway needs no persisted corridor index");
        // Gate never rejects City presence alongside Highway. SOFT zoning policy is outside this gate.
        var city=new ResourceLocation("test:city"); var versions=new HashMap<>(PROFILE.systemVersions()); versions.put(city,"city-v1");
        var cityProfile=new WorldgenProfile(PROFILE.profileId(),1,versions,PROFILE.resourceSnapshot());
        var requiredCity=new HashMap<>(REQUIRED); requiredCity.put(city,Set.of(CLAIM_QUERY,DETERMINISTIC_CANDIDATES));
        var cityDescriptor=new ProviderDescriptor(city,"city-v1",requiredCity.get(city),true,ClaimQueryCompleteness.COMPLETE,false);
        var cityClaim=new SpatialClaim("city-soft-region",city,DIM,"city-v1",AREA,Optional.empty(),SpatialClaimType.SITE,SpatialClaimStrength.SOFT,10,0,List.of());
        var cityEvidence=new ProviderActivationGate.Evidence(cityDescriptor,new WorldgenIdentity(42,DIM,city,"city-v1",PROFILE.resourceSnapshot()),AREA,
                new ClaimQueryResult(List.of(cityClaim),ClaimQueryCompleteness.COMPLETE,List.of(),1));
        var withCity=new ArrayList<>(ready()); withCity.add(cityEvidence);
        check(ProviderActivationGate.evaluate(Optional.of(cityProfile),cityProfile,IDENTITY,AREA,requiredCity,withCity).status()==READY,"City SOFT region and Highway coexist at contract gate");
        var protectedEvidence=evidence(PROTECTED); var pd=protectedEvidence.descriptor();
        var unknown=new ProviderActivationGate.Evidence(pd,protectedEvidence.scope(),AREA,new ClaimQueryResult(List.of(),ClaimQueryCompleteness.UNKNOWN,List.of(),0));
        check(gate(List.of(evidence(SITE),evidence(HIGHWAY),unknown)).status()==UNKNOWN,"unknown protected source blocks activation");
        check(gate(List.of(evidence(SITE),evidence(HIGHWAY))).status()==MISSING_PROVIDER,"protected provider missing");
        check(ProviderActivationGate.evaluate(Optional.empty(),PROFILE,IDENTITY,AREA,REQUIRED,ready()).status()==LEGACY_UNSUPPORTED,"legacy lacks protection baseline");
        var noPolicy=descriptor(protectedEvidence,new ProviderDescriptor(PROTECTED,pd.generationVersion(),pd.capabilities(),true,ClaimQueryCompleteness.COMPLETE,false));
        check(gate(List.of(evidence(SITE),evidence(HIGHWAY),noPolicy)).status()==UNKNOWN,"complete sample not corridor-compatible policy proof");
        var noGuarantee=descriptor(protectedEvidence,new ProviderDescriptor(PROTECTED,pd.generationVersion(),pd.capabilities(),true,ClaimQueryCompleteness.UNKNOWN,true));
        check(gate(List.of(evidence(SITE),evidence(HIGHWAY),noGuarantee)).status()==UNKNOWN,"one complete result cannot replace complete capability guarantee");
        for(var d:List.of(new ProviderDescriptor(PROTECTED,"wrong-version",pd.capabilities(),true,ClaimQueryCompleteness.COMPLETE,true),
                new ProviderDescriptor(PROTECTED,pd.generationVersion(),pd.capabilities(),false,ClaimQueryCompleteness.COMPLETE,true),
                new ProviderDescriptor(PROTECTED,pd.generationVersion(),Set.of(CLAIM_QUERY),true,ClaimQueryCompleteness.COMPLETE,true)))
            check(gate(List.of(evidence(SITE),evidence(HIGHWAY),descriptor(protectedEvidence,d))).status()==INCOMPATIBLE,"version/determinism/capability incompatibility");
        for(var scope:List.of(new WorldgenIdentity(43,DIM,PROTECTED,pd.generationVersion(),PROFILE.resourceSnapshot()),
                new WorldgenIdentity(42,OTHER_DIM,PROTECTED,pd.generationVersion(),PROFILE.resourceSnapshot()),
                new WorldgenIdentity(42,DIM,PROTECTED,pd.generationVersion(),"changed"),identity(SITE))) {
            var wrong=new ProviderActivationGate.Evidence(pd,scope,AREA,protectedEvidence.query());
            check(gate(List.of(evidence(SITE),evidence(HIGHWAY),wrong)).status()==INCOMPATIBLE,"evidence must match full scope");
        }
        var narrow=new ProviderActivationGate.Evidence(pd,protectedEvidence.scope(),new BoundsXZ(0,0,1,1),protectedEvidence.query());
        check(gate(List.of(evidence(SITE),evidence(HIGHWAY),narrow)).status()==UNKNOWN,"coverage must include requested region");
        var outOfScope=new ProviderActivationGate.Evidence(pd,protectedEvidence.scope(),AREA,
                new ClaimQueryResult(List.of(entry("other",PARTIAL,1).claim()),ClaimQueryCompleteness.COMPLETE,List.of(),1));
        check(gate(List.of(evidence(SITE),evidence(HIGHWAY),outOfScope)).status()==INCOMPATIBLE,"complete claims cannot spoof another provider");
        var duplicate=new ArrayList<>(ready()); duplicate.add(unknown);
        check(gate(duplicate).status()==INCOMPATIBLE,"contradictory duplicate provider, not last-wins");
        duplicate=new ArrayList<>(ready()); duplicate.add(protectedEvidence);
        check(gate(duplicate).status()==READY,"identical provider evidence idempotent");
        check(ProviderActivationGate.evaluate(Optional.of(PROFILE),PROFILE,IDENTITY,AREA,Map.of(),List.of()).status()==INCOMPATIBLE,"empty required policy not vacuous READY");
        check(ProviderActivationGate.evaluate(Optional.of(PROFILE),PROFILE,IDENTITY,new BoundsXZ(0,0,0,1),REQUIRED,ready()).status()==UNKNOWN,"empty activation scope no proof");
        var caps=new HashSet<>(Set.of(CLAIM_QUERY)); var d=new ProviderDescriptor(SITE,"v",caps,true,ClaimQueryCompleteness.COMPLETE,false); caps.clear();
        check(d.capabilities().size()==1,"capability defensive copy");
        rejects(UnsupportedOperationException.class,()->d.capabilities().clear());
        rejects(UnsupportedOperationException.class,()->gate(List.of()).issues().clear());
    }

    private static void serialization() throws IOException {
        var index=index(10);
        int n=0;
        for(var stage:List.of(ACCEPTED_PLAN,COMMITTED,PARTIAL)) for(var type:List.of(SpatialClaimType.SITE,SpatialClaimType.BUILDING,SpatialClaimType.PROTECTED_SITE)) {
            var e=entry("id-"+n,stage,n,n%2==0?DIM:OTHER_DIM,n%2==0?Optional.empty():Optional.of(new YRange(-64,-10)),type);
            put(index,e); index.verify(e.claimId(),Optional.of(e)); n++;
        }
        var snapshot=index.snapshot(); var tag=ClaimIndexCodec.encode(snapshot);
        var bytes=new ByteArrayOutputStream();
        NbtIo.write(tag,new DataOutputStream(bytes));
        var read=NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),new NbtAccounter(4_000_000));
        var restored=decode(read);
        check(contents(restored).equals(contents(snapshot)),"all entry fields survive binary NBT roundtrip");
        check(restored.profile().equals(PROFILE) && restored.indexRevision()==snapshot.indexRevision() && restored.capacity()==10 && restored.worldSeed()==42,"profile/revision/world/capacity roundtrip");
        check(restored.entries().stream().allMatch(v->v.verification()==UNVERIFIED),"disk cannot assert fresh authoritative confirmation");
        check(new LimitedClaimIndex(restored).snapshot().equals(restored),"explicit inert restore roundtrip");
        check(ClaimIndexCodec.encode(restored).equals(tag),"canonical NBT re-encode");
        read.putInt("capacity",1); check(restored.capacity()==10,"decoded snapshot not backed by mutable NBT");
        check(ClaimIndexCodec.decodeProfile(ClaimIndexCodec.encodeProfile(PROFILE)).equals(PROFILE),"standalone profile roundtrip");
        // Every critical top-level / entry field must reject when removed, not silently default.
        for(String key:tag.getAllKeys()) { var bad=tag.copy(); bad.remove(key); rejects(IllegalArgumentException.class,()->decode(bad)); }
        for(String key:first(tag).getAllKeys()) { var bad=tag.copy(); first(bad).remove(key); rejects(IllegalArgumentException.class,()->decode(bad)); }
        for(String key:tag.getCompound("profile").getAllKeys()) { var bad=tag.copy(); bad.getCompound("profile").remove(key); rejects(IllegalArgumentException.class,()->decode(bad)); }
        List<Consumer<CompoundTag>> corrupt=List.of(t->t.putInt("schema",2),t->t.putString("schema","1"),
                t->t.putLong("index_revision",-1),t->t.putInt("capacity",1),t->t.putString("extra","unknown"),
                t->t.getCompound("profile").putInt("schema",2),t->t.getCompound("profile").putString("profile_id","LEGACY"),
                t->first(t).putIntArray("bounds_xz",new int[]{0,0,-1,2}), t->first(t).putIntArray("bounds_xz",new int[]{0,0,0,2}),
                t->first(t).putIntArray("bounds_xz",new int[]{0,1}),t->first(t).putString("bounds_xz","bad"),
                t->first(t).putIntArray("y_range",new int[]{1,1}),t->first(t).putIntArray("y_range",new int[]{2,1}),
                t->first(t).putString("y_range","bad"),t->first(t).putString("stage","FUTURE_STAGE"),
                t->first(t).putString("stage","CANDIDATE_RESERVED"),t->first(t).putString("type","UNKNOWN"),
                t->first(t).putString("strength","UNKNOWN"),t->first(t).putInt("priority",9999),
                t->first(t).putString("owner","NO NAMESPACE"),t->first(t).putString("dimension","overworld"),
                t->first(t).putString("generation_version","newest"),t->first(t).putLong("revision",-1),
                t->first(t).putInt("margin",-1),t->first(t).putInt("revision",1),t->first(t).putString("plan_digest"," "));
        for(var edit:corrupt) { var bad=tag.copy(); edit.accept(bad); rejects(IllegalArgumentException.class,()->decode(bad)); }
        var repeated=tag.copy(); ((ListTag)repeated.get("entries")).add(first(repeated).copy());
        rejects(IllegalArgumentException.class,()->decode(repeated));
        var repeatedSystem=tag.copy(); var systems=(ListTag)repeatedSystem.getCompound("profile").get("systems"); systems.add(systems.get(0).copy());
        rejects(IllegalArgumentException.class,()->decode(repeatedSystem));
        var wrongList=tag.copy(); var strings=new ListTag(); strings.add(StringTag.valueOf("not an entry")); wrongList.put("entries",strings);
        rejects(IllegalArgumentException.class,()->decode(wrongList));
        var tooMany=tag.copy(); var huge=new ListTag();
        for(int i=0;i<=LimitedClaimIndex.MAX_ENTRIES;i++) huge.add(new CompoundTag());
        tooMany.put("entries",huge); rejects(IllegalArgumentException.class,()->decode(tooMany));
        var tooManySystems=tag.copy(); var bigSystems=new ListTag();
        for(int i=0;i<129;i++) bigSystems.add(new CompoundTag());
        tooManySystems.getCompound("profile").put("systems",bigSystems); rejects(IllegalArgumentException.class,()->decode(tooManySystems));
        var tooManyEdges=tag.copy(); var edgeList=new ListTag();
        for(int i=0;i<1025;i++) edgeList.add(StringTag.valueOf("edge"));
        first(tooManyEdges).put("edges",edgeList); rejects(IllegalArgumentException.class,()->decode(tooManyEdges));
        var tooLong=tag.copy(); first(tooLong).putString("plan_digest","x".repeat(4097));
        rejects(IllegalArgumentException.class,()->decode(tooLong));
        // Absent Y is legal, but present malformed/empty Y is never silently dropped.
        var presentY=tag.copy(); first(presentY).putIntArray("y_range",new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE});
        check(contents(decode(presentY)).get(0).claim().yRange().orElseThrow().height()==4294967295L,"full signed Y domain roundtrip");
        rejects(IllegalArgumentException.class,()->ClaimIndexCodec.decode(tag,id->OTHER_DIM));
        // No captured tag can mutate a published snapshot, and no source authority is loaded by the codec.
        first(tag).putString("plan_digest","tampered"); check(!contents(snapshot).get(0).planDigest().equals("tampered"),"encode produces independent mutable tag");
    }

    private static void permutationsAndThreads() throws Exception {
        var events=List.of(entry("a",ACCEPTED_PLAN,1),entry("a",PARTIAL,2),entry("a",COMMITTED,3),
                entry("b",PARTIAL,1),entry("b",PARTIAL,1),entry("c",COMMITTED,2,OTHER_DIM,Optional.of(new YRange(-5,8)),SpatialClaimType.CONNECTION));
        var expected=List.of(events.get(2),events.get(3),events.get(5));
        var good=ready(); var mixed=new ArrayList<>(ready());
        mixed.add(new ProviderActivationGate.Evidence(evidence(PROTECTED).descriptor(),identity(PROTECTED),AREA,new ClaimQueryResult(List.of(),ClaimQueryCompleteness.UNKNOWN,List.of(),0)));
        for(int seed=0;seed<4;seed++) {
            var random=new Random(57_005L+seed*197L);
            for(int round=0;round<500;round++) {
                var shuffled=new ArrayList<>(events); Collections.shuffle(shuffled,random); var index=index(10);
                for(var event:shuffled) put(index,event);
                check(contents(index.snapshot()).equals(expected),"valid lifecycle event order converges without regression");
                var providers=new ArrayList<>(good); Collections.shuffle(providers,random);
                check(gate(providers).equals(gate(good)),"activation provider order independent");
                providers=new ArrayList<>(mixed); Collections.shuffle(providers,random);
                check(gate(providers).equals(gate(mixed)),"conflicting evidence order independent");
                var reversed=new ArrayList<>(index.snapshot().entries()); Collections.reverse(reversed);
                check(new ClaimIndexSnapshot(42,PROFILE,index.snapshot().indexRevision(),10,reversed).equals(index.snapshot()),"snapshot canonical input ordering");
            }
        }
        var index=index(10); for(var e:events) put(index,e); var snapshot=index.snapshot(); var baseline=query(snapshot,10);
        var executor=Executors.newFixedThreadPool(4);
        try {
            var tasks=new ArrayList<java.util.concurrent.Callable<ClaimIndexSnapshot.QueryResult>>();
            for(int i=0;i<128;i++) tasks.add(()->query(snapshot,10));
            var futures=executor.invokeAll(tasks); put(index,entry("new",PARTIAL,1));
            for(var future:futures) check(future.get().equals(baseline),"snapshot concurrent reads unaffected by owning-index updates");
        } finally { executor.shutdownNow(); }
        check(contents(snapshot).equals(expected),"captured worker snapshot frozen");
    }
    public static void main(String[] args) throws Exception {
        valuesAndProfiles(); indexAndVerification(); gates(); serialization(); permutationsAndThreads();
        System.out.println("PASS WG-05 index/profile/gate checks="+checks+"; 2000 lifecycle/provider shuffles; binary NBT roundtrip; no SavedData/world bootstrap/generator integration");
    }
}
