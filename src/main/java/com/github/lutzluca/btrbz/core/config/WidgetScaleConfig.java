package com.github.lutzluca.btrbz.core.config;

import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import net.minecraft.network.chat.Component;

public class WidgetScaleConfig {

    //There is a float and double version because YACL only supports Double bindings
    //But floats are more convenient to use in rendering code
    private double guiScaleDouble = 1.0d;
    public float guiScale = 1.0f;

    public Option.Builder<Double> createGuiScaleOption() {
        return Option.<Double>createBuilder()
            .name(Component.literal("Widget Scale"))
            .description(OptionDescription.of(
                Component.literal("Scales all Draggable Widgets")
            ))
            .binding(
                1.0d,
                () -> guiScaleDouble,
                scale -> {guiScaleDouble = scale;guiScale = scale.floatValue();}
            )
            .controller(opt ->
                DoubleSliderControllerBuilder.create(opt)
                    .range(0.5, 2.0)
                    .step(0.05)
            );
    }


    public OptionGroup createGroup() {
        return OptionGroup
            .createBuilder()
            .name(Component.literal("GUI Scale"))
            .description(OptionDescription.of(Component.literal("Global configuration for widget scaling.")))
            .option(this.createGuiScaleOption().build())
            .collapsed(false)
            .build();
    }
}
