package dev.flycolony.bridge;

import com.google.gson.JsonObject;
import java.util.Random;
import java.util.Set;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

/** Explicit, bounded training fixtures. Never permitted in challenge/ordinary worlds. */
public final class SchoolArena {
 public static final String WORLD_PREFIX="Fly Colony School";
 public static final Set<String> LESSONS=Set.of("hold_attack","collect_log","aim_log","eat_food");
 public static void validate(String worldName,String lesson,long seed){
  if(!worldName.startsWith(WORLD_PREFIX))throw new IllegalArgumentException("School resets require a separate world named Fly Colony School ...");
  if(!LESSONS.contains(lesson))throw new IllegalArgumentException("Unknown school lesson");
  if(seed<0||seed>2147483647L)throw new IllegalArgumentException("Invalid school seed");
 }
 public static JsonObject reset(MinecraftServer server,ServerPlayerEntity p,String lesson,long seed){
  validate(server.getSaveProperties().getLevelName(),lesson,seed);
  if(p.getServerWorld()!=server.getOverworld())throw new IllegalStateException("School uses the Overworld only");
  if(!p.isAlive())throw new IllegalStateException("Respawn before resetting the school");
  var world=server.getOverworld();var random=new Random(seed);
  world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
  world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,server);
  world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);
  world.setTimeOfDay(6000);world.setWeather(6000,0,false,false);
  // A disposable 13x13 practice platform, entirely above normal terrain.
  for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)for(int y=249;y<=255;y++){
   var block=y==249?Blocks.BEDROCK:(Math.abs(x)==6||Math.abs(z)==6)&&y<252?Blocks.GLASS:Blocks.AIR;
   world.setBlockState(new BlockPos(x,y,z),block.getDefaultState(),3);
  }
  for(var item:world.getEntitiesByClass(ItemEntity.class,new Box(-7,248,-7,7,257,7),e->true))item.discard();
  p.closeHandledScreen();p.getInventory().clear();p.playerScreenHandler.setCursorStack(ItemStack.EMPTY);
  p.clearStatusEffects();p.changeGameMode(GameMode.SURVIVAL);p.setHealth(20);p.getHungerManager().setFoodLevel(lesson.equals("eat_food")?8:20);p.getHungerManager().setSaturationLevel(0);
  p.getInventory().selectedSlot=0;p.setExperienceLevel(0);p.setExperiencePoints(0);
  boolean aim=lesson.equals("aim_log");double x=.5+(random.nextDouble()-.5)*(aim?.6:.15);double z=.5+(random.nextDouble()-.5)*.12;
  float yaw=aim?(random.nextBoolean()?1:-1)*(12+random.nextFloat()*14):0;
  if(lesson.equals("eat_food"))p.getInventory().setStack(0,new ItemStack(Items.BREAD,1));
  else world.setBlockState(new BlockPos(0,251,2),Blocks.OAK_LOG.getDefaultState(),3);
  p.setVelocity(0,0,0);p.fallDistance=0;p.teleport(world,x,250,z,Set.of(),yaw,4,true);p.currentScreenHandler.sendContentUpdates();
  JsonObject out=new JsonObject();out.addProperty("schema","fly-school-reset-1");out.addProperty("lesson",lesson);out.addProperty("seed",seed);out.addProperty("trainingOnly",true);out.addProperty("worldName",server.getSaveProperties().getLevelName());
  out.addProperty("assistance","Supplied enclosed platform, daylight, no mob spawning, restored health, cleared inventory, seeded starting pose and one oak log; food lesson supplies one bread and hunger. Native Survival inputs perform all actions. No challenge credit.");
  return out;
 }
}
