package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.utils.ItemOverrideManager;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Slot.class)
public abstract class SlotMixin {

    @Inject(method = "getStack", at = @At("RETURN"), cancellable = true)
    private void onGetStack(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack original = cir.getReturnValue();

        ItemStack modified = ItemOverrideManager.apply(
            ScreenInfoHelper.get().getCurrInfo(),
            (Slot) (Object) this,
            original
        );

        if (original != modified) {
            cir.setReturnValue(modified);
        }
    }
}
