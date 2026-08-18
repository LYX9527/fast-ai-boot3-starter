package io.github.lyx9527.fastai.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明生成 Tool 的权限、确认、风险和审计要求。
 * 方法级配置存在时整体覆盖类级配置。
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface LLMToolSecurity {

    /**
     * Tool 风险等级。
     *
     * @return 风险等级
     */
    LLMToolRiskLevel risk() default LLMToolRiskLevel.READ_ONLY;

    /**
     * 执行 Tool 所需的全部权限标识。
     *
     * @return 权限标识集合
     */
    String[] permissions() default {};

    /**
     * 是否必须由服务端确认流程确认该具体 Tool。
     *
     * @return 是否需要显式确认
     */
    boolean requireConfirmation() default false;

    /**
     * 是否记录 Tool 执行审计日志。
     *
     * @return 是否启用审计
     */
    boolean audit() default true;
}
