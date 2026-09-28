package dev.flycolony.bridge;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import net.minecraft.client.MinecraftClient;

/** Opt-in, in-game input sampling only. Does not inject or capture desktop input. */
public final class HumanInputs {
 private boolean active;private long tick,started;private final ArrayDeque<JsonObject> samples=new ArrayDeque<>();
 public boolean active(){return active;}
 public void start(){samples.clear();tick=0;started=System.currentTimeMillis();active=true;}
 public void stop(){active=false;}
 public void sample(MinecraftClient c){
  if(!active)return;
  if(c.player==null||c.world==null||System.currentTimeMillis()-started>100_000){active=false;return;}
  var row=new JsonObject();row.addProperty("tick",++tick);row.addProperty("at",System.currentTimeMillis());
  row.addProperty("yaw",c.player.getYaw());row.addProperty("pitch",c.player.getPitch());row.addProperty("guiOpen",c.currentScreen!=null);row.addProperty("hotbar",c.player.getInventory().selectedSlot);
  var keys=new JsonArray();var o=c.options;
  var bindings=new net.minecraft.client.option.KeyBinding[]{o.forwardKey,o.backKey,o.leftKey,o.rightKey,o.jumpKey,o.sprintKey,o.sneakKey,o.attackKey,o.useKey};
  String[] names={"forward","back","left","right","jump","sprint","sneak","attack","use"};
  for(int i=0;i<bindings.length;i++)if(bindings[i].isPressed())keys.add(names[i]);row.add("keys",keys);
  samples.addLast(row);while(samples.size()>600)samples.removeFirst();
 }
 public JsonObject snapshot(MinecraftClient c){
  var out=new JsonObject();out.addProperty("schema","fly-human-inputs-1");out.addProperty("active",active);out.addProperty("tick",tick);
  if(c.player!=null){out.addProperty("yaw",c.player.getYaw());out.addProperty("pitch",c.player.getPitch());out.addProperty("hotbar",c.player.getInventory().selectedSlot);}
  out.addProperty("guiOpen",c.currentScreen!=null);var rows=new JsonArray();for(var row:samples)rows.add(row.deepCopy());out.add("samples",rows);return out;
 }
}
