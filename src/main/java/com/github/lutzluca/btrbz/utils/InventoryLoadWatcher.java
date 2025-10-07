package com.github.lutzluca.btrbz.utils;

import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;

public class InventoryLoadWatcher {

    private final GenericContainerScreen watchedScreen;
    private final Predicate<ItemStack> loadingPredicate;
    private final Consumer<List<SlotSnapshot>> onLoaded;
    private final Runnable unsubscribeSwitch;
    private final boolean autoDisposeOnLoaded;
    private final int requiredStableTicks;
    private final int maxWaitTicks;
    private boolean tickRegistered = false;
    private int stableTicks;
    private int waitedTicks;
    private List<SlotSnapshot> lastSnapshot = null;
    private boolean loaded;
    private boolean gaveUp;

    public InventoryLoadWatcher(
        GenericContainerScreen screen,
        Consumer<List<SlotSnapshot>> onLoaded
    ) {
        this(
            screen, onLoaded, stack -> {
                if (stack == null || stack.isEmpty()) { return false; }
                return stack.getName().getString().toLowerCase().contains("loading");
            }, 3, 50, true
        );
    }

    public InventoryLoadWatcher(
        GenericContainerScreen screen,
        Consumer<List<SlotSnapshot>> onLoaded,
        Predicate<ItemStack> loadingPredicate,
        int requiredStableTicks,
        int maxWaitTicks,
        boolean autoDisposeOnLoaded
    ) {
        this.watchedScreen = screen;
        this.onLoaded = onLoaded;
        this.loadingPredicate = loadingPredicate;
        this.requiredStableTicks = requiredStableTicks;
        this.maxWaitTicks = maxWaitTicks;
        this.autoDisposeOnLoaded = autoDisposeOnLoaded;

        this.unsubscribeSwitch = ScreenInfoHelper.registerOnSwitch(this::onSwitch);
        startTicking();
    }

    private void onSwitch(ScreenInfo info) {
        if (!(info.getScreen() instanceof GenericContainerScreen gcs)) {
            dispose();
            return;
        }

        if (!gcs.equals(this.watchedScreen)) {
            dispose();
        }
    }

    private void startTicking() {
        if (!tickRegistered) {
            ClientTickDispatcher.register(this::onTick);
            tickRegistered = true;
        }
    }

    private void stopTicking() {
        if (tickRegistered) {
            ClientTickDispatcher.unregister(this::onTick);
            tickRegistered = false;
        }
    }

    private void onTick(MinecraftClient client) {
        if (this.loaded || this.gaveUp) { return; }

        var currentInfo = ScreenInfoHelper.get().getCurrInfo();
        if (currentInfo.getScreen() != this.watchedScreen) {
            dispose();
            return;
        }

        var handler = watchedScreen.getScreenHandler();
        var inv = handler.getInventory();

        waitedTicks++;
        if (waitedTicks > this.maxWaitTicks) {
            this.gaveUp = true;
            dispose();
            return;
        }

        if (StreamSupport.stream(inv.spliterator(), false).anyMatch(loadingPredicate)) {
            return;
        }

        var currSnapshot = IntStream
            .range(0, inv.size())
            .mapToObj(idx -> new SlotSnapshot(idx, inv.getStack(idx)))
            .collect(Collectors.toList());

        if (!currSnapshot.equals(this.lastSnapshot)) {
            this.lastSnapshot = currSnapshot;
            this.stableTicks = 0;
            return;
        }

        if (++this.stableTicks >= this.requiredStableTicks) {
            this.loaded = true;
            this.onLoaded.accept(currSnapshot);

            if (this.autoDisposeOnLoaded) {
                dispose();
            }
        }
    }

    private void reset() {
        this.stableTicks = 0;
        this.waitedTicks = 0;
        this.loaded = false;
        this.gaveUp = false;
        this.lastSnapshot = null;
    }

    public void dispose() {
        stopTicking();
        unsubscribeSwitch.run();
    }

    public record SlotSnapshot(int idx, ItemStack stack) {

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SlotSnapshot(int otherIdx, ItemStack otherStack))) {
                return false;
            }

            return idx == otherIdx && ItemStack.areEqual(stack, otherStack);
        }
    }
}
