//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerApp {
    public static void main(String[] args) {
        int port = 12345;
        System.out.println("Server is starting on port " + port + "...");

        try {
            ServerSocket serverSocket = new ServerSocket(port);

            try {
                System.out.println("Server is ready and listening for connections!");

                while(true) {
                    Socket socket = serverSocket.accept();
                    System.out.println("A Client has connected!");
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    String request = in.readLine();
                    System.out.println("Received from Client: " + request);
                    if (request != null && request.startsWith("REGISTER")) {
                        out.println("SUCCESS");
                    } else {
                        out.println("FAIL");
                    }

                    socket.close();
                }
            } catch (Throwable var8) {
                try {
                    serverSocket.close();
                } catch (Throwable var7) {
                    var8.addSuppressed(var7);
                }

                throw var8;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
