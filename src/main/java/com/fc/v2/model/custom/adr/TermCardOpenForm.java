package com.fc.v2.model.custom.adr;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 术语分类卡立卡／改版表单。
 *
 * 表单里只放该由人交的栏：底册代号、分类层级、随层级走的那几样料、
 * 年报告份数、复核时限、开口收口。以下四样不设入口，交了也不存：
 * 术语分类卡号、术语编码、版次序（一律系统发号/数出）、监督层级（照界线折）、
 * 当前走到哪一步（系统数已走完的级数）。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "TermCardOpenForm", description = "术语分类卡立卡/改版表单")
public class TermCardOpenForm implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "所属单位底册代号（归类只认这把尺）")
    private String siteNo;

    @ApiModelProperty(value = "术语名号（检索口）")
    private String termName;

    @ApiModelProperty(value = "症状词（检索口）")
    private String symptom;

    @ApiModelProperty(value = "分类层级 1系统器官分类 2首选术语 3延伸术语")
    private Integer termLevel;

    @ApiModelProperty(value = "上位分类码（挂系统器官分类那一级非交不可）")
    private String parentCode;

    @ApiModelProperty(value = "设定依据的条款号（系统器官分类层级交）")
    private String basisClause;

    @ApiModelProperty(value = "本市细化的那几条（首选术语层级交）")
    private String refineItems;

    @ApiModelProperty(value = "收词年度（首选术语层级交）")
    private String collectYear;

    @ApiModelProperty(value = "延伸到哪些专业组，顿号分隔（延伸术语层级交；组数由系统点）")
    private String extendGroups;

    @ApiModelProperty(value = "年报告份数（份/年；零、负不进库）")
    private BigDecimal qty;

    @ApiModelProperty(value = "复核时限（工作日；负数不进库）")
    private BigDecimal fineAmt;

    @ApiModelProperty(value = "开口时刻")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date openAt;

    @ApiModelProperty(value = "收口时刻")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date closeAt;

    @ApiModelProperty(value = "经办人（落创建者/更新者）")
    private String operator;

    @ApiModelProperty(value = "备注")
    private String remark;

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
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

    public String getBasisClause() {
        return basisClause;
    }

    public void setBasisClause(String basisClause) {
        this.basisClause = basisClause;
    }

    public String getRefineItems() {
        return refineItems;
    }

    public void setRefineItems(String refineItems) {
        this.refineItems = refineItems;
    }

    public String getCollectYear() {
        return collectYear;
    }

    public void setCollectYear(String collectYear) {
        this.collectYear = collectYear;
    }

    public String getExtendGroups() {
        return extendGroups;
    }

    public void setExtendGroups(String extendGroups) {
        this.extendGroups = extendGroups;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getFineAmt() {
        return fineAmt;
    }

    public void setFineAmt(BigDecimal fineAmt) {
        this.fineAmt = fineAmt;
    }

    public Date getOpenAt() {
        return openAt;
    }

    public void setOpenAt(Date openAt) {
        this.openAt = openAt;
    }

    public Date getCloseAt() {
        return closeAt;
    }

    public void setCloseAt(Date closeAt) {
        this.closeAt = closeAt;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
