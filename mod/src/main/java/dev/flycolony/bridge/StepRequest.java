package dev.flycolony.bridge;



import com.google.gson.JsonObject;

import java.util.Set;



public record StepRequest(long sequence, String action, int ticks, float yaw, float pitch, int slot, int button, double x, double y, Set<String> keys) {

    /** Keep mining/use continuous while observing, but stop finite movement/turns. */
    public StepRequest observationHold(){
        return observationHold(false);
    }
    public StepRequest observationHold(boolean screenOpen){
        if(action.equals("mine_block")&&!screenOpen)return new StepRequest(sequence,"mine_block",1,0,0,0,0,.5,.5,Set.of());
        var buttons=new java.util.HashSet<String>();
        for(String key:Set.of("attack","use"))if(!screenOpen&&(keys.contains(key)||action.equals(key)))buttons.add(key);
        return new StepRequest(sequence,buttons.isEmpty()?"wait":"control",1,0,0,0,0,.5,.5,Set.copyOf(buttons));
    }
    public static final Set<String> ACTIONS=Set.of("mine_block","control","wait","forward","back","left","right","jump","jump_forward","sprint_forward","sneak_forward","look","attack","attack_once","use","release_use","inventory","close_screen","hotbar","hotbar_next","hotbar_previous","slot_select","slot_next","slot_previous","slot_left","slot_right","slot_shift","slot_swap","craft_output","drop","swap_offhand","respawn","gui_left","gui_right");

    public static StepRequest parse(JsonObject o) {

        for(String key:o.keySet())if(!Set.of("sequence","action","ticks","yaw","pitch","slot","button","x","y","keys").contains(key))throw new IllegalArgumentException("Unsupported field: "+key);

        long sequence=integer(o,"sequence",0);String action=o.get("action").getAsString();

        int ticks=integer(o,"ticks",1),slot=integer(o,"slot",0),button=integer(o,"button",0);

        double yaw=number(o,"yaw",0),pitch=number(o,"pitch",0),x=number(o,"x",.5),y=number(o,"y",.5);

        if(sequence<1||sequence>1_000_000_000L||!ACTIONS.contains(action)||ticks<1||ticks>100||slot<0||slot>255||button<0||button>8||!Double.isFinite(yaw)||Math.abs(yaw)>180||!Double.isFinite(pitch)||Math.abs(pitch)>90||!Double.isFinite(x)||x<0||x>1||!Double.isFinite(y)||y<0||y>1)throw new IllegalArgumentException("Invalid action, tick count or bounded argument");

        Set<String> keys=new java.util.HashSet<>();if(o.has("keys"))for(var key:o.getAsJsonArray("keys")){String k=key.getAsString();if(!Set.of("forward","back","left","right","jump","sprint","sneak","attack","use").contains(k))throw new IllegalArgumentException("Unknown held key");keys.add(k);}

        if(action.equals("attack_once")&&(ticks!=1||!keys.isEmpty()||yaw!=0||pitch!=0))throw new IllegalArgumentException("A single attack is one stationary tick with no held keys");

        if(action.equals("mine_block")&&(ticks>10||!keys.isEmpty()||yaw!=0||pitch!=0))throw new IllegalArgumentException("Target mining is stationary, at most ten ticks, with no extra held keys");

        return new StepRequest(sequence,action,ticks,(float)yaw,(float)pitch,slot,button,x,y,Set.copyOf(keys));

    }

    private static int integer(JsonObject o,String key,int fallback){if(!o.has(key))return fallback;double v=o.get(key).getAsDouble();if(!Double.isFinite(v)||v!=Math.rint(v)||v<Integer.MIN_VALUE||v>Integer.MAX_VALUE)throw new IllegalArgumentException("Expected integer: "+key);return (int)v;}

    private static double number(JsonObject o,String key,double fallback){return o.has(key)?o.get(key).getAsDouble():fallback;}

}

