package io.github.lyx9527.fastai.annotation;

/**
 * 编译期 Tool 风险等级声明。
 */
public enum LLMToolRiskLevel {
    /** 只读取数据，不修改业务状态。 */
    READ_ONLY,
    /** 会修改业务状态的写操作。 */
    WRITE,
    /** 删除、取消、资金等必须显式确认的高风险操作。 */
    DANGEROUS
}
