package com.auction.shared.models;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

// Implements Serializable để sau này có thể gửi object qua Socket mạng
public abstract class Entity implements Serializable {
    // Tính đóng gói: để protected hoặc private
    protected String id;
    protected LocalDateTime createdAt;

    public Entity() {
        // Tự động sinh ID ngẫu nhiên và lấy thời gian hiện tại khi tạo mới
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }

    // Getters
    public String getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}