package com.yangaobo.servicedesk.knowledge.service;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.api.ticket.dto.SupportContextDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentRuleEntity;
import com.yangaobo.servicedesk.knowledge.model.form.DocumentRuleForm;
import java.util.Objects;

/**
 * 知识管理角色与项目、型号、环境精确适用规则。
 *
 * @author yangaobo
 */
public final class KnowledgeRules {
    /** 禁止创建无状态规则实例。 */
    private KnowledgeRules() {}
    /** 系统管理员角色不隐式授予资料管理或私有工单正文。 */
    public static void administrator(IdentityDTO actor) {
        if(actor.getMustChangePassword() || !actor.getRoles().contains("KNOWLEDGE_ADMIN"))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }
    /** 受众与问题上下文必须同时允许。 */
    public static boolean matches(DocumentRuleEntity rule, SupportContextDTO context) {
        var environment=context.getEnvironment();
        return Objects.equals(rule.getServiceItemId(),context.getServiceItemId())
            && equal(rule.getProductModelId(),context.getProductModelId(),false)
            && equal(rule.getOsName(),environment==null?null:environment.getOsName(),true)
            && equal(rule.getOsVersion(),environment==null?null:environment.getOsVersion(),false)
            && equal(rule.getSoftwareName(),environment==null?null:environment.getSoftwareName(),true)
            && equal(rule.getSoftwareVersion(),environment==null?null:environment.getSoftwareVersion(),false);
    }
    /** 未知不等于匹配；仅规则NULL明确表示不限。 */
    private static boolean equal(String required,String actual,boolean fold) {
        if(required==null)return true;
        if(actual==null || actual.isBlank())return false;
        return normalize(required,fold).equals(normalize(actual,fold));
    }
    /** 名称trim并只折叠ASCII大写，版本只trim。 */
    public static String normalize(String value,boolean fold) {
        if(value==null)return null;
        value=value.strip();
        if(value.isEmpty())throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        if(!fold)return value;
        StringBuilder result=new StringBuilder();
        for(char character:value.toCharArray())result.append(character>='A' && character<='Z'?(char)(character+32):character);
        return result.toString();
    }
    /** 校验名称与版本成对，不支持猜测版本范围。 */
    public static void normalizeRule(DocumentRuleForm form) {
        form.setOsName(normalize(form.getOsName(),true));form.setOsVersion(normalize(form.getOsVersion(),false));
        form.setSoftwareName(normalize(form.getSoftwareName(),true));form.setSoftwareVersion(normalize(form.getSoftwareVersion(),false));
        if((form.getOsVersion()!=null && form.getOsName()==null) || (form.getSoftwareVersion()!=null && form.getSoftwareName()==null))
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
}
