package com.skirlez.fabricatedexchange.block;

import java.util.ArrayList;
import java.util.List;

import com.skirlez.fabricatedexchange.util.GeneralUtil;
import com.skirlez.fabricatedexchange.util.SuperNumber;

import net.minecraft.block.entity.BlockEntity;

/* This interface is used by block entities which can be in an idle state or consuming state.
For example: When the Energy Collectors have no fuel items in them, They are in the idle state,
simply generating EMC. When a fuel item is put inside of them, they are in the consuming state,
consuming their EMC in order to transmute the fuel item to the next one. these states will be used 
by the block entities to determine if they should spread their EMC to the surrounding block entities or not. */
public interface ConsumerBlockEntityInterface {
    SuperNumber getEmc();
    SuperNumber getOutputRate();
    boolean isConsuming();

    default void distributeEmc(List<BlockEntity> neighbors) {
        List<Integer> giftIndices = new ArrayList<Integer>(); // stores the indices which will be given EMC (as a gift!)
        for (int i = 0; i < neighbors.size(); i++) {
            BlockEntity neighbor = neighbors.get(i);
            if (neighbor instanceof ConsumerBlockEntityInterface
                    && ((ConsumerBlockEntityInterface)neighbor).isConsuming())
                giftIndices.add(i);
        }
        if (giftIndices.size() > 0) {
            SuperNumber emc = getEmc();
            SuperNumber output = new SuperNumber(SuperNumber.min(getOutputRate(), emc));
            output.divide(giftIndices.size());
            for (int i : giftIndices) {
                ConsumerBlockEntityInterface neighbor = (ConsumerBlockEntityInterface)neighbors.get(i);
                neighbor.getEmc().steal(emc, output);
            }
        }
    }
    

}