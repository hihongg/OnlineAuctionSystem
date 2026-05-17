package com.auction.shared.models;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

// Implements Serializable để sau này có thể gửi object qua Socket mạng
public abstract class Entity implements Serializable {
    // Tính đóng gói: để protected hoặc private
    // Đổi tên field id → entityId để tránh xung đột với Item.getId() (trả về int)
    protected String entityId;
    protected LocalDateTime createdAt;

    public Entity() {
        // Tự động sinh ID ngẫu nhiên và lấy thời gian hiện tại khi tạo mới
        this.entityId = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }

    // Getters
    public String getEntityId() {
        return entityId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}