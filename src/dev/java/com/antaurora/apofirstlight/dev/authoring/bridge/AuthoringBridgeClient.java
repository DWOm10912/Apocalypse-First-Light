package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.antaurora.apofirstlight.authoring.BuildingAuthoringConfig;
import com.google.gson.*;
import com.sun.net.httpserver.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.antaurora.apofirstlight.dev.authoring.bridge.BridgeJson.*;

/** Entire class is dev-only and client-only. HTTP threads never touch game state. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class AuthoringBridgeClient {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static AuthoringBridgeClient active;
    private static long nextConfig;
    private static boolean enabled;
    private static int configuredPort;
    private final IntegratedServer server;
    private final UUID player;
    private final String epoch=UUID.randomUUID().toString(),token;
    private final HttpServer http;
    private final ExecutorService network=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"AFL-authoring-http");t.setDaemon(true);return t;});
    private final BridgeRouter router=new BridgeRouter();
    private final LinkedHashMap<String,JsonObject> completed=new LinkedHashMap<>();
    private final Path discovery=FMLPaths.GAMEDIR.get().resolve("afl_authoring_bridge/session.json");
    private volatile boolean closed;
    private volatile JsonObject visual=new JsonObject();
    private AuthoringBridgeClient(Minecraft mc) throws Exception {
        server=mc.getSingleplayerServer();player=mc.player.getUUID();byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);token=HexFormat.of().formatHex(bytes);
        http=HttpServer.create(new InetSocketAddress("127.0.0.1",configuredPort),8);http.setExecutor(network);http.createContext("/call",this::request);http.start();
        Files.createDirectories(discovery.getParent());var temp=discovery.resolveSibling("session.tmp");
        Files.writeString(temp,GSON.toJson(object("host","127.0.0.1","port",http.getAddress().getPort(),"token",token,"world_session",epoch,"world",server.getWorldData().getLevelName(),"ready",true)));
        Files.move(temp,discovery,StandardCopyOption.REPLACE_EXISTING);LOG.info("AFL authoring bridge ready on loopback port {} (token not logged)",http.getAddress().getPort());
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END||FMLEnvironment.production)return;var mc=Minecraft.getInstance();
        if(System.currentTimeMillis()>=nextConfig){nextConfig=System.currentTimeMillis()+1000;try{
            var file=FMLPaths.CONFIGDIR.get().resolve("apocalypse_firstlight-authoring-bridge.properties");
            if(!Files.exists(file))Files.writeString(file,"# Development runClient only. Restart world after changing port.\nauthoringAgentBridgeEnabled=false\nauthoringAgentBridgePort=0\n");
            Properties props=new Properties();try(var reader=Files.newBufferedReader(file)){props.load(reader);}enabled=Boolean.parseBoolean(props.getProperty("authoringAgentBridgeEnabled","false"));configuredPort=Integer.parseInt(props.getProperty("authoringAgentBridgePort","0"));if(configuredPort<0||configuredPort>65535)enabled=false;
        }catch(Exception ex){enabled=false;LOG.warn("Cannot read authoring bridge config: {}",ex.getMessage());}}
        boolean ready=enabled&&mc.player!=null&&mc.level!=null&&mc.getSingleplayerServer()!=null&&!mc.getSingleplayerServer().isPublished();
        if(active!=null&&(!ready||active.server!=mc.getSingleplayerServer()||!active.player.equals(mc.player.getUUID()))){active.stop();active=null;}
        if(active==null&&ready)try{active=new AuthoringBridgeClient(mc);}catch(Exception ex){enabled=false;nextConfig=System.currentTimeMillis()+10000;LOG.error("Cannot start authoring bridge: {}",ex.getMessage());}
        if(active!=null)active.visual=object("screen_width",mc.getWindow().getWidth(),"screen_height",mc.getWindow().getHeight(),"camera_type",mc.options.getCameraType().name(),"shader_state","UNKNOWN_NOT_QUERIED","screen_open",mc.screen!=null);
    }
    private void stop(){closed=true;http.stop(0);network.shutdownNow();try{Files.deleteIfExists(discovery);}catch(Exception e){LOG.warn("Could not remove stale bridge discovery");}}
    private void request(HttpExchange x){
        try {
            if(!x.getRequestMethod().equals("POST")||!x.getRequestURI().getPath().equals("/call")||x.getRequestHeaders().containsKey("Origin"))throw new IllegalArgumentException("REQUEST_REJECTED");
            String auth=x.getRequestHeaders().getFirst("Authorization");
            if(auth==null||!MessageDigest.isEqual(auth.getBytes(StandardCharsets.UTF_8),("Bearer "+token).getBytes(StandardCharsets.UTF_8))) {reply(x,401,object("error","UNAUTHORIZED"));return;}
            byte[] bytes=x.getRequestBody().readNBytes(65537);if(bytes.length>65536)throw new IllegalArgumentException("REQUEST_TOO_LARGE");
            var req=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();String tool=string(req,"tool","");
            if(closed||(!tool.equals("minecraft_status")&&!epoch.equals(string(req,"world_session",""))))throw new IllegalArgumentException("WORLD_SESSION_CHANGED: call minecraft_status before continuing");
            String id=string(req,"request_id","");UUID.fromString(id);if(completed.containsKey(id)){reply(x,200,completed.get(id));return;}
            var a=req.has("arguments")?req.getAsJsonObject("arguments"):new JsonObject();var future=new CompletableFuture<JsonObject>();var abandoned=new AtomicBoolean();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            Runnable job=()->{
                if(abandoned.get()||System.nanoTime()>deadline){future.complete(object("error","REQUEST_EXPIRED_NO_EXECUTION"));return;}
                try{
                    if(closed)throw new IllegalArgumentException("WORLD_SESSION_CHANGED");
                    JsonObject result;
                    if(tool.equals("capture_current_view")){
                        var level=Minecraft.getInstance().level;if(level==null||!level.dimension().location().toString().equals(string(req,"dimension","")))throw new IllegalArgumentException("DIMENSION_MISMATCH");result=capture();
                    }
                    else {
                        var p=server.getPlayerList().getPlayer(player);if(p==null)throw new IllegalArgumentException("PLAYER_NOT_READY");
                        if(!tool.equals("minecraft_status")&&!p.serverLevel().dimension().location().toString().equals(string(req,"dimension","")))throw new IllegalArgumentException("DIMENSION_MISMATCH: refresh minecraft_status");
                        if(tool.equals("minecraft_status")){
                            result=object("bridge_connected",true,"world_loaded",true,"minecraft_version",net.minecraft.SharedConstants.getCurrentVersion().getName(),"forge_version",net.minecraftforge.versions.forge.ForgeVersion.getVersion(),"afl_version",net.minecraftforge.fml.ModList.get().getModContainerById("apocalypse_firstlight").orElseThrow().getModInfo().getVersion().toString(),"world",server.getWorldData().getLevelName(),"world_session",epoch,"dimension",p.serverLevel().dimension().location().toString(),"player_position",new double[]{p.getX(),p.getY(),p.getZ()},"game_mode",p.gameMode.getGameModeForPlayer().getName(),"worldedit_detected",BridgeRouter.hasWorldEdit(),"worldedit_version",net.minecraftforge.fml.ModList.get().getModContainerById("worldedit").map(m->m.getModInfo().getVersion().toString()).orElse("ABSENT"),"authoring_enabled",BuildingAuthoringConfig.ENABLED.get(),"visual",visual.deepCopy());
                            try{result.add("active_authoring_session",AuthoringAdapter.info(p));}catch(Exception ignored){result.add("active_authoring_session",JsonNull.INSTANCE);}
                        }else result=router.call(tool,a,p);
                        if(tool.equals("get_player_state"))result.add("visual",visual.deepCopy());
                    }
                    future.complete(result);
                }catch(Exception ex){future.complete(object("error",ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage()));}
            };
            if(tool.equals("capture_current_view"))Minecraft.getInstance().execute(job);else server.execute(job);
            JsonObject result;
            try{result=future.get(20,TimeUnit.SECONDS);}catch(TimeoutException ex){abandoned.set(true);result=object("error","TIMEOUT_OUTCOME_UNKNOWN_DO_NOT_RETRY_WRITE: inspect world before another edit");}
            completed.put(id,result);if(completed.size()>128)completed.remove(completed.keySet().iterator().next());reply(x,200,result);
        }catch(Exception ex){try{reply(x,400,object("error",ex.getMessage()==null?"INVALID_REQUEST":ex.getMessage()));}catch(Exception ignored){x.close();}}
    }
    private JsonObject capture() throws Exception {
        var mc=Minecraft.getInstance();if(mc.getSingleplayerServer()!=server||mc.level==null||closed)throw new IllegalArgumentException("WORLD_SESSION_CHANGED");
        var dir=FMLPaths.GAMEDIR.get().resolve("afl_authoring_captures").resolve(epoch);Files.createDirectories(dir);var file=dir.resolve(UUID.randomUUID()+".png");
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(file);return object("path",file.toAbsolutePath().toString(),"width",image.getWidth(),"height",image.getHeight(),"source","minecraft_framebuffer","world_session",epoch);}
    }
    private static void reply(HttpExchange x,int status,JsonObject result) throws Exception {byte[] body=GSON.toJson(result).getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status,body.length);try(var out=x.getResponseBody()){out.write(body);}finally{x.close();}}
}
