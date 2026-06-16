package com.github.lutzluca.btrbz.utils.slot;

import java.util.function.Supplier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.github.lutzluca.btrbz.compat.CatharsisSupport;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;

public final class VirtualSlotProjection {

    private static final ThreadLocal<Integer> SUPPRESSION_DEPTH = ThreadLocal.withInitial(() -> 0);

    private VirtualSlotProjection() { }

    public static boolean isProjectionSuppressed() {
        return SUPPRESSION_DEPTH.get() > 0;
    }

    public static <T> T withProjectionSuppressed(Supplier<T> supplier) {
        int previousDepth = SUPPRESSION_DEPTH.get();
        SUPPRESSION_DEPTH.set(previousDepth + 1);
        try {
            return supplier.get();
        } finally {
            if (previousDepth == 0) {
                SUPPRESSION_DEPTH.remove();
            } else {
                SUPPRESSION_DEPTH.set(previousDepth);
            }
        }
    }

    public static ItemStack project(Slot slot, ItemStack rawStack) {
        if (VirtualSlotProjection.isProjectionSuppressed()) {
            return rawStack;
        }

        var displayStack = VirtualSlotProjection.withProjectionSuppressed(() ->
            SlotHookRegistry.getDisplayStack(new SlotRenderContext(VirtualSlotProjection.createSlotView(slot, rawStack)))
        );

        if (displayStack == rawStack) {
            return rawStack;
        }

        return CatharsisSupport.disableCatharsisModifications(displayStack);
    }

    public static SlotView createSlotView(Slot slot, ItemStack rawStack) {
        var helper = ScreenInfoHelper.get();
        return new SlotView(
            helper.getCurrInfo(),
            helper.getPrevInfo(),
            slot,
            rawStack,
            GameUtils.isPlayerInventorySlot(slot)
        );
    }
}
