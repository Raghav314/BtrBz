package com.github.lutzluca.btrbz.data;

import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product.Summary;

public class BazaarData {

    private final List<Consumer<Map<String, Product>>> listeners = new ArrayList<>();
    private Map<String, Product> lastProducts = Collections.emptyMap();

    private static Optional<Double> firstSummaryPrice(List<Summary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return Optional.empty();
        }

        return Try.of(summaries::getFirst).map(Summary::getPricePerUnit).toJavaOptional();
    }

    public void onUpdate(Map<String, Product> products) {
        this.lastProducts = products;
        for (var listener : this.listeners) {
            listener.accept(this.lastProducts);
        }
    }

    public void addListener(Consumer<Map<String, Product>> listener) {
        this.listeners.add(listener);
    }

    public Map<String, Product> getProducts() {
        return this.lastProducts;
    }

    public Optional<Double> lowestSellPrice(String productId) {
        var product = Optional.ofNullable(this.getProducts().get(productId));
        return product.flatMap(prod -> firstSummaryPrice(prod.getBuySummary()));
    }

    public Optional<Double> highestBuyPrice(String productId) {
        var product = Optional.ofNullable(this.getProducts().get(productId));
        return product.flatMap(prod -> firstSummaryPrice(prod.getSellSummary()));
    }
}
