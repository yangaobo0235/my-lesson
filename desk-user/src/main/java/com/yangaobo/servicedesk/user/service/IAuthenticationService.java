package com.yangaobo.servicedesk.user.service;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.user.model.form.auth.LoginForm;
import com.yangaobo.servicedesk.user.model.form.auth.PasswordForm;

/**
 * 登录与当前会话的业务边界。
 *
 * @author yangaobo
 */
public interface IAuthenticationService extends com.baomidou.mybatisplus.spring.service.IService<com.yangaobo.servicedesk.user.model.entity.AccountEntity> {
    /** 校验当前账号、组织和身份版本，不信任历史权限。 */
    IdentityDTO verify(String ownerId, long authVersion, String sessionRef);

    /** 防刷后校验口令并记录成功登录，返回当前事实。 */
    IdentityDTO login(LoginForm form, String remoteAddress);

    /** 改密并提高认证版本，调用方失效当前会话。 */
    void changePassword(IdentityDTO actor, PasswordForm form);
}
