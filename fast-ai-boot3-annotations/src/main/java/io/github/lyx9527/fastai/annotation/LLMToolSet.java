package io.github.lyx9527.fastai.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个业务 Service 所属的逻辑工具集。
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface LLMToolSet {

    /**
     * 用于请求级选择的工具集唯一名称。
     *
     * @return 工具集名称
     */
    String name();

    /**
     * 工具集业务说明。
     *
     * @return 工具集说明
     */
    String description() default "";
}
