package com.auction.client.utils;

import java.io.*;
import java.net.Socket;

public class ClientService {

    private final String HOST = "localhost";
    private final int PORT = 12345;

    public String sendRequest(String message) {
        try (Socket socket = new Socket(HOST, PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println(message);
            return in.readLine();

        } catch (IOException e) {
            return "CONNECTION_ERROR";
        }
    }
}