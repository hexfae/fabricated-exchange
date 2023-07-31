package com.skirlez.fabricatedexchange.screen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.skirlez.fabricatedexchange.FabricatedExchange;
import com.skirlez.fabricatedexchange.FabricatedExchangeClient;
import com.skirlez.fabricatedexchange.emc.EmcData;
import com.skirlez.fabricatedexchange.networking.ModMessages;
import com.skirlez.fabricatedexchange.screen.slot.transmutation.ConsumeSlot;
import com.skirlez.fabricatedexchange.screen.slot.transmutation.MidSlot;
import com.skirlez.fabricatedexchange.screen.slot.transmutation.TransmutationSlot;
import com.skirlez.fabricatedexchange.util.GeneralUtil;
import com.skirlez.fabricatedexchange.util.PlayerState;
import com.skirlez.fabricatedexchange.util.ServerState;
import com.skirlez.fabricatedexchange.util.SuperNumber;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.collection.DefaultedList;

public class TransmutationTableScreenHandler extends ScreenHandler {
    private final Inventory inventory; // the transmutation slots
    private final Inventory minorInventory; // the middle slot and the slots on the left
    private final LivingEntity player;
    private ConsumeSlot emcSlot;
    private String searchText = "";
    private int offeringPageNum = 0;
    private int lastOfferingPage = 0;
    private List<Pair<Item, SuperNumber>> knowledge = new ArrayList<Pair<Item,SuperNumber>>();
    private final DefaultedList<TransmutationSlot> transmutationSlots = DefaultedList.of();
    public TransmutationTableScreenHandler(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
        this(syncId, inventory, new SimpleInventory(18));
        this.lastOfferingPage = buf.readInt();
    }
    
    public TransmutationTableScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(ModScreenHandlers.TRANSMUTATION_TABLE_SCREEN_HANDLER, syncId);
        checkSize(inventory, 18);
        minorInventory = new SimpleInventory(1);
        this.inventory = inventory;
        this.player = playerInventory.player;
        inventory.onOpen(playerInventory.player);
        emcSlot = new ConsumeSlot(inventory, 0, 108, 80, player, this);
        this.addSlot(emcSlot);
        
        // use trigonometry to create the transmutation slots

        // outer ring
        double angle = 270.0;
        for (int i = 0; i < 12; i++) {
            addTransmutationSlot(new TransmutationSlot(inventory, i + 1, angle, player, this));
            angle += 360.0 / 12.0;
        }

        // inner ring
        angle = 270.0;
        for (int i = 0; i < 4; i++) {
            addTransmutationSlot(new TransmutationSlot(inventory, i + 13, angle, player, this));
            angle += 360.0 / 4.0;
        }



        addSlot(new MidSlot(minorInventory, 0, 158, 32, this, player.world.isClient));
        GeneralUtil.addPlayerInventory(this, playerInventory, 36, 100);
        GeneralUtil.addPlayerHotbar(this, playerInventory, 36, 158);


