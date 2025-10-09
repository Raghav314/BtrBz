package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.OrderModels.ChatFlippedOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.TimedStore;
import com.github.lutzluca.btrbz.mixin.AbstractSignEditScreenAccessor;
import com.github.lutzluca.btrbz.utils.ItemOverrideManager;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager.ScreenClickRule;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Util;
import io.vavr.control.Try;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;


// TODO: clear up this mess: I currently don't care at the horrendous mess this is
@Slf4j
public class FlipHelper {

    private static final int customHelperSlot = 16;
    private static final int flipOrderIdx = 15;

    private final TimedStore<FlipEntry> pendingFlips = new TimedStore<>(15_000L);
    private final BazaarData bazaarData;
    private String clickedProductName = null;
    private boolean pendingFlipClick = false;

    public FlipHelper(BazaarData bazaarData) {
        this.bazaarData = bazaarData;

        ScreenActionManager.register(new ScreenClickRule() {
            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                if (slot == null || button != 1) {
                    return false;
                }

                return info.inMenu(ScreenInfoHelper.BazaarMenuType.Orders);
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                // TODO: move the filter (same as the one used in BtrBz so a static field somewhere)
                final var FILTER = Set.of(
                    Items.BLACK_STAINED_GLASS_PANE,
                    Items.ARROW,
                    Items.HOPPER
                );

                record OrderTitleInfo(OrderType type, String productName) { }

                var itemStack = slot.getStack();
                var orderTitleInfo = Optional
                    .ofNullable(itemStack)
                    .filter(stack -> !stack.isEmpty() && !FILTER.contains(stack.getItem()))
                    .map(stack -> stack.getName().getString())
                    .flatMap(title -> {
                        var parts = title.split(" ", 2);
                        if (parts.length != 2) {
                            log.warn(
                                "Item title does not follow '<type> <productName>': '{}'",
                                title
                            );
                            return Optional.empty();
                        }

                        return OrderType
                            .tryFrom(parts[0].trim())
                            .onFailure(err -> log.warn(
                                "Failed to parse Order type from '{}'",
                                parts[0],
                                err
                            ))
                            .toJavaOptional()
                            .map(type -> new OrderTitleInfo(type, parts[1].trim()));
                    });

                if (orderTitleInfo.isEmpty() || orderTitleInfo.get().type != OrderType.Buy) {
                    clickedProductName = null;
                    return false;
                }

                boolean isFilled = OrderInfoParser
                    .getLore(itemStack)
                    .stream()
                    .filter(line -> line.trim().startsWith("Filled"))
                    .findFirst()
                    .map(filledStatusLine -> filledStatusLine.contains("100%"))
                    .orElse(false);

                if (!isFilled) {
                    clickedProductName = null;
                    return false;
                }

                clickedProductName = orderTitleInfo.get().productName();
                log.debug("Set pendingFlip for potential flip: {}", clickedProductName);
                return false;
            }
        });

        ItemOverrideManager.register((info, slot, original) -> {
            if (!info.inMenu(ScreenInfoHelper.BazaarMenuType.OrderOptions)) {
                return Optional.empty();
            }

            var prod = clickedProductName;
            if (slot == null || slot.getIndex() != customHelperSlot || prod == null) {
                return Optional.empty();
            }

            var lowestPrice = this.bazaarData
                .nameToId(prod)
                .flatMap(this.bazaarData::lowestSellPrice);
            if (lowestPrice.isEmpty()) {
                log.debug("No lowestSellPrice for product {}", prod);
                return Optional.empty();
            }

            return lowestPrice.map(price -> {
                var formatted = Util.formatDecimal(price - 0.1, 1, true);

                ItemStack star = new ItemStack(Items.NETHER_STAR);
                star.set(
                    DataComponentTypes.CUSTOM_NAME,
                    Text.literal(formatted).formatted(Formatting.DARK_PURPLE)
                );
                return star;
            });
        });

