package com.auction.shared.models;

public enum AuctionStatus {
    PENDING,  // Chờ mở (chưa đến giờ)
    OPEN,     // Đang diễn ra (cho phép đặt giá)
    CLOSED,   // Đã kết thúc (đã có người thắng)
    CANCELED  // Bị hủy (do Admin hủy hoặc lỗi)
}