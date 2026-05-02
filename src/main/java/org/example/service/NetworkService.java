package org.example.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class NetworkService {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8080;

    public String sendMessage(String message) {
        try (
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        ) {
            out.println(message);
            return in.readLine();

        } catch (IOException e) {
            return "ERROR|Không kết nối được tới server";
        }
    }
}