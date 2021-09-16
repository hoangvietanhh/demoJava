package com.example.demo.entity;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class Status {
    private User user;
    private String status;
    private String message;
}
