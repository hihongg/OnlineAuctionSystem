package com.auction.shared.models;

public abstract class User extends Entity {
    // Tính đóng gói: Dùng protected để các lớp con (Bidder, Seller) có thể truy cập
    protected String username;
    protected String password;
    protected String email;
    protected String role;

    public User(String username, String password, String email) {
        super(); // Gọi constructor của Entity để tự tạo ID
        this.username = username;
        this.password = password;
        this.email = email;
    }

    // Phương thức trừu tượng: Ép các lớp con phải tự định nghĩa chức năng của mình
    public abstract void displayRole();

    // Getters & Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}