package io.github.lyx9527.fastai.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记由可信 ToolContext 注入、且对大模型参数 Schema 隐藏的方法参数。
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.PARAMETER)
public @interface InjectCtx {

    /**
     * ToolContext 中的字段名称；未填写时使用源代码参数名。
     *
     * @return 上下文字段名称
     */
    String value() default "";
}
