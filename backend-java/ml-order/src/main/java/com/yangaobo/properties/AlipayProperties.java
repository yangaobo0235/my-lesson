package com.yangaobo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付宝配置属性类，从 Nacos 配置中心或 application.yml 读取。
 *
 * @author 杨奥博
 */
@Data
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    /** 应用ID */
    private String appId;

    /** 支付宝公钥 */
    private String alipayPublicKey;

    /** 应用私钥 */
    private String merchantPrivateKey;

    /** 支付宝网关地址 */
    private String gatewayHost = "openapi-sandbox.dl.alipaydev.com";

    /** 异步通知接口（下单成功后支付宝回调） */
    private String notifyUrl;

    /** 签名类型，默认 RSA2 */
    private String signType = "RSA2";

    /** 是否忽略 SSL 证书校验 */
    private boolean ignoreSsl = false;

    public boolean isConfigured() {
        return hasRealValue(appId)
                && hasRealValue(alipayPublicKey)
                && hasRealValue(merchantPrivateKey)
                && hasRealValue(notifyUrl);
    }

    private boolean hasRealValue(String value) {
        return value != null
                && !value.isBlank()
                && !value.contains("your-")
                && !value.contains("change-me");
    }
}
