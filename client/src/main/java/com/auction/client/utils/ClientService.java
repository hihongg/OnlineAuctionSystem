//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.auction.client.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientService {
    private final String HOST = "localhost";
    private final int PORT = 12345;

    public String sendRequest(String message) {
        try {
            String var5;
            try (
                    Socket socket = new Socket("localhost", 12345);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            ) {
                out.println(message);
                var5 = in.readLine();
            }

            return var5;
        } catch (IOException var13) {
            return "CONNECTION_ERROR";
        }
    }
}
