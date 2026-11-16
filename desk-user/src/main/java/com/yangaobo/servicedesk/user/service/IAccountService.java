package com.yangaobo.servicedesk.user.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.user.model.entity.AccountEntity;
import com.yangaobo.servicedesk.user.model.form.account.AccountCreateForm;
import com.yangaobo.servicedesk.user.model.form.account.AccountRolesForm;
import com.yangaobo.servicedesk.user.model.form.account.AccountScopesForm;
import com.yangaobo.servicedesk.user.model.form.account.AccountStatusForm;
import com.yangaobo.servicedesk.user.model.form.account.AccountUpdateForm;
import com.yangaobo.servicedesk.user.model.form.auth.IdentityQueryForm;
import com.yangaobo.servicedesk.user.model.form.auth.PasswordResetForm;
import com.yangaobo.servicedesk.user.model.vo.account.AccountVO;

/**
 * 账号的业务边界。
 *
 * @author yangaobo
 */
public interface IAccountService extends IService<AccountEntity> {
    /**
     * 按当前管理员权限分页读取账号。
     */
    PageVO<AccountVO> listAccounts(IdentityDTO actor, IdentityQueryForm form);

    /**
     * 查询安全账号详情，不输出口令哈希。
     */
    AccountVO query(IdentityDTO actor, String id);

    /**
     * 管理员开通账号及角色，首次登录强制改密。
     */
    AccountVO create(IdentityDTO actor, AccountCreateForm form);

    /**
     * 调整姓名或组织，保留历史并撤销旧身份版本。
     */
    AccountVO updateAccount(IdentityDTO actor, AccountUpdateForm form);

    /**
     * 完整替换角色并处理客户范围撤销。
     */
    AccountVO setRoles(IdentityDTO actor, AccountRolesForm form);

    /**
     * 完整替换服务人员当前客户范围。
     */
    AccountVO setScopes(IdentityDTO actor, AccountScopesForm form);

    /**
     * 启停账号，所有入口共用最后管理员保护。
     */
    AccountVO setStatus(IdentityDTO actor, AccountStatusForm form, boolean enabled);

    /**
     * 重置后要求主动改密，不返回新密码。
     */
    void resetPassword(IdentityDTO actor, PasswordResetForm form);
}
