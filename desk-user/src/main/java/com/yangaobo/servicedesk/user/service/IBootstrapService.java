package com.yangaobo.servicedesk.user.service;

import com.yangaobo.servicedesk.user.config.BootstrapProperties;

/**
 * 受控首名管理员初始化的业务边界。
 *
 * @author yangaobo
 */
public interface IBootstrapService {
    /**
     * 仅首次建立唯一服务商、管理员及审计；已有服务商时不修改任何数据。
     */
    boolean initialize(BootstrapProperties properties);
}
