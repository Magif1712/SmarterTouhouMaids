package com.github.magif1712.smarter_touhou_maids.features.maid.menu;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class AutoTaskConfigMenu extends AbstractContainerMenu {
    private final EntityMaid maid;

    public AutoTaskConfigMenu(int id, Inventory inventory, FriendlyByteBuf extraData) {
        this(id, inventory, inventory.player.level().getEntity(extraData.readInt()));
    }

    public AutoTaskConfigMenu(int id, Inventory inventory, Entity entity) {
        super(InitMenus.AUTO_TASK_CONFIG_MENU.get(), id);
        if (entity instanceof EntityMaid) {
            this.maid = (EntityMaid) entity;
        } else {
            this.maid = null;
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return maid != null && maid.isAlive() && maid.distanceTo(player) < 8.0f;
    }

    public EntityMaid getMaid() {
        return maid;
    }
}