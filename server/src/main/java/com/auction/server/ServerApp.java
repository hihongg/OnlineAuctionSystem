package com.auction.server;

import com.auction.server.services.AuctionService;
import com.auction.shared.models.*;

import java.time.LocalDateTime;

public class ServerApp {
    public static void main(String[] args) {
        System.out.println("=== KHỞI ĐỘNG HỆ THỐNG TEST SERVER ===");
        AuctionService auctionService = new AuctionService();

        // 1. Dựng rạp: Tạo dữ liệu giả
        Seller seller = new Seller("NguoiBan_01", "123", "seller@gmail.com");
        Item item = new Item("Laptop Gaming RTX 4090", "Laptop siêu mạnh", 1000.0); // Giá khởi điểm 1000

        Auction auction = new Auction(item, seller, LocalDateTime.now(), LocalDateTime.now().plusMinutes(10));
        // Mở phiên luôn để test
        auction.setStatus(AuctionStatus.OPEN);
        auctionService.addAuction(auction);

// Lấy ID thật tự động sinh ra của hệ thống
        String realAuctionId = auction.getId();
        System.out.println("Đã tạo phiên đấu giá: " + item.getName() + " | ID: " + realAuctionId);

        // Tạo 2 người chơi "khô máu"
        Bidder bidderA = new Bidder("Hai_A", "123", "a@gmail.com");
        Bidder bidderB = new Bidder("Hoang_B", "123", "b@gmail.com");

        System.out.println("\n=== BẮT ĐẦU TEST ĐẶT GIÁ ĐỒNG THỜI ===");
        System.out.println("Tình huống: A và B cùng đặt giá 1500 vào đúng 1 thời điểm!");

        // Tạo Luồng cho người A
        Thread threadA = new Thread(() -> {
            try {
                // SỬ DỤNG realAuctionId ở đây
                auctionService.placeBid(realAuctionId, bidderA, 1500.0);
            } catch (Exception e) {
                System.out.println("[Lỗi của A] " + e.getMessage());
            }
        });

        // Tạo Luồng cho người B
        Thread threadB = new Thread(() -> {
            try {
                // SỬ DỤNG realAuctionId ở đây
                auctionService.placeBid(realAuctionId, bidderB, 1500.0);
            } catch (Exception e) {
                System.out.println("[Lỗi của B] " + e.getMessage());
            }
        });

        threadA.start();
        threadB.start();
    }
}