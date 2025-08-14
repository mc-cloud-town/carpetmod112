package carpet.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IInteractionObject;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("NullableProblems")
public class CTECInventoryPlayer implements ITickable, IInventory, IInteractionObject {
    private final List<EntityPlayer> viewerPlayers = new ArrayList<>();
    private final EntityPlayerMP targetPlayer;
    private final InventoryPlayer targetInventory;

    private final NonNullList<ItemStack> buttons = NonNullList.withSize(13, ItemStack.EMPTY);

    private final List<NonNullList<ItemStack>> allInventories;
    private int lastHotbarIndex = 0;

    public CTECInventoryPlayer(EntityPlayerMP targetPlayer) {
        this.targetPlayer = targetPlayer;
        this.targetInventory = targetPlayer.inventory;

        this.allInventories = Arrays.asList(buttons, targetInventory.armorInventory, targetInventory.offHandInventory, targetInventory.mainInventory);

        this.setButton(buttons, 0, "Stop All actions");
        this.setButton(buttons, 1, "Not supported yet"); // Attack every 14 gt: off
        this.setButton(buttons, 2, "Not supported yet"); // Attack every continuous: off
        this.setButton(buttons, 3, "Not supported yet"); // Use continuous: off

        for (int i = 1; i < 10; i++) {
            this.setButton(buttons, i + 3, "Hotbar: " + i, i);
        }

        // Setup the hotbar buttons
        forceUpdate(targetInventory.currentItem);
    }

    private void setButton(NonNullList<ItemStack> list, int index, String name) {
        this.setButton(list, index, name, 1);
    }

    private void setButton(NonNullList<ItemStack> list, int index, String name, int count) {
        ItemStack itemStack = new ItemStack(Item.getItemFromBlock(Blocks.STRUCTURE_VOID));
        itemStack.setCount(count);
        itemStack.setStackDisplayName(name);
        list.set(index, itemStack);
    }

    public ItemStack decrStackSize(int index, int count) {
        List<ItemStack> list = null;
        for (NonNullList<ItemStack> nonnulllist : this.allInventories) {
            if (index < nonnulllist.size()) {
                list = nonnulllist;
                break;
            }

            index -= nonnulllist.size();
        }

        return list != null && !list.get(index).isEmpty() ? ItemStackHelper.getAndSplit(list, index, count) : ItemStack.EMPTY;
    }

    public ItemStack removeStackFromSlot(int index) {
        NonNullList<ItemStack> list = null;
        for (NonNullList<ItemStack> nonnulllist : this.allInventories) {
            if (index < nonnulllist.size()) {
                list = nonnulllist;
                break;
            }

            index -= nonnulllist.size();
        }

        if (list != null && !list.get(index).isEmpty()) {
            return list.set(index, ItemStack.EMPTY);
        }

        return ItemStack.EMPTY;
    }

    public void setInventorySlotContents(int index, ItemStack stack) {
        NonNullList<ItemStack> list = null;
        for (NonNullList<ItemStack> nonnulllist : this.allInventories) {
            if (index < nonnulllist.size()) {
                list = nonnulllist;
                break;
            }

            index -= nonnulllist.size();
        }

        if (list != null) {
            list.set(index, stack);
        }
    }

    public int getSizeInventory() {
        return 54;
    }

    public boolean isEmpty() {
        return false;
    }

    public ItemStack getStackInSlot(int index) {
        List<ItemStack> list = null;
        for (NonNullList<ItemStack> nonnulllist : this.allInventories) {
            if (index < nonnulllist.size()) {
                list = nonnulllist;
                break;
            }

            index -= nonnulllist.size();
        }

        return list == null ? ItemStack.EMPTY : list.get(index);
    }

    public String getName() {
        return this.targetPlayer.getName() + "'s Inventory";
    }

    public boolean hasCustomName() {
        return true;
    }

    public ITextComponent getDisplayName() {
        return new TextComponentString(this.getName());
    }

    public int getInventoryStackLimit() {
        return 64;
    }

    public void markDirty() {
        for (NonNullList<ItemStack> nonnulllist : this.allInventories) {
            for (int i = 0; i < nonnulllist.size(); i++) {
                ItemStack stack = nonnulllist.get(i);
                if (stack.getCount() == 0) {
                    nonnulllist.set(i, ItemStack.EMPTY);
                }
            }
        }
        targetInventory.markDirty();
        viewerPlayers.forEach(player -> player.inventory.markDirty());
    }

