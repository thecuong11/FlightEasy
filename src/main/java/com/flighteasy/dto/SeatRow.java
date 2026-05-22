package com.flighteasy.dto;


import java.util.List;

public record SeatRow(int rowNumber, List<SeatInfo> seats) {
    public static SeatRowBuilder builder() {
        return new SeatRowBuilder();
    }
    public static class SeatRowBuilder {
        private int rowNumber;
        private List<SeatInfo> seats;
        public SeatRowBuilder rowNumber(int v) {
            this.rowNumber = v;
            return this;
        }
        public SeatRowBuilder seats(List<SeatInfo> v) {
            this.seats = v;
            return this;
        }
        public SeatRow build() {
            return new SeatRow(rowNumber, seats);
        }
    }
}
