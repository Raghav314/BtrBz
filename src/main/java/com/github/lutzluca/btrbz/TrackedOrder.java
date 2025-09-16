package com.github.lutzluca.btrbz;

import io.vavr.control.Try;

public class TrackedOrder {

    public final String productName;
    public final OrderType type;

    public final int volume;
    public final double pricePerUnit;
    public OrderStatus status = new OrderStatus.Unknown();


    public TrackedOrder(OrderInfo info) {
        this.productName = info.productName;
        this.type = info.type;
        this.volume = info.volume();
        this.pricePerUnit = info.pricePerUnit();
    }

    public boolean match(OrderInfo info) {
        return this.productName.equals(info.productName) && this.type == info.type
                && this.volume == info.volume
                && Double.compare(this.pricePerUnit, info.pricePerUnit) == 0;
    }

    @Override
    public String toString() {
        return String.format("(%s, name: %s, price: %.1f, volume: %d)", type, productName,
                pricePerUnit, volume);
    }

    public enum OrderType {
        Sell, Buy;

        public static Try<OrderType> tryFrom(String value) {
            return switch (value) {
                case "BUY" -> Try.success(OrderType.Buy);
                case "SELL" -> Try.success(OrderType.Sell);
                default -> Try
                        .failure(new IllegalArgumentException("Unknown order type: " + value));
            };
        }
    }

    // its like tagged unions, but with 20x more code (⊙_⊙)
    public sealed abstract static class OrderStatus permits OrderStatus.Unknown, OrderStatus.Top,
            OrderStatus.Matched, OrderStatus.Undercut {

        @Override
        public final String toString() {
            return switch (this) {
                case Unknown ignored -> "Unknown";
                case Top ignored -> "Top";
                case Matched ignored -> "Matched";
                case Undercut ignored -> "Undercut";
            };
        }

        public final boolean sameVariant(OrderStatus other) {
            return other != null && this.getClass() == other.getClass();
        }

        public static final class Unknown extends OrderStatus {
        }

        public static final class Top extends OrderStatus {
        }

        public static final class Matched extends OrderStatus {
        }

        public static final class Undercut extends OrderStatus {

            public final double amount;

            public Undercut(double amount) {
                this.amount = amount;
            }
        }
    }

    public record OrderInfo(String productName, OrderType type, int volume, double pricePerUnit,
            boolean filled) {

        public boolean notFilled() {
            return !this.filled;
        }
    }
}
