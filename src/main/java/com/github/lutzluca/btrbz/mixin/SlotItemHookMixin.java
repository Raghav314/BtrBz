package com.github.lutzluca.btrbz.mixin;

import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.utils.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.utils.slot.SlotView;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SimpleContainer.class)
public class SlotItemHookMixin {

    @ModifyExpressionValue(method = "getItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;get(I)Ljava/lang/Object;"))
    public Object getStack(Object original, @Local(argsOnly = true) int slot) {
        var helper = ScreenInfoHelper.get();

        return SlotHookRegistry.replaceItem(
            new SlotRenderContext(
                new SlotView(
                    helper.getCurrInfo(),
                    helper.getPrevInfo(),
                    (ItemStack) original,
                    slot,
                    false //playerInventorySlot is set as false because SimpleContainer#getItem() will always be in a container
                )
            )
        );
    }
}