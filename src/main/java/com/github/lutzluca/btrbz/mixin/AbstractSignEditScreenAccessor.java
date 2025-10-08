package com.github.lutzluca.btrbz.mixin;

import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {

    @Invoker("setCurrentRowMessage")
    void invokeSetCurrentRowMessage(String message);

    @Accessor("currentRow")
    void setCurrentRow(int row);
}
