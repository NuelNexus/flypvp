package dev.flycolony.bridge;



import com.google.gson.*;

import com.sun.net.httpserver.*;

import java.net.*;

import java.nio.charset.StandardCharsets;

import java.nio.file.*;

import java.security.*;

import java.util.*;

import java.util.concurrent.*;

import java.util.concurrent.locks.LockSupport;

import net.fabricmc.api.ClientModInitializer;

import net.fabricmc.fabric.api.networking.v1.*;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;

import net.minecraft.client.util.ScreenshotRecorder;

import net.minecraft.server.MinecraftServer;

import net.minecraft.server.network.ServerPlayerEntity;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;



public final class FlyBridge implements ClientModInitializer {

 public static FlyBridge INSTANCE=new FlyBridge();

 private static final Logger LOG=LoggerFactory.getLogger("flybridge");

 private final HumanInputs humanInputs=new HumanInputs();private final BoundedTurn cameraTurn=new BoundedTurn();private final StepGate gate=new StepGate();private final PlayerControls controls=new PlayerControls();

 private HttpServer http;private ExecutorService executor;private final String token=UUID.randomUUID().toString()+UUID.randomUUID();

 private volatile boolean liveCursorLocked,liveWindowFocused;private volatile float liveBreakingProgress;private volatile boolean realtime;private volatile long liveServerTicks,lastCommandNs,lastRenderNs,renderFrames;private volatile StepRequest heldRequest;private volatile boolean liveScreenOpen;private volatile String liveScreenType="none";
 private volatile Pending pending;private volatile JsonObject latest;private volatile JsonObject lastResult;private volatile long serverFence,clientFence,clientTicks;private volatile String stopReason="Not started";private volatile String sessionId="";private volatile long completedSteps;private boolean tickInFlight;private final TickCommand<Pending> tickCommand=new TickCommand<>();private float oldServerRate;private int oldFps;private boolean oldVsync;private boolean oldPauseOnLostFocus;private long startedNs;

 private volatile MinecraftServer controlledServer;private volatile UUID playerId;private volatile CompletableFuture<byte[]> capture;private volatile CompletableFuture<JsonObject> vision;
 private volatile CompletableFuture<JsonObject> perception;

 private record Pending(StepRequest request,CompletableFuture<JsonObject> future,long started,long firstTick,int[] remaining,String[] error){}

