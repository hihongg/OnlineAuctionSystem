package com.auction.shared.models;

public class Bidder extends User {
    public Bidder(String username, String password, String email) {
        super(username, password, email);
        this.role = "BIDDER";
    }

    @Override
    public void displayRole() {
        System.out.println("Vai trò: Người tham gia đấu giá (Bidder) - " + username);
    }
}