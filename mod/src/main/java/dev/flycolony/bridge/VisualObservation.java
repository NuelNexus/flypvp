package dev.flycolony.bridge;

import com.google.gson.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;

/** Actor interface: actual RGB plus a strict body-only allowlist. No world map. */
public final class VisualObservation {
 public static JsonObject capture(MinecraftClient c,BufferedImage source)throws Exception{
  if(c.player==null||c.world==null)throw new IllegalStateException("Open a world for visual observations");
  long now=System.currentTimeMillis();int width=384,height=216;
  double scale=Math.min((double)width/source.getWidth(),(double)height/source.getHeight());
  int vw=(int)Math.round(source.getWidth()*scale),vh=(int)Math.round(source.getHeight()*scale),vx=(width-vw)/2,vy=(height-vh)/2;
  var scaled=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var g=scaled.createGraphics();
  g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(source,vx,vy,vw,vh,null);g.dispose();
  var bytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(scaled,"png",bytes);
  var j=new JsonObject();j.addProperty("schema","fly-visual-observation-1");j.addProperty("capturedAt",now);j.addProperty("width",width);j.addProperty("height",height);j.addProperty("png",Base64.getEncoder().encodeToString(bytes.toByteArray()));
  var viewport=new JsonObject();viewport.addProperty("x",(double)vx/width);viewport.addProperty("y",(double)vy/height);viewport.addProperty("width",(double)vw/width);viewport.addProperty("height",(double)vh/height);j.add("viewport",viewport);
  var p=c.player;var body=new JsonObject();body.addProperty("health",p.getHealth());body.addProperty("food",p.getHungerManager().getFoodLevel());body.addProperty("air",p.getAir());body.addProperty("onGround",p.isOnGround());body.addProperty("inWater",p.isTouchingWater());body.addProperty("usingItem",p.isUsingItem());body.addProperty("hotbar",p.getInventory().selectedSlot);body.addProperty("guiOpen",c.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>);body.addProperty("alive",p.isAlive());
  var inv=new JsonArray();for(int i=0;i<p.getInventory().size();i++){var stack=p.getInventory().getStack(i);if(stack.isEmpty())continue;var item=new JsonObject();item.addProperty("slot",i);item.addProperty("name",Registries.ITEM.getId(stack.getItem()).toString());item.addProperty("count",stack.getCount());inv.add(item);}body.add("inventory",inv);j.add("body",body);
  j.addProperty("assistance","RGB and health/hunger/air/ground/water/use/alive state, selected hotbar, screen-open flag and inventory identity/counts. No coordinates, crosshair identity, entities, voxels, recipes or rewards in actor observation.");return j;
 }
}
