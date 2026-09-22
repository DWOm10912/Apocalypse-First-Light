package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.block.SteelCableBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Immutable endpoint-driven cable plan. No neighbor reads or world references. */
public record BridgeCableGeometry(List<Cable> cables,List<String> rejected) {
    public record Piece(BlockPos pos,net.minecraft.world.level.block.state.BlockState state,Vec3 entry,Vec3 exit) {}
    public record Cable(int pylon,int side,boolean mainSpan,int anchorIndex,int run,
                        Vec3 tower,Vec3 deck,List<Piece> placements) {}
    public static BridgeCableGeometry empty() { return new BridgeCableGeometry(List.of(),List.of()); }
    public static BridgeCableGeometry plan(SeaBridgeGeometry bridge,HighwayProfile profile,BridgePylonGeometry tower) {
        if(!tower.enabled())return empty();
        var result=new ArrayList<Cable>(); var rejected=new ArrayList<String>();
        var occupied=new HashSet<BlockPos>();
        tower.placements().forEach(p->occupied.add(p.pos()));
        for(int pylon=0;pylon<2;pylon++)for(int face:new int[]{-1,1}) {
            boolean main=face==(pylon==0?1:-1);
            int p=tower.span().pylons().get(pylon), base=tower.baseYs().get(pylon);
            double previousTop=Double.NEGATIVE_INFINITY;
            for(int index:main?new int[]{0,2,3,4,6,8}:new int[]{0,3,5,8}) {
                int ti=pylon, fi=face, ix=index;
                var ta=tower.anchors().stream().filter(a->a.kind().equals("TOWER") && a.pylon()==ti
                        && a.side()==1 && a.facingAlong()==fi && a.index()==ix).findFirst().orElseThrow();
                var da=tower.anchors().stream().filter(a->a.kind().equals("DECK") && a.pylon()==ti
                        && a.side()==1 && a.facingAlong()==fi && a.index()==ix).findFirst().orElseThrow();
                int distance=Math.abs(da.socket().getX()-ta.socket().getX())+Math.abs(da.socket().getZ()-ta.socket().getZ())+1;
                int bottom=da.socket().getY(), chosen=0; double score=Double.POSITIVE_INFINITY;
                for(int k=1;k<=3;k++) {
                    double top=bottom+distance/(double)k;
                    double band=LandmarkMainSpan.ANCHOR_HEIGHTS.stream().mapToDouble(h->Math.abs(base+h-top)).min().orElse(100);
                    if(top<previousTop || top>base+64 || band>3.0)continue;
                    double cost=Math.abs(top-ta.socket().getY());
                    if(cost<score) { score=cost;chosen=k; }
                }
                if(chosen==0) { rejected.add(pylon+":"+face+":"+index+":NO_LEGAL_ANCHOR_BAND");continue; }
                var pair=new ArrayList<Cable>(); String reason=null;
                var pairPositions=new HashSet<BlockPos>();
                for(int side:new int[]{-1,1}) {
                    var pieces=new ArrayList<Piece>(); Vec3 last=null;
                    int deckStation=p+face*(24+12*index);
                    var uphill=Direction.fromDelta((int)-bridge.plan().tangent(0).x()*face,0,(int)-bridge.plan().tangent(0).z()*face);
                    for(int n=0;n<distance;n++) {
                        int y=bottom+n/chosen, phase=n%chosen;
                        var pos=LandmarkMainSpan.position(bridge.plan(),deckStation-face*n,side*13,y);
                        var step=new Vec3(uphill.getStepX(),0,uphill.getStepZ());
                        // Rigid sub-block inset matches baked model and shape; never replans the cable.
                        var center=Vec3.atLowerCornerOf(pos).add(.5,-SteelCableBlock.SLOPED_DROP,.5)
                                .add(step.scale(SteelCableBlock.SLOPED_INSET));
                        var entry=center.subtract(step.scale(.5)).add(0,phase/(double)chosen,0);
                        var exit=center.add(step.scale(.5)).add(0,(phase+1)/(double)chosen,0);
                        if(last!=null && last.distanceToSqr(entry)>1e-16)reason="ENDPOINT_DISCONTINUITY";
                        if(!bridge.bounds().contains(pos.getX(),pos.getZ()) || y< -64 || y+2>=320)reason="BOUNDS";
                        if(occupied.contains(pos) || !pairPositions.add(pos))reason="STRUCTURE_OR_CABLE_COLLISION";
                        if(y<profile.sampleAt(deckStation-face*n).roadY()+1)reason="DECK_COLLISION";
                        var state=AflBlocks.STEEL_CABLE.get().defaultBlockState().setValue(SteelCableBlock.FACING,uphill)
                                .setValue(SteelCableBlock.SEGMENT,SteelCableBlock.Segment.of(chosen,phase));
                        pieces.add(new Piece(pos,state,entry,exit));last=exit;
                    }
                    pair.add(new Cable(pylon,side,main,index,chosen,last,pieces.get(0).entry(),List.copyOf(pieces)));
                }
                if(reason!=null) { rejected.add(pylon+":"+face+":"+index+":"+reason);continue; }
                occupied.addAll(pairPositions);result.addAll(pair);previousTop=bottom+distance/(double)chosen;
            }
        }
        return new BridgeCableGeometry(List.copyOf(result),List.copyOf(rejected));
    }
    public void render(HighwayBlockWriter writer) {
        for(var c:cables)for(var p:c.placements())if(writer.owns(p.pos()))writer.set(p.pos(),p.state());
    }
    public String description() {
        var slopes=new TreeMap<Integer,Integer>();
        var positions=cables.stream().flatMap(c->c.placements().stream()).map(Piece::pos).toList();
        for(var c:cables)slopes.merge(c.run(),1,Integer::sum);
        var box=net.minecraft.world.level.levelgen.structure.BoundingBox.encapsulatingPositions(positions);
        return " cableEnabled="+!cables.isEmpty()+" cableCount="+cables.size()+" cableBlockCount="+positions.size()
                +" cableSlopeDistribution="+slopes+" rejectedCableCount="+(2*rejected.size())
                +" rejectedCableReason="+rejected+" cableBounds="+box+" cableContinuity=ENDPOINT_CONTRACT";
    }
}
