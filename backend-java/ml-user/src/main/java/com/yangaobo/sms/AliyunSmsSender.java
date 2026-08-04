package com.yangaobo.sms;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponseBody;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody;
import com.yangaobo.exception.ServiceException;
import com.yangaobo.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sms.provider", havingValue = "aliyun")
public class AliyunSmsSender implements SmsSender {

    private final Client client;
    private final AliyunSmsProperties properties;

    @Override
    public void sendVerificationCode(String phone) {
        validateConfiguration();
        SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                .setCountryCode("86")
                .setPhoneNumber(phone)
                .setSignName(properties.signName())
                .setTemplateCode(properties.templateCode())
                .setTemplateParam(JSONUtil.toJsonStr(Map.of("code", "##code##", "min", "5")))
                .setCodeLength(4L)
                .setValidTime(300L)
                .setDuplicatePolicy(1L)
                .setInterval(60L)
                .setReturnVerifyCode(false)
                .setAutoRetry(1L);
        try {
            SendSmsVerifyCodeResponse response = client.sendSmsVerifyCode(request);
            SendSmsVerifyCodeResponseBody body = response.getBody();
            if (!Boolean.TRUE.equals(body.getSuccess()) || !"OK".equals(body.getCode())) {
                log.warn("阿里云短信认证提交失败: phone={}, resultCode={}, message={}, requestId={}",
                        maskPhone(phone),
                        body.getCode(),
                        body.getMessage(),
                        body.getRequestId());
                throw new ServiceException(ResultCode.SMS_SEND_FAILED, "短信发送失败，请稍后重试");
            }
            String bizId = body.getModel() == null ? null : body.getModel().getBizId();
            log.info("阿里云短信认证提交成功: phone={}, bizId={}, requestId={}",
                    maskPhone(phone),
                    bizId,
                    body.getRequestId());
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("调用阿里云短信认证服务异常: phone={}, type={}",
                    maskPhone(phone), exception.getClass().getSimpleName());
            throw new ServiceException(ResultCode.SMS_SEND_FAILED, "短信服务暂时不可用");
        }
    }

    @Override
    public boolean checkVerificationCode(String phone, String code) {
        validateConfiguration();
        CheckSmsVerifyCodeRequest request = new CheckSmsVerifyCodeRequest()
                .setCountryCode("86")
                .setPhoneNumber(phone)
                .setVerifyCode(code)
                .setCaseAuthPolicy(1L);
        try {
            CheckSmsVerifyCodeResponse response = client.checkSmsVerifyCode(request);
            CheckSmsVerifyCodeResponseBody body = response.getBody();
            if (!Boolean.TRUE.equals(body.getSuccess()) || !"OK".equals(body.getCode())) {
                log.warn("阿里云短信认证核验调用失败: phone={}, resultCode={}, message={}",
                        maskPhone(phone), body.getCode(), body.getMessage());
                throw new ServiceException(ResultCode.SMS_SEND_FAILED, "短信验证码核验服务暂时不可用");
            }
            return body.getModel() != null
                    && "PASS".equals(body.getModel().getVerifyResult());
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("调用阿里云短信验证码核验异常: phone={}, type={}",
                    maskPhone(phone), exception.getClass().getSimpleName());
            throw new ServiceException(ResultCode.SMS_SEND_FAILED, "短信验证码核验服务暂时不可用");
        }
    }

    private void validateConfiguration() {
        if (StrUtil.hasBlank(
                properties.endpoint(),
                properties.accessKeyId(),
                properties.accessKeySecret(),
                properties.signName(),
                properties.templateCode())) {
            throw new ServiceException(ResultCode.SMS_SEND_FAILED, "短信服务配置不完整");
        }
    }

    private String maskPhone(String phone) {
        return phone.length() < 7
                ? "***"
                : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
