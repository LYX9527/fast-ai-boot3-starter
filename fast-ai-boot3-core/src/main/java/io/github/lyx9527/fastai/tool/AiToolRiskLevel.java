package io.github.lyx9527.fastai.tool;

/**
 * Tool 运行时风险等级。
 */
public enum AiToolRiskLevel {
    /** 只读查询操作。 */
    READ_ONLY,
    /** 修改业务状态的写操作。 */
    WRITE,
    /** 必须显式确认的高风险操作。 */
    DANGEROUS
}
