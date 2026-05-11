package com.auction.shared.models;

import com.google.gson.Gson;

public class Message {
    private String action; // Ví dụ: "LOGIN", "BID", "NEW_BID_BROADCAST"
    private String payload; // Dữ liệu thực tế (dưới dạng chuỗi JSON)

    public Message(String action, String payload) {
        this.action = action;
        this.payload = payload;
    }

    public String getAction() { return action; }
    public String getPayload() { return payload; }

    // Chuyển đối tượng thành chuỗi JSON để gửi qua Socket
    public String toJson() {
        return new Gson().toJson(this);
    }

    // Chuyển chuỗi JSON nhận từ Socket thành đối tượng Message
    public static Message fromJson(String json) {
        return new Gson().fromJson(json, Message.class);
    }
}