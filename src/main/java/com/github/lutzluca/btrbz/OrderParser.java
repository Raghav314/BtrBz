package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.TrackedOrder.OrderInfo;
import com.github.lutzluca.btrbz.TrackedOrder.OrderType;
import io.vavr.control.Try;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class OrderParser {

    private OrderParser() { }

    public static Try<OrderInfo> parseOrder(ItemStack item, int slotIdx) {
        // name: {orderType} {productName}
        // lore lines:
        // Worth {roundedFormattedTotal} coins
        // Blank
        // Order amount / Offer amount: {volume}x
        // Optional: Filled: {filledAmount}/{volume} {filledPercentage}%!
        // Blank
        // Price per unit: {pricePerUnit} coins
        // ...
        return Try.of(() -> {
            var orderInfo = item.getName().getString().split(" ", 2);
            if (orderInfo.length != 2) {
                throw new IllegalArgumentException(
                    "Title line of item does not follow the pattern '<orderType> <productName>'");
            }

            var orderTypeResult = OrderType.tryFrom(orderInfo[0]);
            if (orderTypeResult.isFailure()) {
                throw new IllegalArgumentException(
                    "Failed to parse Order type: " + orderTypeResult.getCause().getMessage());
            }

            var productName = orderInfo[1];
            var lore = getLore(item);
            var additionalInfoOpt = getAdditionalOrderInfo(lore);
            if (additionalInfoOpt.isEmpty()) {
                throw new IllegalArgumentException(
                    "Failed to parse out the additional Order info from the lore of the item");
            }

            var details = additionalInfoOpt.get();
            return new OrderInfo(productName.trim(), orderTypeResult.get(), details.volume(),
                details.pricePerUnit(), details.filled(), slotIdx
            );
        });
    }

    private static Optional<OrderDetails> getAdditionalOrderInfo(List<String> lore) {
        Double pricePerUnit = null;
        Integer volume = null;
        Boolean filled = null;

        for (String rawLine : lore) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (pricePerUnit == null && line.startsWith("Price per unit:")) {
                var parsed = parseNumber(
                    line.replace("Price per unit:", "").replace("coins", "").trim());

                if (parsed.isFailure()) {
                    Notifier.logInfo("Failed to parse pricePerUnit: {}", parsed.getCause());
                    return Optional.empty();
                }
                pricePerUnit = parsed.get().doubleValue();
            } else if (volume == null
                && (line.startsWith("Order amount:") || line.startsWith("Offer amount:"))) {
                var parsed = parseNumber(line
                    .replace("Order amount:", "")
                    .replace("Offer amount:", "")
                    .replaceAll("x.*", "")
                    .trim());

                if (parsed.isFailure()) {
                    Notifier.logInfo("Failed to parse volume: {}", parsed.getCause());
                    return Optional.empty();
                }
                volume = parsed.get().intValue();
            } else if (filled == null && line.startsWith("Filled") && line.contains("%")) {
                filled = line.contains("100%");
            }

            if (pricePerUnit != null && volume != null && filled != null) {
                break;
            }
        }

        if (pricePerUnit != null && volume != null) {
            return Optional.of(new OrderDetails(pricePerUnit, volume, filled != null && filled));
        }
        return Optional.empty();
    }

    public static Try<SetOrderInfo> parseConfirmItem(ItemStack item) {
        // name: Buy Order / Sell Offer
        // lore lines:
        // Bazaar
        // Blank
        // Price per unit: {pricePerUnit} coins
        // Blank
        // Order / Selling: {volume}x {productName}
        // Total price / You earn: {total} coins.
        // ...
        return Try.of(() -> {
            if (item == null || item.isEmpty()) {
                throw new IllegalArgumentException("Empty item");
            }

            String title = item.getName().getString();
            var type = switch (title) {
                case "Sell Offer" -> OrderType.Sell;
                case "Buy Order" -> OrderType.Buy;
                default -> throw new IllegalArgumentException("Unknown confirm title: " + title);
            };

            var lore = getLore(item);
            Double pricePerUnit = null;
            Integer volume = null;
            String productName = null;
            Double total = null;

            for (String rawLine : lore) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (pricePerUnit == null && line.startsWith("Price per unit:")) {
                    var parsed = parseNumber(
                        line.replace("Price per unit:", "").replace("coins", "").trim());
                    if (parsed.isFailure()) {
                        throw new IllegalArgumentException(
                            "Failed to parse pricePerUnit: " + parsed.getCause().getMessage());
                    }
                    pricePerUnit = parsed.get().doubleValue();
                } else if (volume == null
                    && (line.startsWith("Order:") || line.startsWith("Selling:"))) {
                    var part = line.substring(line.indexOf(":") + 1).trim();
                    int xIdx = part.indexOf('x');
                    if (xIdx <= 0) {
                        throw new IllegalArgumentException("Invalid volume line: " + line);
                    }
                    var volStr = part.substring(0, xIdx).trim();
                    var parsed = parseNumber(volStr);
                    if (parsed.isFailure()) {
                        throw new IllegalArgumentException(
                            "Failed to parse volume: " + parsed.getCause().getMessage());
                    }
                    volume = parsed.get().intValue();
                    productName = part.substring(xIdx + 1).trim();
                } else if (total == null
                    && (line.startsWith("Total price:") || line.startsWith("You earn:"))) {
                    var parsed = parseNumber(line
                        .replace("Total price:", "")
                        .replace("You earn:", "")
                        .replace("coins", "")
                        .trim());
                    if (parsed.isFailure()) {
                        throw new IllegalArgumentException(
                            "Failed to parse total: " + parsed.getCause().getMessage());
                    }
                    total = parsed.get().doubleValue();
                }
            }

            if (pricePerUnit == null || volume == null || productName == null || total == null) {
                throw new IllegalArgumentException(
                    "Could not extract all required fields from confirm item");
            }

            return SetOrderInfo.of(productName, type, volume, pricePerUnit, total);
        });
    }

    public record FilledOrderInfo(String productName, OrderType type, int volume) {
    }

    public static Try<FilledOrderInfo> parseFilledOrderInfo(String bazaarMsg) {
        // [Bazaar] Your Buy Order / Your Sell Offer for {volume}x {productName} was filled!
        return Try.of(() -> {
            var filledMsg = bazaarMsg.replace("[Bazaar]", "").trim();

            var parts = filledMsg.replace("Your", "").trim().split(" for ");
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                    "Bazaar chat message does not follow the pattern: 'Your {order type} for <info>', msg"
                        + bazaarMsg);

            }

            var type = switch (parts[0].trim()) {
                case "Buy Order" -> OrderType.Buy;
                case "Sell Offer" -> OrderType.Sell;
                default -> throw new IllegalArgumentException("Unexpected order type: '" + parts[0]
                    + "', expected 'Buy Order Setup' or 'Sell Offer Setup'");
            };

            var xIdx = parts[1].indexOf("x");
            if (xIdx < 0) {
                throw new IllegalArgumentException(
                    "Failed to find an 'x' to denote the volume of the order");
            }

            var volume =
                OrderParser.parseNumber(parts[1].substring(0, xIdx).trim()).get().intValue();
            var productName = parts[1].substring(xIdx + 1).replace("was filled!", "").trim();

            return new FilledOrderInfo(productName, type, volume);
        });
    }

    public static Try<Number> parseNumber(String str) {
        var nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setParseIntegerOnly(false);
        return Try.of(() -> nf.parse(str.trim()));
    }

    private static List<String> getLore(ItemStack item) {
        return Optional.ofNullable(item.get(DataComponentTypes.LORE)).map(LoreComponent::lines)
                       .orElseGet(ArrayList::new).stream().map(Text::getString).toList();
    }

    private record OrderDetails(double pricePerUnit, int volume, boolean filled) {
    }
}
