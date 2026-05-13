package org.example.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class NetworkService {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8080;

    /*
     * Gửi một message từ client JavaFX sang server.
     * Ví dụ:
     * LOGIN|username|password
     * REGISTER|username|password|role
     * BID|username|price
     */
    public String sendMessage(String message) {
        try (
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream())
                )
        ) {
            out.println(message);

            String response = in.readLine();

            if (response == null) {
                return "ERROR|Server không phản hồi!";
            }

            return response;

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR|Không kết nối được tới server!";
        }
    }
}