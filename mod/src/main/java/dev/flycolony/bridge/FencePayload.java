package dev.flycolony.bridge;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
public record FencePayload(String session,long sequence) implements CustomPayload {
    public static final Id<FencePayload> ID=new Id<>(Identifier.of("flybridge","fence"));
    public static final PacketCodec<RegistryByteBuf,FencePayload> CODEC=PacketCodec.tuple(PacketCodecs.STRING,FencePayload::session,PacketCodecs.VAR_LONG,FencePayload::sequence,FencePayload::new);
    @Override public Id<? extends CustomPayload> getId(){return ID;}
}
