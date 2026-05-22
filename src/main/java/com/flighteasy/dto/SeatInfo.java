package com.flighteasy.dto;

import java.math.BigDecimal;

public record SeatInfo(
        String seatNumber, String position,
        Boolean isAvailable, BigDecimal extraFee, Boolean isExtraLegroom
) {
    public static SeatInfoBuilder builder() { return new SeatInfoBuilder();}

    public static class SeatInfoBuilder {
        private String seatNumber, position;
        private Boolean isAvailable, isExtraLegroom;
        private BigDecimal extraFee;
        public SeatInfoBuilder seatNumber(String v) {
            this.seatNumber = v;
            return this;
        }
        public SeatInfoBuilder position(String v) {
            this.position = v;
            return this;
        }
        public SeatInfoBuilder isAvailable(Boolean v) {
            this.isAvailable = v;
            return this;
        }
        public SeatInfoBuilder extraFee(BigDecimal v) {
            this.extraFee = v;
            return this;
        }
        public SeatInfoBuilder isExtraLegroom(Boolean v) {
            this.isExtraLegroom = v;
            return this;
        }
        public SeatInfo build() {
            return new SeatInfo(seatNumber, position, isAvailable, extraFee, isExtraLegroom);
        }
    }
}
