package org.example.model;

public class Vehicle extends Item {

    public Vehicle(int id, String title, String description) {
        super(
                id,
                title,
                description,
                0.0,
                0.0,
                "",
                0,
                "OPEN"
        );
    }
}