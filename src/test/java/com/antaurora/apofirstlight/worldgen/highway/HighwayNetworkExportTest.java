package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.dev.HighwayNetworkExport;
import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import java.awt.image.BufferedImage;
import java.util.*;

/** Checks the real text serializer and in-memory overlay without a world or screenshot. */
public final class HighwayNetworkExportTest {
    private static int checks;
    public static void main(String[] args) {
        for (long seed : new long[]{0, 2, 42}) {
            var macro = MacroGeography.forSeed(seed);
            var graph = HighwayRouteGraph.build(seed);
            String text = HighwayNetworkExport.report(graph, macro);
            require(text.equals(HighwayNetworkExport.report(HighwayRouteGraph.build(seed), macro)), "stable same seed");
            require(text.contains("National Trunks = 2"), "two trunks");
            require(text.contains("Strategic Branches = " + graph.getStrategicBranches().size()), "branch count");
            require(text.contains("Highway Nodes = " + graph.nodes().size()), "node count");
            require(text.contains("Sea Crossing Reservations = " + graph.seaCrossings().size()), "reservation count");
            require(text.contains("CONNECTED SATELLITES\n") && text.contains("UNCONNECTED SATELLITES\n"), "both headings");
            var routes = section(text,"ROUTES\n","EDGES\n");
            var edges = section(text,"EDGES\n","NODES\n");
            var nodes = section(text,"NODES\n","SEA CROSSING RESERVATIONS (PLANNED ONLY)\n");
            var crossings = section(text,"SEA CROSSING RESERVATIONS (PLANNED ONLY)\n","CONNECTED SATELLITES\n");
            require(values(routes,"routeId = ").size()==graph.routes().size(),"route count");
            require(values(edges,"edgeId = ").size()==graph.edges().size(),"edge count");
            require(values(nodes,"nodeId = ").size()==graph.nodes().size(),"node IDs unique");
            require(values(crossings,"crossingId = ").size()==graph.seaCrossings().size(),"crossing IDs unique");
            for(var edge:graph.edges()) {
                require(edges.contains("edgeId = "+edge.id()),"edge identity");
                if(edge.geometry()!=null) {
                    require(edges.contains("controlPointsXZ = "+edge.geometry().points()),"real polyline points");
                    require(edges.contains("parentAttachment = "+edge.parentAttachment().orElse(null)),"parent preserved");
                }
            }
            for(var c:graph.seaCrossings()) {
                require(crossings.contains("crossingId = "+c.id())
                        && crossings.contains(c.mainland().id()+" @ "+c.mainland().x()+", "+c.mainland().z())
                        && crossings.contains(c.satellite().id()+" @ "+c.satellite().x()+", "+c.satellite().z()),
                        "bridgehead coordinates");
            }
            BufferedImage image = new BufferedImage(751,751,BufferedImage.TYPE_INT_RGB);
            HighwayNetworkExport.overlay(image,graph,macro,12000,32);
            int px=(int)Math.round((graph.intersection().x()+12000)/32.0);
            int pz=(int)Math.round((graph.intersection().z()+12000)/32.0);
            require(image.getRGB(px,pz)!=0xFF000000,"overlay draws the national intersection without a saved image");
        }
        var macro = MacroGeography.forSeed(42);
        var empty = HighwayRouteGraph.buildTrunks(42);
        String noBranches = HighwayNetworkExport.report(empty,macro);
        require(noBranches.contains("Strategic Branches = 0")
                && noBranches.contains("Unconnected Satellites = "+macro.islands().size()),"no branch fallback");
        require(section(noBranches,"UNCONNECTED SATELLITES\n",null).split("status = UNCONNECTED",-1).length-1
                ==macro.islands().size(),"every missing island has explicit status");
        require(noBranches.contains("failureReason = ROUTING_STATUS_UNKNOWN"),"unknown reason not invented");
        System.out.println("HighwayNetworkExportTest PASS: "+checks+" checks; no world or files");
    }
    private static String section(String text,String start,String end) {
        int a=text.indexOf(start);if(a<0)throw new AssertionError("section "+start);
        int b=end==null?text.length():text.indexOf(end,a+start.length());
        if(b<0)throw new AssertionError("section "+end);
        return text.substring(a+start.length(),b);
    }
    private static Set<String> values(String section,String prefix) {
        Set<String> ids=new HashSet<>();int count=0;
        for(String line:section.split("\n"))if(line.startsWith(prefix)) {
            count++;if(!ids.add(line.substring(prefix.length())))throw new AssertionError("duplicate "+line);
        }
        if(count!=ids.size())throw new AssertionError("count");
        return ids;
    }
    private static void require(boolean ok,String label) { if(!ok)throw new AssertionError(label);checks++; }
}
