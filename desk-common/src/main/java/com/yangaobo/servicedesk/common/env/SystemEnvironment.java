package com.yangaobo.servicedesk.common.env;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * 保存一次解析的不可变环境快照，不依赖Spring或建立外部连接。
 *
 * @author yangaobo
 */
@Getter
public final class SystemEnvironment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 唯一基础环境。
     */
    private final SystemEnvironmentEnum baseEnvironment;

    /**
     * 保存已验证环境。
     *
     * @param baseEnvironment 唯一基础环境
     */
    private SystemEnvironment(SystemEnvironmentEnum baseEnvironment) {
        this.baseEnvironment = Objects.requireNonNull(baseEnvironment);
    }

    /**
     * 从实际生效的Profile解析唯一基础环境；其他功能Profile不改变结果。
     *
     * @param profiles 应用已选择的active或default Profiles
     * @return 不可变快照
     * @throws IllegalArgumentException 未选择或同时选择两个基础环境
     */
    public static SystemEnvironment fromProfiles(String... profiles) {
        Set<String> values = Arrays.stream(profiles).collect(Collectors.toSet());
        boolean dev = values.contains(SystemEnvironmentEnum.DEV.getValue());
        boolean prod = values.contains(SystemEnvironmentEnum.PROD.getValue());
        if (dev == prod) {
            throw new IllegalArgumentException("必须且只能选择dev或prod基础环境");
        }
        return new SystemEnvironment(dev ? SystemEnvironmentEnum.DEV : SystemEnvironmentEnum.PROD);
    }
}
