package com.yangaobo.sms;

public interface SmsSender {

    void sendVerificationCode(String phone);

    boolean checkVerificationCode(String phone, String code);
}
