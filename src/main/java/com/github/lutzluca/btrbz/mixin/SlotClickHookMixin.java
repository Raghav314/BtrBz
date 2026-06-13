package com.github.lutzluca.btrbz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.slot.SlotClickContext;
import com.github.lutzluca.btrbz.utils.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.utils.slot.SlotInputModifiers;
import com.github.lutzluca.btrbz.utils.slot.SlotView;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class SlotClickHookMixin {

    @Inject(
        method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onSlotClicked(
        Slot slot,
        int slotId,
        int button,
        ClickType type,
        CallbackInfo ci
    ) {
        if (slot != null && this.handleSlotHook(slot, button, type)) {
            ci.cancel();
        }
    }

    private boolean handleSlotHook(Slot slot, int button, ClickType type) {
        var context = new SlotClickContext(
            this.createSlotView(slot, slot.getItem()),
            type,
            button,
            SlotInputModifiers.from(Minecraft.getInstance())
        );

        return SlotHookRegistry.handleClick(context);
    }

    private SlotView createSlotView(Slot slot, ItemStack rawStack) {
        var helper = ScreenInfoHelper.get();
        return new SlotView(
            helper.getCurrInfo(),
            helper.getPrevInfo(),
            rawStack,
            slot.getContainerSlot(),
            GameUtils.isPlayerInventorySlot(slot)
        );
    }
}
