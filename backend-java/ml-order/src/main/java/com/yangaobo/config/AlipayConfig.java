package com.yangaobo.config;

import com.alipay.easysdk.kernel.Config;
import com.yangaobo.properties.AlipayProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝配置类，替代原先硬编码的 AlipayUtil。
 *
 * @author 杨奥博
 */
@Configuration
@EnableConfigurationProperties(AlipayProperties.class)
public class AlipayConfig {

    private static final Logger log = LoggerFactory.getLogger(AlipayConfig.class);

    @PostConstruct
    public void logConfig() {
        log.info("AlipayConfig 初始化完成，等待属性注入...");
    }

    /**
     * 创建支付宝 EasySDK 的 Config 单例 Bean，
     * 所有敏感配置均从 AlipayProperties（Nacos / application.yml）读取。
     */
    @Bean
    public Config alipaySdkConfig(AlipayProperties p) {
        log.info("支付宝配置加载: gatewayHost={}, appId={}, notifyUrl={}, signType={}, ignoreSsl={}",
                p.getGatewayHost(), p.getAppId(), p.getNotifyUrl(), p.getSignType(), p.isIgnoreSsl());
        log.info("支付宝配置: alipayPublicKey 长度={}, merchantPrivateKey 长度={}",
                p.getAlipayPublicKey() != null ? p.getAlipayPublicKey().length() : 0,
                p.getMerchantPrivateKey() != null ? p.getMerchantPrivateKey().length() : 0);
        Config config = new Config();
        config.protocol = "https";
        config.gatewayHost = p.getGatewayHost();
        config.signType = p.getSignType();
        config.ignoreSSL = p.isIgnoreSsl();
        config.appId = p.getAppId();
        config.alipayPublicKey = p.getAlipayPublicKey();
        config.merchantPrivateKey = p.getMerchantPrivateKey();
        config.notifyUrl = p.getNotifyUrl();
        return config;
    }
}
