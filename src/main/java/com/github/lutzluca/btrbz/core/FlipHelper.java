package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.TimedStore;
import com.github.lutzluca.btrbz.mixin.AbstractSignEditScreenAccessor;
import com.github.lutzluca.btrbz.utils.ItemOverrideManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager.ScreenClickRule;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
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


@Slf4j
public final class FlipHelper {

    private static final TimedStore<FlipEntry> pendingFlips = new TimedStore<>(15_000L);
    private static final int customHelperSlot = 16;
    private static String clickedProductName = null;

    private FlipHelper() { }

    public static void init(BazaarData bazaarData) {
        ScreenActionManager.register(new ScreenClickRule() {

            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                if (slot == null || button != 1) {
                    clickedProductName = null;
                    return false;
                }

                return info.inMenu(ScreenInfoHelper.BazaarMenuType.Orders);
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                final var FILTER = Set.of(
                    Items.BLACK_STAINED_GLASS_PANE,
                    Items.ARROW,
                    Items.HOPPER
                );

                record OrderTitleInfo(OrderType type, String productName) { }

                var itemStack = slot.getStack();
                var orderInfo = Optional
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

                if (orderInfo.isEmpty() || orderInfo.get().type != OrderType.Buy) {
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
                    return false;
                }

                clickedProductName = orderInfo.get().productName();
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

            var lowestPrice = bazaarData.nameToId(prod).flatMap(bazaarData::lowestSellPrice);
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
                    ScreenInfoHelper.BazaarMenuType.OrderOptions);
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                final int flipOrderIdx = 15;

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
                    .onFailure(err -> log.warn("Failed to click flip order programmatically", err));

                return false;
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var curr = ScreenInfoHelper.get().getCurrInfo();
            var prev = ScreenInfoHelper.get().getPrevInfo();
            if (!(curr.getScreen() instanceof SignEditScreen signEditScreen) || prev == null || !prev.inMenu(
                ScreenInfoHelper.BazaarMenuType.OrderOptions)) {
                return;
            }

            if (clickedProductName == null) {
                log.warn("Expected clickedProductName to be non-null");
                return;
            }

            var flipPrice = bazaarData
                .nameToId(clickedProductName)
                .flatMap(bazaarData::lowestSellPrice)
                .map(lowest -> lowest - 0.1);

            if (flipPrice.isEmpty()) {
                log.warn("Could not resolve price for product {}", clickedProductName);
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
            }).onFailure(err -> log.warn("Failed to finalize sign edit", err));
        });
    }

    private record FlipEntry(String productName, double pricePerUnit) { }
}
