package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.google.gson.*;
import net.minecraft.core.BlockPos;

final class BridgeJson {
    static final Gson GSON=new GsonBuilder().disableHtmlEscaping().create();
    static JsonObject object(Object... values){var o=new JsonObject();for(int i=0;i<values.length;i+=2)o.add((String)values[i],GSON.toJsonTree(values[i+1]));return o;}
    static String string(JsonObject o,String key,String fallback){return o.has(key)?o.get(key).getAsString():fallback;}
    static int integer(JsonObject o,String key,int fallback){return o.has(key)?o.get(key).getAsBigDecimal().intValueExact():fallback;}
    static boolean bool(JsonObject o,String key){return o.has(key)&&o.get(key).getAsBoolean();}
    static BlockPos pos(JsonObject o,String key){var a=o.getAsJsonArray(key);if(a==null||a.size()!=3)throw new IllegalArgumentException("Expected integer [x,y,z]: "+key);return new BlockPos(a.get(0).getAsBigDecimal().intValueExact(),a.get(1).getAsBigDecimal().intValueExact(),a.get(2).getAsBigDecimal().intValueExact());}
    static int[] xyz(BlockPos p){return new int[]{p.getX(),p.getY(),p.getZ()};}
}
