package com.auction.shared.models;

public class Seller extends User {
    public Seller(String username, String password, String email) {
        super(username, password, email);
    }

    @Override
    public void displayRole() {
        System.out.println("Vai trò: Người bán hàng (Seller) - " + username);
    }
}