package com.antaurora.apofirstlight.dev;

import com.google.gson.*;
import java.util.*;

/** Phase-0 descriptive statistics, not terrain-generation or road acceptance thresholds. */
final class TerrainDiagnosticMetrics {
    private TerrainDiagnosticMetrics() {}
    static JsonObject stats(Collection<? extends Number> input) {
        double[] a=input.stream().mapToDouble(Number::doubleValue).filter(Double::isFinite).sorted().toArray();
        JsonObject j=new JsonObject();j.addProperty("count",a.length);
        if(a.length==0){j.addProperty("status","NO_SAMPLES");return j;}
        double mean=Arrays.stream(a).average().orElseThrow(),ss=0;
        for(double v:a)ss+=(v-mean)*(v-mean);
        j.addProperty("min",a[0]);j.addProperty("max",a[a.length-1]);j.addProperty("range",a[a.length-1]-a[0]);
        j.addProperty("mean",mean);j.addProperty("standard_deviation",Math.sqrt(ss/a.length));
        for(int p:new int[]{10,25,50,75,90})j.addProperty(p==50?"median":"p"+p,quantile(a,p/100.0));
        return j;
    }
    private static double quantile(double[] a,double p) {
        double i=(a.length-1)*p;int lo=(int)i;return a[lo]+(a[Math.min(lo+1,a.length-1)]-a[lo])*(i-lo);
    }
    static double grade(double a,double b,double distance) {return Math.abs(b-a)/distance;}
    static String slopeClass(double grade) {return grade<=1.0/16?"flat":grade<=.125?"gentle":grade<=.25?"moderate":"steep";}
    static JsonObject distribution(Map<String,Integer> counts,int denominator) {
        JsonObject j=new JsonObject();j.addProperty("denominator",denominator);
        counts.forEach((k,v)->{JsonObject x=new JsonObject();x.addProperty("count",v);
            if(denominator>0)x.addProperty("percent",100.0*v/denominator);j.add(k,x);});return j;
    }
    static JsonObject profile(List<Integer> values,int spacing) {
        List<Integer> valid=values.stream().filter(Objects::nonNull).toList();JsonObject j=stats(valid);
        j.add("height_samples",TerrainDiagnosticIO.GSON.toJsonTree(values));j.addProperty("spacing",spacing);
        j.addProperty("complete",valid.size()==values.size());
        if(valid.size()!=values.size()||valid.size()<2)return j;
        for(int span:new int[]{1,4,8,16}) {
            double max=0;int pairs=0;
            for(int i=span;i<values.size();i++){max=Math.max(max,Math.abs(values.get(i)-values.get(i-span)));pairs++;}
            if(pairs>0)j.addProperty("max_"+(span*spacing)+"_block_rise",max);
        }
        // Residual RMS after least-squares linear detrending. Uniform slopes have zero roughness.
        int n=values.size();double xm=(n-1)*spacing/2.0,ym=valid.stream().mapToInt(Integer::intValue).average().orElseThrow();
        double cov=0,xx=0;
        for(int i=0;i<n;i++){double dx=i*spacing-xm;cov+=dx*(values.get(i)-ym);xx+=dx*dx;}
        double slope=cov/xx,ss=0,maxGrade=0;
        for(int i=0;i<n;i++){double r=values.get(i)-ym-slope*(i*spacing-xm);ss+=r*r;
            if(i>0)maxGrade=Math.max(maxGrade,grade(values.get(i-1),values.get(i),spacing));}
        j.addProperty("detrended_rms_roughness",Math.sqrt(ss/n));j.addProperty("max_adjacent_grade",maxGrade);
        j.addProperty("fitted_signed_grade",slope);return j;
    }
}
