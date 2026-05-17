package com.auction.shared.models;

/**
 * Sản phẩm đấu giá thuộc danh mục Phương tiện (Vehicle).
 *
 * Kế thừa Item, thêm thông tin về hãng xe, năm sản xuất và số km.
 */
public class Vehicle extends Item {

    private String make;        // Hãng xe (Toyota, Honda, BMW...)
    private String model;       // Dòng xe (Camry, Civic, X5...)
    private int    year;        // Năm sản xuất
    private int    mileage;     // Số km đã đi (odometer)

    public Vehicle() {
        super();
    }

    public Vehicle(int id, String name, double startingPrice) {
        super(id, name, startingPrice);
    }

    public Vehicle(String name, String description, double startingPrice,
                   long endTime, int sellerId) {
        super(name, description, startingPrice, endTime, sellerId);
    }

    public Vehicle(String name, String description, double startingPrice,
                   long endTime, int sellerId,
                   String make, String model, int year, int mileage) {
        super(name, description, startingPrice, endTime, sellerId);
        this.make    = make;
        this.model   = model;
        this.year    = year;
        this.mileage = mileage;
    }

    @Override
    public String getCategory() {
        return "VEHICLE";
    }

    @Override
    public String printInfo() {
        return String.format(
                "=== PHƯƠNG TIỆN ===\n  Tên        : %s\n  Hãng/Dòng  : %s %s\n  Năm SX     : %d\n  Số km      : %,d km\n  Giá hiện   : $%.2f\n  Trạng thái : %s",
                getName(),
                make != null ? make : "N/A",
                model != null ? model : "",
                year,
                mileage,
                getCurrentHighestBid(),
                getStatus()
        );
    }

    public String getMake()             { return make; }
    public void   setMake(String make)  { this.make = make; }

    public String getModel()              { return model; }
    public void   setModel(String model)  { this.model = model; }

    public int  getYear()           { return year; }
    public void setYear(int year)   { this.year = year; }

    public int  getMileage()              { return mileage; }
    public void setMileage(int mileage)   { this.mileage = mileage; }
}
