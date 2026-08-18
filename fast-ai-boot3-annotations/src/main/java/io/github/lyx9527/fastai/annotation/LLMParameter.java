package io.github.lyx9527.fastai.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 描述由大模型生成并传入 Tool 的业务方法参数。
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.PARAMETER)
public @interface LLMParameter {

    /**
     * 参数业务含义，用于生成 Tool 参数 Schema。
     *
     * @return 参数描述
     */
    String description();

    /**
     * 参数是否必填。
     *
     * @return 是否必填
     */
    boolean required() default true;
}
