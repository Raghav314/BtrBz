package com.github.lutzluca.btrbz.utils;

import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.screen.slot.Slot;

public class ScreenActionManager {

    private static final List<ScreenClickRule> rules = new ArrayList<>();

    public static void register(ScreenClickRule rule) {
        rules.add(rule);
    }

    public static boolean handleClick(ScreenInfo info, Slot slot, int button) {
        for (ScreenClickRule rule : rules) {
            if (rule.applies(info, slot, button)) {
                return rule.onClick(info, slot, button);
            }
        }

        return false;
    }

    public interface ScreenClickRule {

        boolean applies(ScreenInfo info, Slot slot, int button);

        boolean onClick(ScreenInfo info, Slot slot, int button);
    }
}
