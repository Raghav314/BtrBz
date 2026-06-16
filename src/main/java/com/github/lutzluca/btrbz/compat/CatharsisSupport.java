package com.github.lutzluca.btrbz.compat;

import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.world.item.ItemStack;

@Slf4j
public final class CatharsisSupport {

    // https://github.com/meowdding/catharsis/blob/development/docs/mod_compatibility/imc.md

    private static BiConsumer<ItemStack, Boolean> disabledConsumer = (stack, disabled) -> {};

    private CatharsisSupport() { }

    public static void disabled(BiConsumer<ItemStack, Boolean> consumer) {
        log.info("[BtrBz] Catharsis IMC connected");
        disabledConsumer = consumer;
    }

    public static ItemStack disableCatharsisModifications(ItemStack stack) {
        disabledConsumer.accept(stack, true);
        return stack;
    }
}
