package com.flighteasy.dto;


import java.util.List;

public record SeatMapResponse(
        List<SeatRow> firstClass,
        List<SeatRow> business,
        List<SeatRow> economy
) {
    public static SeatMapResponseBuilder builder() {
        return new SeatMapResponseBuilder();
    }

    public static class SeatMapResponseBuilder {
        private List<SeatRow> firstClass;
        private List<SeatRow> business;
        private List<SeatRow> economy;
        public SeatMapResponseBuilder firstClass(List<SeatRow> v) {
            this.firstClass = v;
            return this;
        }
        public SeatMapResponseBuilder business(List<SeatRow> v) {
            this.business = v;
            return this;
        }
        public SeatMapResponseBuilder economy(List<SeatRow> v) {
            this.economy = v;
            return this;
        }
        public SeatMapResponse build() {
            return new SeatMapResponse(firstClass, business, economy);
        }
    }
}
