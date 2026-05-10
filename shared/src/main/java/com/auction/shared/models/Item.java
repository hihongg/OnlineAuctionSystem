package com.auction.shared.models;

public class Item extends Entity {
    // Tính đóng gói (Encapsulation): private tất cả thuộc tính, dùng getter/setter [cite: 119]
    private String name;
    private String description;
    private double startingPrice;

    // Constructor
    public Item(String name, String description, double startingPrice) {
        super(); // Gọi constructor của Entity để tạo id và createdAt
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
    }

    // Tính trừu tượng và Đa hình: Ép các lớp con (như Electronics, Art) phải tự định nghĩa cách in thông tin [cite: 121]
    public void printInfo() {}

    // Getters và Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(double startingPrice) {
        if (startingPrice >= 0) {
            this.startingPrice = startingPrice;
        } else {
            throw new IllegalArgumentException("Giá khởi điểm không được âm!");
        }
    }
}