package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import static com.antaurora.apofirstlight.dev.authoring.bridge.BridgeJson.*;

final class WorldInspector {
    static final Map<String,String> LEGEND=Map.of("#","structural solid","G","glass","D","door","S","stair","A","air","W","wood/furniture","M","metal","?","other");
    static String category(BlockState s){
        String id=BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        if(s.isAir())return "A";if(id.contains("glass"))return "G";if(s.getBlock() instanceof DoorBlock)return "D";
        if(s.getBlock() instanceof StairBlock)return "S";
        if(id.matches(".*(wood|planks|log|fence|chest|bookshelf|slab).*$"))return "W";
        if(id.matches(".*(iron|steel|metal|copper).*$"))return "M";
        return s.isSolid()?"#":"?";
    }
    static JsonObject inspect(ServerLevel level,BridgeBounds b){
        b.check(level);Map<String,Integer> palette=new TreeMap<>();Set<String> types=new HashSet<>();List<JsonObject> densities=new ArrayList<>();List<Integer> floors=new ArrayList<>();
        int nonAir=0,be=0,minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE,lastBand=-10000;
        for(int y=b.min().getY();y<=b.max().getY();y++){
            int occupied=0,solid=0;
            for(int z=b.min().getZ();z<=b.max().getZ();z++)for(int x=b.min().getX();x<=b.max().getX();x++){
                var pos=new BlockPos(x,y,z);var s=level.getBlockState(pos);palette.merge(s.toString(),1,Integer::sum);types.add(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
                if(!s.isAir()){occupied++;nonAir++;minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
                if(s.isSolid())solid++;if(level.getBlockEntity(pos)!=null)be++;
            }
            double density=occupied/(double)(b.width()*b.depth()),sd=solid/(double)(b.width()*b.depth());
            densities.add(object("y",y,"occupied",occupied,"density",density,"solid_density",sd));
            if(sd>=.65){if(y>lastBand+1)floors.add(y);lastBand=y;}
        }
        List<Integer> gaps=new ArrayList<>();for(int i=1;i<floors.size();i++)gaps.add(floors.get(i)-floors.get(i-1));Collections.sort(gaps);
        var entries=new ArrayList<JsonObject>();palette.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(256).forEach(e->entries.add(object("state",e.getKey(),"count",e.getValue(),"percentage",100.0*e.getValue()/b.volume())));
        return object("bounds",b.json(),"non_air",nonAir,"air_ratio",1.0-nonAir/(double)b.volume(),"unique_block_count",types.size(),"palette_count",palette.size(),"palette",entries,"palette_truncated",palette.size()>256,"block_entities",be,"entities",level.getEntities(null,b.aabb()).size(),"min_occupied_y",nonAir==0?null:minY,"max_occupied_y",nonAir==0?null:maxY,"occupied_height",nonAir==0?0:maxY-minY+1,"horizontal_density",densities,"likely_floor_bands",floors,"estimated_floor_count",Math.max(0,floors.size()-1),"estimated_floor_height",gaps.isEmpty()?null:gaps.get(gaps.size()/2),"confidence",floors.size()>2?"LOW_HEURISTIC":"INSUFFICIENT","analysis_note","Solid-density >=65% bands; roofs/foundations may be counted. Confirm with slices.");
    }
    static JsonObject slice(ServerLevel level,BridgeBounds b,JsonObject a,boolean horizontal){
        b.check(level);String mode=string(a,"encoding","category");if(!Set.of("category","palette").contains(mode))throw new IllegalArgumentException("INVALID_ENCODING");
        int step=integer(a,"downsample",1);if(step<1||step>32)throw new IllegalArgumentException("INVALID_DOWNSAMPLE");
        String axis=horizontal?"Y":string(a,"axis","X");if(!Set.of("X","Y","Z").contains(axis))throw new IllegalArgumentException("INVALID_AXIS");
        int c=integer(a,"coordinate",horizontal?b.min().getY():b.min().getX());
        if(bool(a,"relative"))c+=axis.equals("X")?b.min().getX():axis.equals("Y")?b.min().getY():b.min().getZ();
        int lo=axis.equals("X")?b.min().getX():axis.equals("Y")?b.min().getY():b.min().getZ(),hi=axis.equals("X")?b.max().getX():axis.equals("Y")?b.max().getY():b.max().getZ();
        if(c<lo||c>hi)throw new IllegalArgumentException("SLICE_OUT_OF_BOUNDS");
        int w=axis.equals("X")?b.depth():b.width(),h=axis.equals("Y")?b.depth():b.height();
        if((long)((w+step-1)/step)*((h+step-1)/step)>4096)throw new IllegalArgumentException("SLICE_TOO_LARGE: crop or downsample");
        Map<String,Integer> palette=new LinkedHashMap<>();List<Object> rows=new ArrayList<>();
        for(int v=0;v<h;v+=step){var row=new ArrayList<Integer>();StringBuilder cats=new StringBuilder();for(int u=0;u<w;u+=step){
            var p=axis.equals("Y")?new BlockPos(b.min().getX()+u,c,b.min().getZ()+v):axis.equals("X")?new BlockPos(c,b.max().getY()-v,b.min().getZ()+u):new BlockPos(b.min().getX()+u,b.max().getY()-v,c);
            var state=level.getBlockState(p);String key=state.toString();palette.computeIfAbsent(key,k->palette.size());row.add(palette.get(key));cats.append(category(state));
        }rows.add(mode.equals("category")?cats.toString():row);}
        return object("bounds",b.json(),"axis",axis,"coordinate",c,"encoding",mode,"downsample",step,"sampling","nearest grid sample, not majority","row_order",axis.equals("Y")?"Z ascending; columns X ascending":"Y descending; columns remaining horizontal axis ascending","rows",rows,"legend",LEGEND,"palette",mode.equals("palette")?palette:Map.of());
    }
    static JsonObject facade(ServerLevel level,BridgeBounds b,JsonObject a){
        b.check(level);String side=string(a,"side","SOUTH");if(!Set.of("NORTH","SOUTH","EAST","WEST").contains(side))throw new IllegalArgumentException("INVALID_SIDE");
        int depth=integer(a,"depth",3),step=integer(a,"downsample",1);if(depth<1||depth>16||step<1||step>32)throw new IllegalArgumentException("INVALID_DEPTH_OR_STEP");
        boolean alongX=side.equals("NORTH")||side.equals("SOUTH");int w=alongX?b.width():b.depth(),h=b.height();
        if((long)((w+step-1)/step)*((h+step-1)/step)>4096)throw new IllegalArgumentException("FACADE_TOO_LARGE");
        var rows=new ArrayList<String>();var depths=new ArrayList<List<Integer>>();var pal=new HashMap<String,Integer>();var columns=new int[(w+step-1)/step];var bands=new ArrayList<Integer>();int glass=0,solid=0,total=0;
        for(int v=0;v<h;v+=step){StringBuilder row=new StringBuilder();var dr=new ArrayList<Integer>();int band=0;for(int u=0;u<w;u+=step){BlockState s=Blocks.AIR.defaultBlockState();int found=-1;
            for(int d=0;d<Math.min(depth,alongX?b.depth():b.width());d++){
                int x=alongX?b.min().getX()+u:side.equals("WEST")?b.min().getX()+d:b.max().getX()-d;
                int z=alongX?(side.equals("NORTH")?b.min().getZ()+d:b.max().getZ()-d):b.min().getZ()+u;
                s=level.getBlockState(new BlockPos(x,b.max().getY()-v,z));if(!s.isAir()){found=d;break;}
            }String cat=category(s);row.append(cat);dr.add(found);total++;if(cat.equals("G"))glass++;if(s.isSolid()){solid++;band++;columns[u/step]++;}pal.merge(s.toString(),1,Integer::sum);
        }rows.add(row.toString());depths.add(dr);bands.add(band);}
        var dominant=pal.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(32).map(e->object("state",e.getKey(),"samples",e.getValue())).toList();
        return object("side",side,"bounds",b.json(),"width",w,"height",h,"downsample",step,"rows",rows,"legend",LEGEND,"window_percentage",100.0*glass/total,"solid_percentage",100.0*solid/total,"dominant_palette",dominant,"vertical_solid_sample_bands",columns,"horizontal_solid_sample_bands",bands,"first_occupied_depth",depths,"orientation","Y descending; X/Z ascending, no perspective mirroring; -1 = no occupied cell in search depth");
    }
}