        ScreenActionManager.register(new ScreenClickRule() {
            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                return slot != null && slot.getIndex() == customHelperSlot && info.inMenu(
                    BazaarMenuType.OrderOptions);
            }

            // TODO: clicking the `customHelperSlot` regardless if its replaced results in
            // a click on the flip item; need to check if the item has a non empty
            // sell summary. Maybe have the BazaarData return a custom struct which registers
            // itself against the on update and store this struct instead of the productName.
            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {

                var client = MinecraftClient.getInstance();
                if (client == null) {
                    return true;
                }

                var gcsOpt = info.getGenericContainerScreen();
                if (gcsOpt.isEmpty()) {
                    return true;
                }

                var handler = gcsOpt.get().getScreenHandler();
                var syncId = handler.syncId;

                var player = client.player;
                var interactionManager = client.interactionManager;

                if (player == null || interactionManager == null) {
                    return true;
                }

                Try
                    .run(() -> interactionManager.clickSlot(
                        syncId,
                        flipOrderIdx,
                        button,
                        SlotActionType.PICKUP,
                        player
                    ))
                    .onFailure(err -> log.warn("Failed to 'click' flip order", err))
                    .onSuccess(v -> {
                        pendingFlipClick = true;
                    });

                return false;
            }
        });

        // TODO: use onSwitch here?
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var curr = ScreenInfoHelper.get().getCurrInfo();
            var prev = ScreenInfoHelper.get().getPrevInfo();

            if (prev == null || !prev.inMenu(BazaarMenuType.OrderOptions) || !pendingFlipClick) {
                return;
            }

            if (!(curr.getScreen() instanceof SignEditScreen signEditScreen)) {
                pendingFlipClick = false;
                clickedProductName = null;
                log.trace(
                    "Cleared pendingFlipClick due to screen transition from OrderOptions to a not SignEditScreen");
                return;
            }

            if (clickedProductName == null) {
                log.warn("Expected clickedProductName to be non-null");
                pendingFlipClick = false;
                return;
            }

            var flipPrice = this.bazaarData
                .nameToId(clickedProductName)
                .flatMap(this.bazaarData::lowestSellPrice)
                .map(lowest -> lowest - 0.1);

            if (flipPrice.isEmpty()) {
                log.warn("Could not resolve price for product {}", clickedProductName);
                pendingFlipClick = false;
                clickedProductName = null;
                return;
            }

            var formatted = Util.formatDecimal(flipPrice.get(), 1, false);
            var signEditScreenAccessor = (AbstractSignEditScreenAccessor) signEditScreen;
            signEditScreenAccessor.setCurrentRow(0);
            signEditScreenAccessor.invokeSetCurrentRowMessage(formatted);

            Try.run(() -> {
                signEditScreen.close();
                client.setScreen(null);
                pendingFlips.add(new FlipEntry(clickedProductName, flipPrice.get()));
            }).onFailure(err -> log.warn("Failed to finalize sign edit", err)).onSuccess(v -> {
                pendingFlipClick = false;
                clickedProductName = null;
            });
        });
    }

    public void handleFlipped(ChatFlippedOrderInfo flipped) {
        var match = this.pendingFlips.removeIfMatching(entry -> entry
            .productName()
            .equalsIgnoreCase(flipped.productName()));

        if (match.isEmpty()) {
            // this may be not necessary as after entring the price in the sign, it opens the orders
            // menu, might as well leave it atm.
            log.warn(
                "No matching pending flip for flipped order {}x {}. Orders may be out of sync",
                flipped.volume(),
                flipped.productName()
            );
            Notifier.notifyChatCommand(
                "No matching pending flip found for flipped order. Click to resync tracked orders",
                "managebazaarorders"
            );
            return;
        }

        var entry = match.get();
        double pricePerUnit = entry.pricePerUnit();

        var orderInfo = new OrderInfo(
            flipped.productName(),
            OrderType.Sell,
            flipped.volume(),
            pricePerUnit,
            false,
            -1
        );

        BtrBz.orderManager().addTrackedOrder(new TrackedOrder(orderInfo, -1));

        log.debug(
            "Added tracked Sell order from flipped chat: {}x {} at {} per unit",
            flipped.volume(),
            flipped.productName(),
            Util.formatDecimal(pricePerUnit, 1, true)
        );
    }

    private record FlipEntry(String productName, double pricePerUnit) { }
}
