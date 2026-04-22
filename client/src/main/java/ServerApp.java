
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerApp {
    public static void main(String[] args) {
        int port = 12345;
        System.out.println("Server is starting on port " + port + "...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is ready and listening for connections!");

            while (true) {
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}