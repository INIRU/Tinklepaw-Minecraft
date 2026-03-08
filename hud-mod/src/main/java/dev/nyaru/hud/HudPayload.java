package dev.nyaru.hud;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HudPayload(byte[] data) implements CustomPayload {
    public static final Id<HudPayload> ID = new Id<>(Identifier.of("dev.nyaru", "hud"));

    public static final PacketCodec<PacketByteBuf, HudPayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeBytes(value.data()),
        buf -> {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new HudPayload(bytes);
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