    public boolean isUsableByPlayer(EntityPlayer player) {
        return !this.targetPlayer.isDead && player.getDistanceSq(this.targetPlayer) <= 64.0D;
    }

    public void openInventory(EntityPlayer player) {
        viewerPlayers.add(player);
    }

    public void closeInventory(EntityPlayer player) {
        viewerPlayers.remove(player);
    }

    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return true;
    }

    public int getField(int id) {
        return 0;
    }

    public void setField(int id, int value) {
    }

    public int getFieldCount() {
        return 0;
    }

    public void clear() {
    }

    @Override
    public Container createContainer(InventoryPlayer playerInventory, EntityPlayer playerIn) {
        return new ContainerPlayerInventory(this, playerInventory);
    }

    @Override
    public String getGuiID() {
        return "minecraft:chest";
    }

    @Override
    public void update() {
        int currentHotbarIndex = targetInventory.currentItem;
        if (this.lastHotbarIndex != currentHotbarIndex) {
            forceUpdate(currentHotbarIndex);
        }
    }

    public void forceUpdate(int currentHotbarIndex) {
        if (currentHotbarIndex > 8 || currentHotbarIndex < 0) {
            LogManager.getLogger().warn("Invalid hotbar index: {}, resetting to 0", currentHotbarIndex);
            currentHotbarIndex = 0;
            targetInventory.currentItem = 0;
        }

        ItemStack lastButton = this.buttons.get(4 + this.lastHotbarIndex);
        if (!lastButton.isEmpty()) {
            ItemStack resetStack = new ItemStack(Item.getItemFromBlock(Blocks.STRUCTURE_VOID), lastButton.getCount());
            resetStack.setStackDisplayName(lastButton.getDisplayName());
            this.buttons.set(4 + this.lastHotbarIndex, resetStack);
        }

        ItemStack currentButton = this.buttons.get(4 + currentHotbarIndex);
        if (!currentButton.isEmpty()) {
            ItemStack barrierStack = new ItemStack(Item.getItemFromBlock(Blocks.BARRIER), currentButton.getCount());
            barrierStack.setStackDisplayName(currentButton.getDisplayName());
            this.buttons.set(4 + currentHotbarIndex, barrierStack);
        }

        viewerPlayers.forEach(player -> player.inventory.markDirty());
        this.lastHotbarIndex = currentHotbarIndex;
    }

    public static class ContainerPlayerInventory extends Container {
        private final CTECInventoryPlayer targetInv;

        public ContainerPlayerInventory(CTECInventoryPlayer targetInv, InventoryPlayer viewerInv) {
            this.targetInv = targetInv;

            targetInv.openInventory(viewerInv.player);
            // |     |  0  |  1  |  2  |  3  |  4  |  5  |  6  |  7  |  8  |
            // | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
            // |  0  | 0   | 16  | 15  | 14  | 13  | 1   | 2   | 17  | 3   |
            // |  1  | 4   | 5   | 6   | 7   | 8   | 9   | 10  | 11  | 12  |
            // |  2  | 27  | 28  | 29  | 30  | 31  | 32  | 33  | 34  | 35  |
            // |  3  | 36  | 37  | 38  | 39  | 40  | 41  | 42  | 43  | 44  |
            // |  4  | 45  | 46  | 47  | 48  | 49  | 50  | 51  | 52  | 53  |
            // |  5  | 18  | 19  | 20  | 21  | 22  | 23  | 24  | 25  | 26  |

            int addIndex = 0;
            this.addSlotToContainer(targetInv, 0, addIndex++);
            this.addSlotToContainer(targetInv, 16, addIndex++);
            this.addSlotToContainer(targetInv, 15, addIndex++);
            this.addSlotToContainer(targetInv, 14, addIndex++);
            this.addSlotToContainer(targetInv, 13, addIndex++);
            this.addSlotToContainer(targetInv, 1, addIndex++);
            this.addSlotToContainer(targetInv, 2, addIndex++);
            this.addSlotToContainer(targetInv, 17, addIndex++);

            for (int i = 0; i < 10; i++) this.addSlotToContainer(targetInv, 3 + i, addIndex++);
            for (int i = 0; i < 27; i++) this.addSlotToContainer(targetInv, 27 + i, addIndex++);
            for (int i = 0; i < 9; i++) this.addSlotToContainer(targetInv, 18 + i, addIndex++);

            for (int l = 0; l < 3; ++l) {
                for (int j1 = 0; j1 < 9; ++j1) {
                    this.addSlotToContainer(new Slot(viewerInv, j1 + l * 9 + 9, 8 + j1 * 18, 103 + l * 18 + 36));
                }
            }

            for (int i1 = 0; i1 < 9; ++i1) {
                this.addSlotToContainer(new Slot(viewerInv, i1, 8 + i1 * 18, 161 + 36));
            }
        }

        protected Slot addSlotToContainer(IInventory targetInv, int index, int addIndex) {
            return addSlotToContainer(new Slot(targetInv, index, addIndex % 9 * 18 + 8, addIndex / 9 * 18 + 18));
        }

        private boolean isUnmovableItem(int slotId) {
            if (slotId < 0 || slotId >= this.inventorySlots.size()) return false;

            return slotId == 0 || slotId == 5 || slotId == 6 || (slotId >= 8 && slotId <= 17);
        }

        @Override
        public boolean canInteractWith(EntityPlayer playerIn) {
            return playerIn.isEntityAlive();
        }

        @Override
        public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
            if (isUnmovableItem(slotId)) {
                if (slotId == 0) {
                    targetInv.targetPlayer.actionPack.stop();
                } else if (slotId >= 9 && slotId <= 17) {
                    // In tick update, we already update the hotbar index.
                    targetInv.targetInventory.currentItem = slotId - 9;
                }

                return ItemStack.EMPTY;
            }

            // Fix for high version of minecraft where the drugType is 40
            // TODO: Supper high versions?
            if (clickTypeIn == ClickType.SWAP && dragType == 40) {
                return ItemStack.EMPTY;
            }

            return super.slotClick(slotId, dragType, clickTypeIn, player);
        }

        @Override
        public void detectAndSendChanges() {
            super.detectAndSendChanges();

            for (IContainerListener listener : this.listeners) {
                listener.sendAllContents(this, this.getInventory());
            }
        }

        @Override
        public ItemStack transferStackInSlot(EntityPlayer playerIn, int slotId) {
            // Prevent shift-click duplication when viewing own inventory.
            // Lazy fix: just block it instead of properly handling self-view merge.
            // TODO: handle self-view merge properly.
            if (playerIn == this.targetInv.targetPlayer) {
                return ItemStack.EMPTY;
            }

            if (isUnmovableItem(slotId)) {
                return ItemStack.EMPTY;
            }

            Slot slot = this.inventorySlots.get(slotId);
            if (slot != null && slot.getHasStack()) {
                ItemStack slotStack = slot.getStack();
                ItemStack remainingItem = slotStack.copy();

                boolean doneFlag = false;
                if (slotId < 54) {
                    doneFlag = true;
                    this.mergeItemStack(slotStack, 54, this.inventorySlots.size(), true);
                } else if (isArmor(slotStack) != -1) {
                    int armorSlot = 1 + (3 - isArmor(slotStack));
                    doneFlag = this.mergeItemStack(slotStack, armorSlot, armorSlot + 1, false);
                } else if (slotStack.getItem() == Items.ELYTRA) {
                    doneFlag = this.mergeItemStack(slotStack, 2, 3, false);
                } else if (slotStack.getItem() instanceof ItemFood) {
                    doneFlag = moveToOffHand(this, slotStack);
                }

                if (!doneFlag && !moveToInventory(this, slotStack) && !moveToOffHand(this, slotStack)
                        && !this.mergeItemStack(slotStack, 1, 5, false)) {
                    return ItemStack.EMPTY;
                }

                if (slotStack.isEmpty()) {
                    slot.putStack(ItemStack.EMPTY);
                } else {
                    slot.onSlotChanged();
                }

                return remainingItem;
            }

            return ItemStack.EMPTY;
        }

        private static int isArmor(ItemStack stack) {
            Item item = stack.getItem();
            if (item instanceof ItemArmor) {
                return ((ItemArmor) item).armorType.getIndex();
            }
            return -1;
        }

        private static boolean moveToOffHand(ContainerPlayerInventory container, ItemStack stack) {
            return container.mergeItemStack(stack, 7, 8, false);
        }

        private static boolean moveToInventory(ContainerPlayerInventory container, ItemStack stack) {
            if (container.mergeItemStack(stack, 45, 54, false)) {
                return true;
            }
            return container.mergeItemStack(stack, 18, 45, false);
        }
    }
}
