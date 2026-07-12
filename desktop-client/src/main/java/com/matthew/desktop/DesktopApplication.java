package com.matthew.desktop;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

/**
 * Lightweight JavaFX Desktop Client
 * 
 * This application serves as a thin UI layer that communicates exclusively
 * with REST APIs provided by the microservices backend. All business logic
 * resides in the microservices, not in this client.
 * 
 * Architecture:
 * - UI Layer: JavaFX (this application)
 * - API Layer: REST clients calling microservices
 * - Business Logic: Microservices (Identity, Market, Trading, Notification)
 */
@SpringBootApplication
public class DesktopApplication extends Application {
    private static ApplicationContext applicationContext;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Initialize Spring context
        applicationContext = SpringApplication.run(DesktopApplication.class);

        // Load main window
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-window.fxml"));
        loader.setControllerFactory(applicationContext::getBean);
        
        Scene scene = new Scene(loader.load(), 1200, 800);
        
        primaryStage.setTitle("Trading Platform - Desktop Client");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> {
            SpringApplication.exit(applicationContext);
            System.exit(0);
        });
        
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        if (applicationContext != null) {
            SpringApplication.exit(applicationContext);
        }
    }
}
