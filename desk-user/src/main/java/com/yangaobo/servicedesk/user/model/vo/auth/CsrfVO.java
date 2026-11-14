package com.yangaobo.servicedesk.user.model.vo.auth;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 同源请求防伪令牌的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class CsrfVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 后续请求须使用的头名。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "后续请求须使用的头名。")
    private String headerName;

    /** 本次同源CSRF令牌，不在浏览器存储持久化。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "本次同源CSRF令牌，不在浏览器存储持久化。")
    private String token;
}
