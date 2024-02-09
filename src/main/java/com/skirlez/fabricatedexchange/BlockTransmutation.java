package com.skirlez.fabricatedexchange;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class BlockTransmutation {
	public static ImmutableMap<Block, Block> blockTransmutationMap = ImmutableMap.of();
	
	public static boolean canTransmuteBlock(Block in) {
		return blockTransmutationMap.containsKey(in);
	}
	
	public static Optional<Block> getBlockToTransmute(Block in) {
		return Optional.ofNullable(blockTransmutationMap.get(in));
	}
	
	public static void generateBlockRotationMap(String[][] blockTransmutationData) {
		if (blockTransmutationData == null)
			return;
		
		Map<Block, Block> map = new HashMap<Block, Block>();
		for (int i = 0; i < blockTransmutationData.length; i++) {
			int j = 0;
		
			if (blockTransmutationData[i] == null) 
				continue;
			int len = blockTransmutationData[i].length;
			if (len == 0)
				continue;
			if (len == 1) {
				String str = blockTransmutationData[i][j];
				String[] parts = str.split("#");
				addBlockRelation(parts[0], parts[1], map); 
				continue;
			}
			while (j < len - 1) {
				addBlockRelation(blockTransmutationData[i][j], blockTransmutationData[i][j + 1], map); 
				j++;
			}
			addBlockRelation(blockTransmutationData[i][j], blockTransmutationData[i][0], map); 
		}
		
		blockTransmutationMap = ImmutableMap.copyOf(map);
	}

	private static void addBlockRelation(String str1, String str2, Map<Block, Block> map) {
		Block b1 = Registries.BLOCK.get(new Identifier(str1));
		Block b2 = Registries.BLOCK.get(new Identifier(str2));
		if (b1 == null || b2 == null) {
			FabricatedExchange.LOGGER.error("Invalid block(s) found in block_transmutation_map.json! Block 1: " + str1 + " -> Block 2: " + str2);
			return;
		}
		if (map.containsKey(b1)) {
			FabricatedExchange.LOGGER.error("Duplicate block transmutation in block_transmutation_map.json! Block 1: " + str1 + " -> Block 2: " + str2);
			return;
		}
		map.put(b1, b2);
	};
	
}