package com.github.lutzluca.btrbz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.github.lutzluca.btrbz.utils.slot.VirtualSlotProjection;

@Mixin(Slot.class)
public abstract class SlotItemProjectionMixin {

    @Inject(method = "getItem", at = @At("RETURN"), cancellable = true)
    private void projectItem(CallbackInfoReturnable<ItemStack> cir) {
        if (VirtualSlotProjection.isProjectionSuppressed()) {
            return;
        }

        var slot = (Slot) (Object) this;
        if (!this.isCurrentMenuSlot(slot)) {
            return;
        }

        var rawStack = cir.getReturnValue();
        var displayStack = VirtualSlotProjection.project(slot, rawStack);
        if (displayStack != rawStack) {
            cir.setReturnValue(displayStack);
        }
    }

    private boolean isCurrentMenuSlot(Slot slot) {
        var screen = Minecraft.getInstance().screen;
        return screen instanceof AbstractContainerScreen<?> containerScreen
            && containerScreen.getMenu().slots.contains(slot);
    }
}
