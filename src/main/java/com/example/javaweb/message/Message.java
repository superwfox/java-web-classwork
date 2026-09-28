package com.example.javaweb.message;

public record Message(long id, String text, double latitude, double longitude,
                      String country, String createdAt, String updatedAt, long version) {}
