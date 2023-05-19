package com.skirlez.fabricatedexchange.networking.packet;

import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;

import java.math.BigInteger;

import com.skirlez.fabricatedexchange.FabricatedExchangeClient;
public class EMCSyncS2CPacket {
   public static void receive(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
      FabricatedExchangeClient.clientEmc = new BigInteger(buf.readString());
   }
}