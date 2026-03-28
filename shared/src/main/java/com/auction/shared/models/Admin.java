package com.auction.shared.models;

public class Admin extends User {
    public Admin(String username, String password, String email) {
        super(username, password, email);
    }

    @Override
    public void displayRole() {
        System.out.println("Vai trò: Quản trị viên hệ thống (Admin) - " + username);
    }
}