        if (!player.getWorld().isClient()) {
            PlayerState playerState = ServerState.getPlayerState(player);
            for (int i = 0; i < playerState.knowledge.size(); i++) {
                String location = playerState.knowledge.get(i);
                String[] parts = location.split(":");
                Item item = Registries.ITEM.get(new Identifier(parts[0], parts[1]));
                SuperNumber emc = EmcData.getItemEmc(item);
                Pair<Item, SuperNumber> pair = new Pair<Item,SuperNumber>(item, emc);
                addKnowledgePair(pair);
            }
            refreshOffering();
        }
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
        offeringPageNum = 0;
    }
    public void changeOfferingPage(int value) {
        offeringPageNum = value;
        refreshOffering();
    }

    public int getLastPageNum() {
        return lastOfferingPage;
    }

    public void setLastPageNum(int lastOfferingPage) {
        this.lastOfferingPage = lastOfferingPage;
    }

    public void refreshOffering() {
        SuperNumber emc = ServerState.getPlayerState(this.player).emc;
        SuperNumber midItemEmc = EmcData.getItemStackEmc(this.slots.get(17).getStack());
        if (!midItemEmc.equalsZero())
            emc = SuperNumber.min(emc, midItemEmc);
        
        LinkedList<Pair<Item, SuperNumber>> newKnowledge = new LinkedList<Pair<Item, SuperNumber>>(knowledge);
        LinkedList<Pair<Item, SuperNumber>> fuelKnowledge = new LinkedList<Pair<Item, SuperNumber>>();
        boolean isSearching = !searchText.isEmpty();

        Iterator<Pair<Item, SuperNumber>> iterator = newKnowledge.iterator();
        while (iterator.hasNext()) {
            Pair<Item, SuperNumber> pair = iterator.next();
            SuperNumber itemEmc = pair.getRight();
            // emc filter - items who's emc value is greater than the players' emc shouldn't be displayed
            // (or if the item has 0 EMC which can happen if you learn it and then set the emc to 0)
            if (emc.compareTo(itemEmc) == -1 || itemEmc.equalsZero()) {
                iterator.remove();
                continue;            
            }

            // search filter - items who don't have the search text as a substring shouldn't be displayed.
            // TODO: does this work for other languages?
            Item item = pair.getLeft();
            String name = item.getName().getString();
            if (isSearching && !name.toLowerCase().contains(searchText.toLowerCase())) {
                iterator.remove();
                continue; 
            }
            if (FabricatedExchange.fuelProgressionMap.containsKey(item)) {
                // "fuel" items go in the inner ring, so we put them in this list
                iterator.remove(); 
                fuelKnowledge.add(new Pair<Item, SuperNumber>(item, itemEmc));
            }
        }
        lastOfferingPage = (newKnowledge.size() - 1) / 12;
        // make sure offering page is within bounds
        if (offeringPageNum != 0) {
            if (offeringPageNum > lastOfferingPage)
                offeringPageNum = lastOfferingPage;
            else if (offeringPageNum < 0)
                offeringPageNum = 0;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(lastOfferingPage);
        if (((ServerPlayerEntity)player).currentScreenHandler instanceof TransmutationTableScreenHandler)
            ServerPlayNetworking.send((ServerPlayerEntity)player, ModMessages.TRANSMUTATION_TABLE_MAX_PAGE, buf);


        // clear all the transmutation slots
        for (int i = 0; i < transmutationSlots.size(); i++) {
            transmutationSlots.get(i).setStack(ItemStack.EMPTY);
        }

        
        int num = 0;
        for (int i = offeringPageNum * 12; i < newKnowledge.size(); i++) {
            Item item = newKnowledge.get(i).getLeft();
            ItemStack stack = new ItemStack(item);
            transmutationSlots.get(num).setStack(stack);
            num++;
            if (num >= 12)
                break;
        }
        num = 0;
        for (int i = 0; i < fuelKnowledge.size(); i++) {
            Item item = fuelKnowledge.get(i).getLeft();

            String name = item.getName().getString();
            if (isSearching && !name.toLowerCase().contains(searchText.toLowerCase())) 
                continue; 
            
            ItemStack stack = new ItemStack(item);
            transmutationSlots.get(num + 12).setStack(stack);
            num++;
            if (num >= 4)
                break;
        }
        return;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        dropInventory(player, minorInventory);
    }


    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        if (invSlot >= 1 && invSlot < 17) {
            
            ItemStack stack = ItemStack.EMPTY;
            TransmutationSlot slot = (TransmutationSlot)this.slots.get(invSlot);
            if (slot != null && slot.hasStack()) {
                stack = slot.getStack().copy();
                
                SuperNumber itemEmc = EmcData.getItemEmc(stack.getItem());
                if (itemEmc.equalsZero())
                    return ItemStack.EMPTY;

                SuperNumber emc;
                boolean client = player.getWorld().isClient();
                if (client)
                    emc = FabricatedExchangeClient.clientEmc;
                else
                    emc = EmcData.getEmc(player);
                
                SuperNumber itemCount = new SuperNumber(emc);
                itemCount.divide(itemEmc);
                itemCount.floor();
                
                SuperNumber sMaxCount = new SuperNumber(stack.getMaxCount());
                sMaxCount = SuperNumber.min(sMaxCount, itemCount);
                int a = sMaxCount.toInt();
                stack.setCount(a);

                SuperNumber itemCost = EmcData.getItemStackEmc(stack);

                if (emc.compareTo(itemCost) != -1) {
                    if (invSlot < this.inventory.size()) {
                        if (!this.insertItem(stack, this.inventory.size(), this.slots.size(), true)) 
                            return ItemStack.EMPTY;
                    } 
                    else if (!this.insertItem(stack, 0, this.inventory.size(), false))
                        return ItemStack.EMPTY;
                    
                    if (!client) {
                        EmcData.subtractEmc(player, itemCost);
                        refreshOffering();
                    }
                    return stack;
                }
                else
                    return ItemStack.EMPTY;
            }
            return stack;
        }
        // if it is not one of those slots it must mean we're shift clicking the inventory, meaning transmute this item
        Slot slot = this.slots.get(invSlot);
        ItemStack slotItemStack = slot.getStack();
        ItemStack itemStack = emcSlot.insertStack(slotItemStack, slotItemStack.getCount());
        if (!ItemStack.areEqual(slotItemStack, itemStack)) {
            slot.setStack(ItemStack.EMPTY);
        }
        
        return ItemStack.EMPTY; // we never actually want to move anything into the slot
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }


    public void addKnowledgePair(Pair<Item, SuperNumber> pair) {
        if (knowledge.size() == 0) {
            knowledge.add(pair);
            return;
        }

        SuperNumber num = pair.getRight();
        int low = 0;
        int high = knowledge.size() - 1;
        
        while (low <= high) {
            int mid = (low + high) / 2;
            Pair<Item, SuperNumber> midPair = knowledge.get(mid);
            SuperNumber midNum = midPair.getRight();
            
            if (num.compareTo(midNum) == 1) {
                high = mid - 1;
            } 
            else if (num.compareTo(midNum) == -1) {
                low = mid + 1;
            } 
            else {
                knowledge.add(mid + 1, pair);
                return;
            }
        }
        
        knowledge.add(low, pair);
    }



    private void addTransmutationSlot(TransmutationSlot slot) {
        this.addSlot(slot);
        transmutationSlots.add(slot);
    }

    public DefaultedList<TransmutationSlot> getTransmutationSlots() {
        return transmutationSlots;
    }

}
