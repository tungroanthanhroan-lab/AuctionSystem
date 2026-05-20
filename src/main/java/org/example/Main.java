package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/views/login-view.fxml")
        );

        Parent root = loader.load();

        Scene scene = new Scene(root, 560, 560);

        stage.setTitle("Auction System - Login");

        try {
            stage.getIcons().add(
                    new Image(getClass().getResourceAsStream("/images/app-icon.png"))
            );
        } catch (Exception e) {
            System.out.println("Không tìm thấy app icon, dùng icon mặc định.");
        }

        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}