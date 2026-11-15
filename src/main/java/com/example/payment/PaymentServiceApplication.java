package com.example.payment;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentServiceApplication {

    public static void main(String[] args) {
        // Windows JVM có thể báo "Asia/Saigon" (tên cũ) mà PostgreSQL 17 từ chối khi mở kết nối.
        // Đặt trước khi Spring/JDBC khởi tạo.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
