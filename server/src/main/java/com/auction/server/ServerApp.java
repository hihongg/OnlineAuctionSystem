package com.auction.server;

import com.auction.server.services.AuctionService;
import com.auction.shared.models.*;

public class ServerApp {
    public static void main(String[] args) {
        System.out.println("=== KHỞI ĐỘNG HỆ THỐNG TEST SERVER ===");
        AuctionService auctionService = new AuctionService();

        // 1. Dựng rạp: Tạo dữ liệu giả
        String itemName = "Laptop Gaming RTX 4090";
        double startingPrice = 1000.0;
        System.out.println("Đã tạo phiên đấu giá: " + itemName + " | Giá khởi điểm: " + startingPrice);

        // Tạo 2 người chơi "khô máu"
        Bidder bidderA = new Bidder("Hai_A", "123", "a@gmail.com");
        Bidder bidderB = new Bidder("Hoang_B", "123", "b@gmail.com");

        System.out.println("\n=== BẮT ĐẦU TEST ĐẶT GIÁ ĐỒNG THỜI ===");
        System.out.println("Tình huống: A và B cùng đặt giá 1500 vào đúng 1 thời điểm!");

        // Tạo Luồng cho người A
        Thread threadA = new Thread(() -> {
            try {
                auctionService.placeBid(itemName, bidderA.getUsername(), 1500.0);
            } catch (Exception e) {
                System.out.println("[Lỗi của A] " + e.getMessage());
            }
        });

        // Tạo Luồng cho người B
        Thread threadB = new Thread(() -> {
            try {
                auctionService.placeBid(itemName, bidderB.getUsername(), 1500.0);
            } catch (Exception e) {
                System.out.println("[Lỗi của B] " + e.getMessage());
            }
        });

        threadA.start();
        threadB.start();
    }
}