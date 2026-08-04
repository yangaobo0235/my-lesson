package com.yangaobo.component;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.yangaobo.exception.ServiceException;
import com.yangaobo.result.ResultCode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * @author 杨奥博
 */
@Slf4j
@Aspect
@Component
public class ServiceAspect {

    @Around("execution(public * com.yangaobo.service.impl.*.*(..))")
	@SneakyThrows
	public Object aroundAdvice(ProceedingJoinPoint pjp) {
	
	    // 获取方法参数
	    Object[] args = pjp.getArgs();
	    // 组装完整业务方法名：类名.方法名()
	    String className = pjp.getTarget().toString();
	    className = className.substring(className.lastIndexOf('.') + 1, className.indexOf('@'));
	    String methodName = pjp.getSignature().getName();
	    methodName = className + "." + methodName + "()";
	
	    // 前置通知: 检查参数中是否存在空值
	    if (ObjectUtil.hasNull(args)) {
	        throw new ServiceException(ResultCode.ILLEGAL_PARAM, "业务方法 " + methodName + "中存在null值参数");
	    }
	
	    // 调用目标方法
	    Object returnValue = pjp.proceed(args);
	
	    // 后置通知: 记录业务层调用日志
	    log.info(String.format("""
	                    ========== 业务日志 ==========
	                    业务方法: %s
	                    业务参数: %s
	                    响应数据: %s
	                    """,
	            methodName, JSONUtil.toJsonStr(args), JSONUtil.toJsonStr(returnValue)));
	
	    // 返回目标方法的返回值
	    return returnValue;
	}
}
