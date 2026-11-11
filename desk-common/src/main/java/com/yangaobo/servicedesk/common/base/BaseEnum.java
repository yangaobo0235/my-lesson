package com.yangaobo.servicedesk.common.base;

/**
 * 提供有限值的稳定线值与中文描述，不改变序列化行为。
 *
 * @param <T> 线值类型
 * @author yangaobo
 */
public interface BaseEnum<T> {

    /**
     * 返回稳定值。
     *
     * @return 接口或配置中使用的值
     */
    T getValue();

    /**
     * 返回中文含义。
     *
     * @return 展示及文档说明
     */
    String getDesc();
}