 @Override public void onInitializeClient(){INSTANCE=this;

  PayloadTypeRegistry.playC2S().register(FencePayload.ID,FencePayload.CODEC);PayloadTypeRegistry.playS2C().register(FencePayload.ID,FencePayload.CODEC);

  ServerPlayNetworking.registerGlobalReceiver(FencePayload.ID,(p,c)->{if(p.session().equals(sessionId)&&c.server()==controlledServer&&c.player().getUuid().equals(playerId))serverFence=Math.max(serverFence,p.sequence());});

  ClientPlayNetworking.registerGlobalReceiver(FencePayload.ID,(p,c)->{if(p.session().equals(sessionId))clientFence=Math.max(clientFence,p.sequence());});

  HudRenderCallback.EVENT.register((context,tick)->{if(active()&&Boolean.getBoolean("flybridge.debugHud"))context.drawText(MinecraftClient.getInstance().textRenderer,"FLY COLONY Ã‚Â· "+gate.completed()+" paired ticks Ã‚Â· F8 releases control",8,8,0xFFCCFF99,true);});

  try{int port=Integer.getInteger("flybridge.port",47831);if(port<1024||port>65535)throw new IllegalArgumentException("Invalid bridge port");http=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);executor=Executors.newFixedThreadPool(3,r->{Thread t=new Thread(r,"FlyBridge HTTP");t.setDaemon(true);return t;});http.setExecutor(executor);http.createContext("/",this::request);http.start();

   var c=MinecraftClient.getInstance();Path descriptor=c.runDirectory.toPath().resolve("config/flybridge-session.json");Files.createDirectories(descriptor.getParent());JsonObject j=new JsonObject();j.addProperty("schema","flybridge-session-0.2");j.addProperty("baseUrl","http://127.0.0.1:"+port);j.addProperty("token",token);j.addProperty("minecraft","1.21.4");Files.writeString(descriptor,j.toString());LOG.info("FlyBridge ready on loopback port {}. Session descriptor: {}",port,descriptor);

  }catch(Exception e){LOG.error("FlyBridge could not start",e);}

 }

 public boolean active(){return realtime||gate.active();}public boolean pairedActive(){return gate.active()&&!realtime;}private long serverTicks(){return realtime?liveServerTicks:gate.completed();}public boolean hasWork(){return active()&&pending!=null;}

 private JsonObject status(){JsonObject j=new JsonObject();j.addProperty("schema","flybridge-status-0.2");j.addProperty("minecraft","1.21.4");j.addProperty("active",active());j.addProperty("sessionId",sessionId);j.addProperty("completedSteps",completedSteps);j.addProperty("serverTicks",serverTicks());j.addProperty("clockMode",realtime?"realtime":"paired");j.addProperty("renderFrames",renderFrames);j.addProperty("clientTicks",clientTicks);j.addProperty("stopReason",stopReason);j.addProperty("runtimeValidated",false);j.addProperty("controlVersion",6);j.addProperty("schoolVersion",1);j.addProperty("modVersion",net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("flybridge").orElseThrow().getMetadata().getVersion().getFriendlyString());j.addProperty("farmVersion",1);j.addProperty("homesteadVersion",1);j.addProperty("seedFarmVersion",1);j.addProperty("woodVersion",1);j.addProperty("seedVersion",1);j.addProperty("craftVersion",1);j.addProperty("equipmentVersion",1);j.addProperty("siteVersion",1);j.addProperty("pipelineVersion",2);j.addProperty("miningTargetVersion",1);j.addProperty("worldSceneVersion",1);j.addProperty("physicsVersion",1);j.addProperty("perceptionVersion",1);j.addProperty("visionVersion",1);j.addProperty("humanDemoVersion",1);j.addProperty("arenaVersion",1);j.addProperty("cursorLocked",liveCursorLocked);j.addProperty("windowFocused",liveWindowFocused);j.addProperty("timing",realtime?"native 20 TPS; independent render clock; asynchronous observations":"paired client/server ticks with ordered network fences; in-game validation pending");return j;}

 private void request(HttpExchange x){try{

  if(!x.getRemoteAddress().getAddress().isLoopbackAddress()){reply(x,403,error("Loopback only"));return;}

  String supplied=x.getRequestHeaders().getFirst("Authorization");if(supplied==null||!MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8),("Bearer "+token).getBytes(StandardCharsets.UTF_8))){reply(x,401,error("Session token required"));return;}

  if(x.getRequestHeaders().containsKey("Origin")){reply(x,403,error("Browser cross-origin control is disabled"));return;}

  String path=x.getRequestURI().getPath(),method=x.getRequestMethod();

  if(path.equals("/status")&&method.equals("GET")){reply(x,200,status());return;}

  if(path.equals("/senses")&&method.equals("GET")){
   var future=onClient(()->{var c=MinecraftClient.getInstance();var server=c.getServer();
    if(server==null||c.player==null||!c.isInSingleplayer())throw new IllegalStateException("Open the single-player world for a sensor survey");
    var id=c.player.getUuid();var view=new JsonObject();ClientObservation.overlay(c,view);float progress=c.interactionManager==null?0:((dev.flycolony.bridge.mixin.InteractionAccess)c.interactionManager).flyBreakingProgress();
    return server.submit(()->{var player=server.getPlayerManager().getPlayer(id);if(player==null)throw new IllegalStateException("Player unavailable");var result=WorldScene.capture(player);result.addProperty("breakingProgress",progress);var pose=result.getAsJsonObject("player");pose.add("position",view.get("position"));pose.add("eye",view.get("eye"));pose.addProperty("yaw",Math.toDegrees(view.get("yaw").getAsDouble()));pose.addProperty("pitch",Math.toDegrees(view.get("pitch").getAsDouble()));result.remove("crosshair");if(view.has("crosshairBlock"))result.add("crosshair",view.get("crosshairBlock"));if(view.has("crosshairEntity")){var target=view.getAsJsonObject("crosshairEntity");target.addProperty("type","entity");result.add("clientTarget",target);}result.add("clientObservedAt",view.get("clientObservedAt"));return result;});
   }).get(5,TimeUnit.SECONDS);reply(x,200,future.get(5,TimeUnit.SECONDS));return;
  }

  if(path.equals("/human/state")&&method.equals("GET")){
   var future=onClient(()->{var c=MinecraftClient.getInstance();if(c.player==null||c.getServer()==null)throw new IllegalStateException("Open the school world first");var id=c.player.getUuid();return c.getServer().submit(()->WorldObservation.capture(c.getServer(),c.getServer().getPlayerManager().getPlayer(id),0));}).get(5,TimeUnit.SECONDS);
   reply(x,200,future.get(5,TimeUnit.SECONDS));return;
  }
  if(path.equals("/observe")&&method.equals("GET")){if(realtime){if(latest==null)throw new IllegalStateException("Waiting for first native observation");JsonObject result=status();result.add("state",onClient(()->liveObservation()).get(5,TimeUnit.SECONDS));result.addProperty("wallMs",(System.nanoTime()-startedNs)/1e6);reply(x,200,result);}else{if(!active()||pending!=null||lastResult==null)throw new IllegalStateException("No idle controlled observation");reply(x,200,lastResult.deepCopy());}return;}
  if(!method.equals("POST")){reply(x,405,error("Use POST"));return;}

  byte[] bytes=x.getRequestBody().readNBytes(16385);if(bytes.length>16384){reply(x,413,error("Request too large"));return;}JsonObject body=bytes.length==0?new JsonObject():JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();

  switch(path){

   case "/human/start" -> {reply(x,200,onClient(()->{var c=MinecraftClient.getInstance();if(active()||humanInputs.active())throw new IllegalStateException("Stop the current controller or capture first");if(c.player==null||c.getServer()==null||!c.isInSingleplayer()||!c.getServer().getSaveProperties().getLevelName().startsWith("Fly Colony"))throw new IllegalStateException("Open a single-player Fly Colony world");humanInputs.start();return humanInputs.snapshot(c);}).get(5,TimeUnit.SECONDS));}
   case "/human/stop" -> {reply(x,200,onClient(()->{humanInputs.stop();return humanInputs.snapshot(MinecraftClient.getInstance());}).get(5,TimeUnit.SECONDS));}
   case "/start" -> {int fps=body.has("targetTps")?body.get("targetTps").getAsInt():120;if(fps<20||fps>260)throw new IllegalArgumentException("targetTps must be 20Ã¢â‚¬â€œ260");reply(x,200,onClient(()->start(fps,body.has("clockMode")?body.get("clockMode").getAsString():"paired")).get(10,TimeUnit.SECONDS));}

   case "/stop" -> {stop("Controller stopped");reply(x,200,status());}
   case "/school/reset" -> {
    String lesson=body.get("lesson").getAsString();long seed=body.get("seed").getAsLong();
    var prepared=onClient(()->{if(active())throw new IllegalStateException("Stop the controller before a school reset");var c=MinecraftClient.getInstance();var server=c.getServer();if(server==null||c.player==null||!c.isInSingleplayer()||server.getPlayerManager().getPlayerList().size()!=1)throw new IllegalStateException("Open the single-player school world");SchoolArena.validate(server.getSaveProperties().getLevelName(),lesson,seed);controls.release(c);c.interactionManager.cancelBlockBreaking();c.player.closeHandledScreen();c.setScreen(null);return server.submit(()->SchoolArena.reset(server,server.getPlayerManager().getPlayer(c.player.getUuid()),lesson,seed));}).get(10,TimeUnit.SECONDS);
    reply(x,200,prepared.get(15,TimeUnit.SECONDS));
   }

   case "/arena/reset", "/arena/evaluate" -> {
    boolean reset=path.equals("/arena/reset");long seed=body.has("seed")?body.get("seed").getAsLong():42;
    var prepared=onClient(()->{if(reset&&active())throw new IllegalStateException("Stop the controller before resetting the arena");var c=MinecraftClient.getInstance();var server=c.getServer();if(server==null||c.player==null||!c.isInSingleplayer()||server.getPlayerManager().getPlayerList().size()!=1)throw new IllegalStateException("Open the single-player school world");ResourceArena.validate(server.getSaveProperties().getLevelName(),seed);var id=c.player.getUuid();if(reset){controls.release(c);c.interactionManager.cancelBlockBreaking();c.player.closeHandledScreen();c.setScreen(null);}return server.submit(()->reset?ResourceArena.reset(server,server.getPlayerManager().getPlayer(id),seed,body.has("approach")?body.get("approach").getAsString():"front"):ResourceArena.evaluate(server,server.getPlayerManager().getPlayer(id)));}).get(10,TimeUnit.SECONDS);
    reply(x,200,prepared.get(60,TimeUnit.SECONDS));
   }
   case "/seedfarm/reset", "/seedfarm/evaluate" -> {
    boolean reset=path.equals("/seedfarm/reset");long seed=body.has("seed")?body.get("seed").getAsLong():42;
    var prepared=onClient(()->{if(reset&&active())throw new IllegalStateException("Stop the controller before resetting seed-to-farm finale");var c=MinecraftClient.getInstance();var server=c.getServer();if(server==null||c.player==null||!c.isInSingleplayer()||server.getPlayerManager().getPlayerList().size()!=1)throw new IllegalStateException("Open the single-player school world");SiteLesson.validate(server.getSaveProperties().getLevelName(),seed);var id=c.player.getUuid();if(reset){controls.release(c);c.interactionManager.cancelBlockBreaking();c.player.closeHandledScreen();c.setScreen(null);}return server.submit(()->reset?SeedFarmArena.reset(server,server.getPlayerManager().getPlayer(id),seed):SeedFarmArena.evaluate(server,server.getPlayerManager().getPlayer(id)));}).get(10,TimeUnit.SECONDS);
    reply(x,200,prepared.get(120,TimeUnit.SECONDS));
   }
   case "/pipeline/reset", "/pipeline/evaluate" -> {
    boolean reset=path.equals("/pipeline/reset");long seed=body.has("seed")?body.get("seed").getAsLong():42;
    var prepared=onClient(()->{if(reset&&active())throw new IllegalStateException("Stop the controller before resetting combined lesson");var c=MinecraftClient.getInstance();var server=c.getServer();if(server==null||c.player==null||!c.isInSingleplayer()||server.getPlayerManager().getPlayerList().size()!=1)throw new IllegalStateException("Open the single-player school world");PipelineLesson.validate(server.getSaveProperties().getLevelName(),seed);var id=c.player.getUuid();if(reset){controls.release(c);c.interactionManager.cancelBlockBreaking();c.player.closeHandledScreen();c.setScreen(null);}return server.submit(()->reset?PipelineArena.reset(server,server.getPlayerManager().getPlayer(id),seed,body.has("environment")?body.get("environment").getAsString():"school"):PipelineArena.evaluate(server,server.getPlayerManager().getPlayer(id)));}).get(10,TimeUnit.SECONDS);
    reply(x,200,prepared.get(60,TimeUnit.SECONDS));
   }
   case "/site/reset", "/site/evaluate" -> {
    boolean reset=path.equals("/site/reset");long seed=body.has("seed")?body.get("seed").getAsLong():42;
    var prepared=onClient(()->{if(reset&&active())throw new IllegalStateException("Stop the controller before resetting site lesson");var c=MinecraftClient.getInstance();var server=c.getServer();if(server==null||c.player==null||!c.isInSingleplayer()||server.getPlayerManager().getPlayerList().size()!=1)throw new IllegalStateException("Open the single-player school world");SiteLesson.validate(server.getSaveProperties().getLevelName(),seed);var id=c.player.getUuid();if(reset){controls.release(c);c.interactionManager.cancelBlockBreaking();c.player.closeHandledScreen();c.setScreen(null);}return server.submit(()->reset?SiteArena.reset(server,server.getPlayerManager().getPlayer(id),seed):SiteArena.evaluate(server,server.getPlayerManager().getPlayer(id)));}).get(10,TimeUnit.SECONDS);
    reply(x,200,prepared.get(60,TimeUnit.SECONDS));
   }
   case "/equipment/reset", "/equipment/evaluate" -> {
    boolean reset=path.equals("/equipment/reset");long seed=body.has("seed")?body.get("seed").getAsLong():42;
    var prepared=onClient(()->{if(reset&&active())throw new IllegalStateException("Stop the controller before resetting equipment lesson");var c=MinecraftClient.getInstance();var server=c.getServer();if(server==null||c.player==null||!c.isInSingleplayer()||server.getPlayerManager().getPlayerList().size()!=1)throw new IllegalStateException("Open the single-player school world");EquipmentLesson.validate(server.getSaveProperties().getLevelName(),seed);var id=c.player.getUuid();if(reset){controls.release(c);c.interactionManager.cancelBlockBreaking();c.player.closeHandledScreen();c.setScreen(null);}return server.submit(()->reset?EquipmentArena.reset(server,server.getPlayerManager().getPlayer(id),seed):EquipmentArena.evaluate(server,server.getPlayerManager().getPlayer(id)));}).get(10,TimeUnit.SECONDS);
    reply(x,200,prepared.get(60,TimeUnit.SECONDS));
   }
   case "/craft/reset", "/craft/evaluate" -> {
    boolean reset=path.equals("/craft/reset");long seed=body.has("seed")?body.get("seed").getAsLong():42;
    var prepared=onClient(()->{if(reset&&active())throw new IllegalStateException("Stop the controller before resetting crafting lesson");var c=MinecraftClient.getInstance();var server=c.getServer();if(server==null||c.player==null||!c.isInSingleplayer()||server.getPlayerManager().getPlayerList().size()!=1)throw new IllegalStateException("Open the single-player school world");CraftLesson.validate(server.getSaveProperties().getLevelName(),seed);var id=c.player.getUuid();if(reset){controls.release(c);c.interactionManager.cancelBlockBreaking();c.player.closeHandledScreen();c.setScreen(null);}return server.submit(()->reset?CraftArena.reset(server,server.getPlayerManager().getPlayer(id),seed):CraftArena.evaluate(server,server.getPlayerManager().getPlayer(id)));}).get(10,TimeUnit.SECONDS);
    reply(x,200,prepared.get(60,TimeUnit.SECONDS));
   }
   case "/seeds/reset", "/seeds/evaluate" -> {
    boolean reset=path.equals("/seeds/reset");long seed=body.has("seed")?body.get("seed").getAsLong():42;
    var prepared=onClient(()->{if(reset&&active())throw new IllegalStateException("Stop the controller before resetting seed lesson");var c=MinecraftClient.getInstance();var server=c.getServer();if(server==null||c.player==null||!c.isInSingleplayer()||server.getPlayerManager().getPlayerList().size()!=1)throw new IllegalStateException("Open the single-player school world");SeedLesson.validate(server.getSaveProperties().getLevelName(),seed);var id=c.player.getUuid();if(reset){controls.release(c);c.interactionManager.cancelBlockBreaking();c.player.closeHandledScreen();c.setScreen(null);}return server.submit(()->reset?SeedArena.reset(server,server.getPlayerManager().getPlayer(id),seed,body.has("controlsOnly")&&body.get("controlsOnly").getAsBoolean()):SeedArena.evaluate(server,server.getPlayerManager().getPlayer(id)));}).get(10,TimeUnit.SECONDS);
    reply(x,200,prepared.get(60,TimeUnit.SECONDS));
   }
   case "/pvp/reset", "/pvp/evaluate", "/pvp/senses" -> {
    String op=path.substring(5);long seed=body.has("seed")?body.get("seed").getAsLong():42;String kind=body.has("opponent")?body.get("opponent").getAsString():"zombie";
    var prepared=onClient(()->{if(op.equals("reset")&&active())throw new IllegalStateException("Stop the controller before resetting the PvP arena");var c=MinecraftClient.getInstance();var server=c.getServer();if(server==null||c.player==null||!c.isInSingleplayer()||server.getPlayerManager().getPlayerList().size()!=1)throw new IllegalStateException("Open the single-player school world");PvpLesson.validate(server.getSaveProperties().getLevelName(),seed,kind);var id=c.player.getUuid();if(op.equals("reset")){controls.release(c);c.interactionManager.cancelBlockBreaking();c.player.closeHandledScreen();c.setScreen(null);}return server.submit(()->{var p=server.getPlayerManager().getPlayer(id);return switch(op){case "reset"->PvpArena.reset(server,p,seed,kind);case "evaluate"->PvpArena.evaluate(server,p);default->{var j=PvpArena.senses(server,p);j.addProperty("opponentId",PvpArena.opponentId(server));yield j;}};});}).get(10,TimeUnit.SECONDS);
    JsonObject result=prepared.get(60,TimeUnit.SECONDS);
    // Motion, cooldown and crosshair are what the local client sees this frame.
    if(op.equals("senses")){int opponentId=result.get("opponentId").getAsInt();onClient(()->{PvpArena.overlay(MinecraftClient.getInstance(),result,opponentId);return null;}).get(5,TimeUnit.SECONDS);}
    reply(x,200,result);
   }
   case "/wood/reset", "/wood/evaluate" -> {
    boolean reset=path.equals("/wood/reset");long seed=body.has("seed")?body.get("seed").getAsLong():42;
    var prepared=onClient(()->{if(reset&&active())throw new IllegalStateException("Stop the controller before resetting wood lesson");var c=MinecraftClient.getInstance();var server=c.getServer();if(server==null||c.player==null||!c.isInSingleplayer()||server.getPlayerManager().getPlayerList().size()!=1)throw new IllegalStateException("Open the single-player school world");WoodLesson.validate(server.getSaveProperties().getLevelName(),seed);var id=c.player.getUuid();if(reset){controls.release(c);c.interactionManager.cancelBlockBreaking();c.player.closeHandledScreen();c.setScreen(null);}return server.submit(()->reset?WoodArena.reset(server,server.getPlayerManager().getPlayer(id),seed):WoodArena.evaluate(server,server.getPlayerManager().getPlayer(id)));}).get(10,TimeUnit.SECONDS);
    reply(x,200,prepared.get(60,TimeUnit.SECONDS));
   }
   case "/farm/reset", "/farm/evaluate" -> {
    boolean reset=path.equals("/farm/reset");String environment=body.has("environment")?body.get("environment").getAsString():"school";String lesson=body.has("lesson")?body.get("lesson").getAsString():"plant";long seed=body.has("seed")?body.get("seed").getAsLong():42;int growth=body.has("randomTickSpeed")?body.get("randomTickSpeed").getAsInt():3;int rounds=lesson.equals("establish")?(body.has("establishmentRounds")?body.get("establishmentRounds").getAsInt():1):body.has("maintenanceRounds")?body.get("maintenanceRounds").getAsInt():lesson.equals("maintain")?2:0;
    var prepared=onClient(()->{if(reset&&active())throw new IllegalStateException("Stop the controller before resetting the farm");var c=MinecraftClient.getInstance();var server=c.getServer();if(server==null||c.player==null||!c.isInSingleplayer()||server.getPlayerManager().getPlayerList().size()!=1)throw new IllegalStateException("Open the single-player school world");FarmLesson.validate(server.getSaveProperties().getLevelName(),lesson,seed,growth);var id=c.player.getUuid();if(reset){controls.release(c);c.interactionManager.cancelBlockBreaking();c.player.closeHandledScreen();c.setScreen(null);}return server.submit(()->reset?FarmArena.reset(server,server.getPlayerManager().getPlayer(id),lesson,seed,growth,rounds,environment):FarmArena.evaluate(server,server.getPlayerManager().getPlayer(id)));}).get(10,TimeUnit.SECONDS);
    reply(x,200,prepared.get(environment.equals("homestead")?120:60,TimeUnit.SECONDS));
   }
   case "/vision" -> {CompletableFuture<JsonObject> result;synchronized(this){if(vision!=null)throw new IllegalStateException("Visual capture pending");result=new CompletableFuture<>();vision=result;}try{reply(x,200,result.get(8,TimeUnit.SECONDS));}finally{if(vision==result)vision=null;}}
   case "/perception" -> {CompletableFuture<JsonObject> result;synchronized(this){if(perception!=null)throw new IllegalStateException("Perception capture pending");if(!realtime||latest==null)throw new IllegalStateException("Start realtime observation first");result=new CompletableFuture<>();perception=result;}try{reply(x,200,result.get(8,TimeUnit.SECONDS));}finally{if(perception==result)perception=null;}}

   case "/step" -> {StepRequest request=StepRequest.parse(body);CompletableFuture<JsonObject> result;

    synchronized(this){if(!active())throw new IllegalStateException("Start an experiment first");if(pending!=null)throw new IllegalStateException("One step is already in flight");if(request.sequence()!=completedSteps+1)throw new IllegalArgumentException("Expected sequence "+(completedSteps+1));result=new CompletableFuture<>();lastCommandNs=System.nanoTime();pending=new Pending(request,result,System.nanoTime(),serverTicks(),new int[]{request.ticks()},new String[]{null});}

    try{reply(x,200,result.get(20,TimeUnit.SECONDS));}catch(TimeoutException e){stop("Action request timeout");throw e;}

   }

   case "/capture" -> {CompletableFuture<byte[]> result; synchronized(this){if(capture!=null)throw new IllegalStateException("Capture already pending");result=new CompletableFuture<>();capture=result;}try{byte[] png=result.get(8,TimeUnit.SECONDS);x.getResponseHeaders().set("Content-Type","image/png");x.getResponseHeaders().set("Cache-Control","no-store");x.sendResponseHeaders(200,png.length);x.getResponseBody().write(png);x.close();}finally{if(capture==result)capture=null;}}

   default -> reply(x,404,error("Unknown endpoint"));

  }

 }catch(Exception e){try{reply(x,e instanceof IllegalArgumentException?400:409,error(e.getCause()!=null?e.getCause().toString():e.toString()));}catch(Exception ignored){}}}

 private JsonObject start(int fps,String clockMode){if(!Set.of("paired","realtime").contains(clockMode))throw new IllegalArgumentException("Invalid clockMode");var c=MinecraftClient.getInstance();var server=c.getServer();if(active()||humanInputs.active())throw new IllegalStateException("Controller or human demo already active");if(server==null||c.world==null||c.player==null||!c.isInSingleplayer())throw new IllegalStateException("Open a singleplayer experiment world first");if(!server.getSaveProperties().getLevelName().startsWith("Fly Colony"))throw new IllegalStateException("Use a new world whose name starts with Fly Colony");if(server.getPlayerManager().getPlayerList().size()!=1)throw new IllegalStateException("Use a single-player experiment, with no LAN guests");

  oldServerRate=server.getTickManager().getTickRate();oldFps=c.options.getMaxFps().getValue();oldVsync=c.options.getEnableVsync().getValue();oldPauseOnLostFocus=c.options.pauseOnLostFocus;c.options.pauseOnLostFocus=false;controlledServer=server;playerId=c.player.getUuid();serverFence=clientFence=clientTicks=completedSteps=0;latest=null;lastResult=null;sessionId=UUID.randomUUID().toString();stopReason="";startedNs=System.nanoTime();c.setScreen(null);c.options.getMaxFps().setValue(clockMode.equals("realtime")?Math.max(60,fps):fps);c.options.getEnableVsync().setValue(false);server.submit(()->server.getTickManager().setTickRate(clockMode.equals("realtime")?20:1000)).join();liveServerTicks=renderFrames=0;heldRequest=null;cameraTurn.cancel();lastCommandNs=lastRenderNs=System.nanoTime();realtime=clockMode.equals("realtime");if(!realtime)gate.start();JsonObject j=status();j.addProperty("targetTps",fps);j.addProperty("worldName",server.getSaveProperties().getLevelName());j.addProperty("worldSeed",server.getOverworld().getSeed());j.addProperty("assistance","Structured nearby blocks, entities, inventory and native GUI input; no pathfinder or recipe executor");return j;

 }

 public void stop(String reason){var farmServer=MinecraftClient.getInstance().getServer();if(farmServer!=null)farmServer.execute(()->FarmArena.restore(farmServer));humanInputs.stop();boolean wasActive=active();realtime=false;heldRequest=null;cameraTurn.cancel();gate.stop();stopReason=reason;Pending p=pending;pending=null;if(p!=null)p.future.completeExceptionally(new IllegalStateException(reason));tickInFlight=false;tickCommand.clear();

  if(wasActive){var c=MinecraftClient.getInstance();c.execute(()->{controls.release(c);if(c.interactionManager!=null){c.interactionManager.cancelBlockBreaking();if(c.player!=null)c.interactionManager.stopUsingItem(c.player);}c.options.getMaxFps().setValue(oldFps);c.options.getEnableVsync().setValue(oldVsync);c.options.pauseOnLostFocus=oldPauseOnLostFocus;});var server=controlledServer;if(server!=null)server.execute(()->{server.getTickManager().setTickRate(oldServerRate);FarmArena.restore(server);PipelineArena.restore(server);SeedFarmArena.restore(server);});}

 }

 public boolean beforeClientTick(){humanInputs.sample(MinecraftClient.getInstance());if(!active())return true;var c=MinecraftClient.getInstance();liveCursorLocked=c.mouse.isCursorLocked();liveWindowFocused=c.isWindowFocused();if(c.world==null||c.player==null||c.getServer()!=controlledServer){stop("World disconnected");return true;}if(c.isPaused()){stop("Game paused");return true;}Pending p=pending;tickCommand.begin(p);if(realtime){liveScreenOpen=c.currentScreen!=null;liveScreenType=liveScreenOpen?c.currentScreen.getClass().getSimpleName():"none";if(System.nanoTime()-lastCommandNs>5_000_000_000L){stop("Controller lease expired");return true;}if(p!=null){heldRequest=p.request;if(p.remaining[0]==p.request.ticks())cameraTurn.start(p.request,System.nanoTime());}StepRequest r=heldRequest;if(r!=null)try{controls.apply(c,r,p!=null&&p.remaining[0]==p.request.ticks());}catch(Exception e){heldRequest=null;controls.release(c);if(p!=null)p.error[0]=e.toString();}tickInFlight=true;return true;}if(p==null)return false;tickInFlight=true;try{controls.apply(c,p.request,p.remaining[0]==p.request.ticks());}catch(Exception e){p.error[0]=e.toString();controls.release(c);}return true;}

 public void afterClientTick(){if(!active()||!tickInFlight)return;tickInFlight=false;Pending p=tickCommand.finish(pending);if(realtime){var client=MinecraftClient.getInstance();liveScreenOpen=client.currentScreen!=null;liveScreenType=liveScreenOpen?client.currentScreen.getClass().getSimpleName():"none";liveBreakingProgress=client.interactionManager==null?0:((dev.flycolony.bridge.mixin.InteractionAccess)client.interactionManager).flyBreakingProgress();clientTicks++;if(p!=null){p.remaining[0]--;if((p.remaining[0]<=0||p.error[0]!=null)&&latest!=null){if(p.error[0]==null)applyTurn(client,cameraTurn.finish());else cameraTurn.cancel();heldRequest=p.error[0]==null?p.request.observationHold(client.currentScreen!=null):null;if(heldRequest!=null)controls.apply(client,heldRequest,false);else controls.release(client);JsonObject result=status();result.addProperty("schema","flybridge-realtime-step-1");result.addProperty("sequence",p.request.sequence());result.addProperty("ok",p.error[0]==null);if(p.error[0]!=null)result.addProperty("error",p.error[0]);result.addProperty("actionTicks",p.request.ticks()-p.remaining[0]);result.addProperty("wallMs",(System.nanoTime()-startedNs)/1e6);result.addProperty("simulationSeconds",liveServerTicks/20.0);result.add("state",liveObservation());completedSteps++;lastResult=result.deepCopy();pending=null;p.future.complete(result);}}return;}if(p==null)return;try{var c=MinecraftClient.getInstance();long sequence=gate.completed()+1;ClientPlayNetworking.send(new FencePayload(sessionId,sequence));StepGate.Ticket ticket=gate.issue();gate.await(ticket,5000);long deadline=System.nanoTime()+2_000_000_000L;c.runTasks(()->!active()||clientFence>=sequence||System.nanoTime()>deadline);if(clientFence<sequence)throw new TimeoutException("Client observation fence did not arrive");

   clientTicks++;p.remaining[0]--;if(p.remaining[0]<=0||p.error[0]!=null){if(p.error[0]!=null)controls.release(c);JsonObject result=status();result.addProperty("schema","flybridge-step-0.2");result.addProperty("sequence",p.request.sequence());result.addProperty("action",p.request.action());result.addProperty("ok",p.error[0]==null);if(p.error[0]!=null)result.addProperty("error",p.error[0]);result.addProperty("actionTicks",gate.completed()-p.firstTick);result.addProperty("wallMs",(System.nanoTime()-startedNs)/1e6);result.addProperty("actionWallMs",(System.nanoTime()-p.started)/1e6);result.addProperty("simulationSeconds",gate.completed()/20.0);JsonObject observation=latest.deepCopy();observation.addProperty("screenOpen",c.currentScreen!=null);observation.addProperty("screenType",c.currentScreen==null?"none":c.currentScreen.getClass().getSimpleName());result.add("state",observation);completedSteps++;lastResult=result.deepCopy();synchronized(this){pending=null;}p.future.complete(result);}

  }catch(Exception e){stop("Synchronization stopped: "+e);}}

 public StepGate.Ticket beforeServerTick(MinecraftServer server){if(!pairedActive()||server!=controlledServer)return null;try{StepGate.Ticket ticket=gate.acquire();if(ticket==null)return null;long deadline=System.nanoTime()+2_000_000_000L;server.runTasks(()->!active()||serverFence>=ticket.sequence()||System.nanoTime()>deadline);if(serverFence<ticket.sequence())throw new TimeoutException("Player action fence did not arrive");return ticket;}catch(Exception e){stop("Server synchronization stopped: "+e);return null;}}

 public void afterServerTick(MinecraftServer server,StepGate.Ticket ticket){FarmArena.track(server);SiteArena.track(server);PipelineArena.track(server);SeedFarmArena.track(server);if(!active()||server!=controlledServer||(!realtime&&ticket==null))return;try{ServerPlayerEntity p=server.getPlayerManager().getPlayer(playerId);if(p==null)throw new IllegalStateException("Player left");JsonObject observation=WorldObservation.capture(server,p,controls.selectedSlot());if(realtime){observation.addProperty("observedServerTick",++liveServerTicks);observation.addProperty("observedWallMs",(System.nanoTime()-startedNs)/1e6);}latest=observation;if(realtime)return;ServerPlayNetworking.send(p,new FencePayload(sessionId,ticket.sequence()));gate.finish(ticket);}catch(Exception e){stop("Observation failed: "+e);}}

 private JsonObject liveObservation(){JsonObject o=latest.deepCopy();ClientObservation.overlay(MinecraftClient.getInstance(),o);o.addProperty("breakingProgress",liveBreakingProgress);o.addProperty("screenOpen",liveScreenOpen);o.addProperty("screenType",liveScreenType);return o;}
 private void applyTurn(MinecraftClient c,BoundedTurn.Delta delta){if(c.player!=null&&c.currentScreen==null)c.player.changeLookDirection(delta.yaw()/.15,delta.pitch()/.15);}
 public void beforeRender(){long now=System.nanoTime();if(realtime){StepRequest r=heldRequest;var c=MinecraftClient.getInstance();if(r!=null&&r.action().equals("control")&&c.player!=null&&c.currentScreen==null)applyTurn(c,cameraTurn.advance(now));renderFrames++;}lastRenderNs=now;}
 public void afterRender(){CompletableFuture<byte[]> p=capture;CompletableFuture<JsonObject> v=vision;CompletableFuture<JsonObject> b=perception;if(p==null&&v==null&&b==null)return;
  try(var image=ScreenshotRecorder.takeScreenshot(MinecraftClient.getInstance().getFramebuffer())){
   var buffered=new java.awt.image.BufferedImage(image.getWidth(),image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);buffered.setRGB(0,0,image.getWidth(),image.getHeight(),image.copyPixelsArgb(),0,image.getWidth());
   if(v!=null){var packet=VisualObservation.capture(MinecraftClient.getInstance(),buffered);if(humanInputs.active())packet.add("human",humanInputs.snapshot(MinecraftClient.getInstance()));v.complete(packet);}
   if(b!=null){
    var client=MinecraftClient.getInstance();var packet=VisualObservation.capture(client,buffered);var state=liveObservation();
    var server=client.getServer();var id=client.player.getUuid();
    // The render thread never waits for the server's world survey.
    server.submit(()->{
     var player=server.getPlayerManager().getPlayer(id);if(player==null)throw new IllegalStateException("Player unavailable");
     var scene=WorldScene.capture(player);alignScene(scene,state);
     var bundle=new JsonObject();bundle.addProperty("schema","fly-perception-1");bundle.add("vision",packet);bundle.add("state",state);bundle.add("scene",scene);
     bundle.addProperty("surveyLagMs",scene.get("capturedAt").getAsLong()-packet.get("capturedAt").getAsLong());return bundle;
    }).whenComplete((value,error)->{if(error!=null)b.completeExceptionally(error);else b.complete(value);});
   }
   if(p!=null){var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(buffered,"png",bytes);p.complete(bytes.toByteArray());}
  }catch(Exception e){if(p!=null)p.completeExceptionally(e);if(v!=null)v.completeExceptionally(e);if(b!=null)b.completeExceptionally(e);}finally{if(capture==p)capture=null;if(vision==v)vision=null;if(perception==b)perception=null;}
 }

 private static void alignScene(JsonObject scene,JsonObject view){
  var pose=scene.getAsJsonObject("player");pose.add("position",view.get("position"));pose.add("eye",view.get("eye"));
  for(String key:new String[]{"velocity","onGround","horizontalCollision","verticalCollision"})if(view.has(key))pose.add(key,view.get(key));
  pose.addProperty("yaw",Math.toDegrees(view.get("yaw").getAsDouble()));pose.addProperty("pitch",Math.toDegrees(view.get("pitch").getAsDouble()));
  scene.remove("crosshair");if(view.has("crosshairBlock"))scene.add("crosshair",view.get("crosshairBlock"));
  if(view.has("crosshairEntity")){var target=view.getAsJsonObject("crosshairEntity").deepCopy();target.addProperty("type","entity");scene.add("clientTarget",target);}
  scene.add("clientObservedAt",view.get("clientObservedAt"));scene.add("breakingProgress",view.get("breakingProgress"));
 }


 public void shutdown(){stop("Minecraft closed");if(http!=null)http.stop(0);if(executor!=null)executor.shutdownNow();}

 private <T>CompletableFuture<T> onClient(Callable<T> fn){CompletableFuture<T> f=new CompletableFuture<>();MinecraftClient.getInstance().execute(()->{try{f.complete(fn.call());}catch(Exception e){f.completeExceptionally(e);}});return f;}

 private static JsonObject error(String s){JsonObject j=new JsonObject();j.addProperty("error",s);return j;}

 private static void reply(HttpExchange x,int code,JsonObject json)throws Exception{byte[] bytes=json.toString().getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.getResponseHeaders().set("Cache-Control","no-store");x.sendResponseHeaders(code,bytes.length);x.getResponseBody().write(bytes);x.close();}

}




