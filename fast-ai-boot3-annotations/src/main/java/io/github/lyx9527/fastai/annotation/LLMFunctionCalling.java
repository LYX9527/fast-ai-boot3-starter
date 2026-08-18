package io.github.lyx9527.fastai.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要转换为大模型 Tool 的业务 Service 方法。
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface LLMFunctionCalling {

    /**
     * Tool 的全局唯一名称。
     *
     * @return Tool 名称
     */
    String name();

    /**
     * 提供给大模型理解 Tool 用途的描述。
     *
     * @return Tool 描述
     */
    String description();

    /**
     * Tool 所属的兼容分组；会与 {@link LLMToolGroup} 声明合并。
     *
     * @return Tool 分组集合
     */
    String[] groups() default {};

    /**
     * 是否将 Tool 返回结果直接作为最终响应返回。
     *
     * @return 是否直接返回
     */
    boolean returnDirect() default false;
}
