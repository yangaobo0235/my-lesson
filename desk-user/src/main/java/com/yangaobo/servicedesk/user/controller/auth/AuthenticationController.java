package com.yangaobo.servicedesk.user.controller.auth;

import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.user.model.form.auth.LoginForm;
import com.yangaobo.servicedesk.user.model.form.auth.PasswordForm;
import com.yangaobo.servicedesk.user.model.vo.auth.CsrfVO;
import com.yangaobo.servicedesk.user.security.CurrentActor;
import com.yangaobo.servicedesk.user.security.SessionCsrfTokenRepository;
import com.yangaobo.servicedesk.user.service.IAuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录与当前会话的业务边界。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/users/auth")
@RequiredArgsConstructor
@Tag(name = "登录与身份")
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class AuthenticationController {
    private final IAuthenticationService authenticationService;
    private final CurrentActor currentActor;
    private final SessionCsrfTokenRepository csrfTokens;
    private final HttpSessionSecurityContextRepository contexts =
            new HttpSessionSecurityContextRepository();

    /**
     * 获取当前同源会话的令牌，首次访问创建匿名会话。
     */
    @Operation(summary = "获取当前同源会话的令牌，首次访问创建匿名会话。")
    @GetMapping("/csrf")
    public Result<CsrfVO> csrf(HttpServletRequest request) {
        CsrfToken token = csrfTokens.generateToken(request);
        CsrfVO data = new CsrfVO();
        data.setHeaderName(token.getHeaderName());
        data.setToken(token.getToken());
        return Result.success(data);
    }

    /**
     * 登录成功轮换会话，旧CSRF令牌同时失效。
     */
    @Operation(summary = "登录成功轮换会话，旧CSRF令牌同时失效。")
    @PostMapping("/login")
    public Result<IdentityDTO> login(
            @Valid @RequestBody LoginForm form,
            HttpServletRequest request,
            HttpServletResponse response) {
        IdentityDTO data = authenticationService.login(form, request.getRemoteAddr());
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute("ownerId", data.getOwnerId());
        session.setAttribute("authVersion", data.getAuthVersion());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        data.getOwnerId(),
                        null,
                        List.of(new SimpleGrantedAuthority("AUTHENTICATED"))));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return Result.success(data);
    }

    /**
     * 当前身份逐次查询数据库，不返回旧角色缓存。
     */
    @Operation(summary = "当前身份逐次查询数据库，不返回旧角色缓存。")
    @GetMapping("/me")
    public Result<IdentityDTO> me() {
        IdentityDTO data = currentActor.requireIdentity();
        return Result.success(data);
    }

    /**
     * 改密成功后必须重新登录。
     */
    @Operation(summary = "改密成功后必须重新登录。")
    @PostMapping("/password")
    public Result<Void> password(
            @Valid @RequestBody PasswordForm form, HttpServletRequest request) {
        authenticationService.changePassword(currentActor.requireIdentity(), form);
        request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
        return Result.success();
    }

    /**
     * 安全幂等退出；再次退出须先获取当前匿名会话的CSRF令牌。
     */
    @Operation(summary = "安全幂等退出；再次退出须先获取当前匿名会话的CSRF令牌。")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return Result.success();
    }
}
