package io.github.lyx9527.fastai.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为类中的全部 Tool 或指定方法增加一个或多个选择分组。
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface LLMToolGroup {

    /**
     * 用于请求级选择 Tool 的分组名称。
     *
     * @return 分组名称集合
     */
    String[] value();
}
