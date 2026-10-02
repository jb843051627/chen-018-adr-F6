package com.fc.v2.model.custom.adr;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 分类树检索命中一条：术语名号、症状词、上位分类码三处任一处命中即出，
 * 命中的那条直接带出它的上位分类码与所在层级，不劳人再抄第二遍。
 *
 * 检索只摆本级现行翻得着的卡，别级的卡、历史版次不同屏并列。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "TermSearchHit", description = "分类树检索命中")
public class TermSearchHit {

    @ApiModelProperty(value = "卡主键")
    private Long cardId;

    @ApiModelProperty(value = "术语编码（系统发号）")
    private String termCode;

    @ApiModelProperty(value = "术语名号")
    private String termName;

    @ApiModelProperty(value = "症状词")
    private String symptom;

    @ApiModelProperty(value = "所在层级 1系统器官分类 2首选术语 3延伸术语")
    private Integer termLevel;

    @ApiModelProperty(value = "上位分类码（命中原样带出，不劳再抄）")
    private String parentCode;

    @ApiModelProperty(value = "本级版次序")
    private Integer nodeNo;

    public TermSearchHit() {
    }

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public String getTermCode() {
        return termCode;
    }

    public void setTermCode(String termCode) {
        this.termCode = termCode;
    }

    public String getTermName() {
        return termName;
    }

    public void setTermName(String termName) {
        this.termName = termName;
    }

    public String getSymptom() {
        return symptom;
    }

    public void setSymptom(String symptom) {
        this.symptom = symptom;
    }

    public Integer getTermLevel() {
        return termLevel;
    }

    public void setTermLevel(Integer termLevel) {
        this.termLevel = termLevel;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }
}
