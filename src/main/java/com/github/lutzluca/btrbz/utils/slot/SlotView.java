package com.github.lutzluca.btrbz.utils.slot;

import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public record SlotView(
    @NotNull ScreenInfo currInfo,
    @NotNull ScreenInfo prevInfo,
    @NotNull ItemStack rawStack,
    int slotIdx,
    boolean playerInventorySlot
) {
}