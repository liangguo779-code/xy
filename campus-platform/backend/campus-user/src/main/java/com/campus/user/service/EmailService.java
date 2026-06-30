package com.campus.user.service;

public interface EmailService {

    void sendVerificationCode(String toEmail, String code);
}
