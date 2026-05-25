package com.auction.shared.models;

/**
 * Sản phẩm đấu giá thuộc danh mục Nghệ thuật (Art).
 *
 * Kế thừa Item, thêm thông tin về tác giả và phong cách nghệ thuật.
 */
public class Art extends Item {

    private String artist;    // Tên nghệ sĩ / tác giả
    private String artStyle;  // Phong cách: Oil painting, Watercolor, Sculpture...
    private int    yearCreated; // Năm sáng tác

    public Art() {
        super();
    }

    public Art(int id, String name, double startingPrice) {
        super(id, name, startingPrice);
    }

    public Art(String name, String description, double startingPrice,
               long endTime, int sellerId) {
        super(name, description, startingPrice, endTime, sellerId);
    }

    /** Constructor dùng khi Seller đặt lịch đấu giá (có startTime) */
    public Art(String name, String description, double startingPrice,
               long startTime, long endTime, int sellerId) {
        super(name, description, startingPrice, startTime, endTime, sellerId);
    }

    public Art(String name, String description, double startingPrice,
               long endTime, int sellerId,
               String artist, String artStyle, int yearCreated) {
        super(name, description, startingPrice, endTime, sellerId);
        this.artist      = artist;
        this.artStyle    = artStyle;
        this.yearCreated = yearCreated;
    }

    @Override
    public String getCategory() {
        return "ART";
    }

    @Override
    public String printInfo() {
        return String.format(
                "=== NGHỆ THUẬT ===\n  Tên       : %s\n  Tác giả   : %s\n  Phong cách : %s\n  Năm sáng tác: %d\n  Giá hiện  : $%.2f\n  Trạng thái: %s",
                getName(),
                artist != null ? artist : "N/A",
                artStyle != null ? artStyle : "N/A",
                yearCreated,
                getCurrentHighestBid(),
                getStatus()
        );
    }

    public String getArtist()              { return artist; }
    public void   setArtist(String artist) { this.artist = artist; }

    public String getArtStyle()               { return artStyle; }
    public void   setArtStyle(String artStyle) { this.artStyle = artStyle; }

    public int  getYearCreated()              { return yearCreated; }
    public void setYearCreated(int yearCreated) { this.yearCreated = yearCreated; }
}