package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 术语分类卡对象 t_adr_term_card
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_adr_term_card")
@ApiModel(value = "TAdrTermCard", description = "术语分类卡")
public class TAdrTermCard implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 术语分类卡号（系统发号，一卡一号，谁塞自家拟的号都不存） */
    @TableField("bill_no")
    @ApiModelProperty(value = "术语分类卡号")
    private String billNo;

    /** 本卡版次序（1 起；旧纸卡搬进来空着，照第 1 版认） */
    @TableField("node_no")
    @ApiModelProperty(value = "本卡版次序")
    private Integer nodeNo;

    /** 所属报告单位底册主键 */
    @TableField("site_id")
    @ApiModelProperty(value = "所属报告单位底册")
    private Long siteId;

    /** 所属单位底册代号（归类只认这把尺，名号读着像不算对上） */
    @TableField("site_no")
    @ApiModelProperty(value = "所属单位底册代号")
    private String siteNo;

    /** 术语编码（系统发号；同单位同分类配平看 site_no＋term_code 这一对） */
    @TableField("term_code")
    @ApiModelProperty(value = "术语编码")
    private String termCode;

    /** 术语名号（检索口） */
    @TableField("term_name")
    @ApiModelProperty(value = "术语名号")
    private String termName;

    /** 症状词（检索口） */
    @TableField("symptom")
    @ApiModelProperty(value = "症状词")
    private String symptom;

    /** 分类层级 1系统器官分类 2首选术语 3延伸术语 */
    @TableField("term_level")
    @ApiModelProperty(value = "分类层级 1系统器官分类 2首选术语 3延伸术语")
    private Integer termLevel;

    /** 上位分类码（挂系统器官分类那一级非交不可；检索命中原样带出） */
    @TableField("parent_code")
    @ApiModelProperty(value = "上位分类码")
    private String parentCode;

    /** 设定依据条款号（系统器官分类层级交） */
    @TableField("basis_clause")
    @ApiModelProperty(value = "设定依据的条款号")
    private String basisClause;

    /** 本市细化的那几条（首选术语层级交） */
    @TableField("refine_items")
    @ApiModelProperty(value = "本市细化的那几条")
    private String refineItems;

    /** 收词年度（首选术语层级交） */
    @TableField("collect_year")
    @ApiModelProperty(value = "收词的年度")
    private String collectYear;

    /** 延伸到的专业组，顿号分隔（延伸术语层级交） */
    @TableField("extend_groups")
    @ApiModelProperty(value = "延伸到哪些专业组")
    private String extendGroups;

    /** 延伸专业组一共几组（按分隔栏点出来，不设手填口） */
    @TableField("extend_group_count")
    @ApiModelProperty(value = "延伸专业组一共几组")
    private Integer extendGroupCount;

    /** 本卡年报告份数(份/年)（零、负不进库） */
    @TableField("qty")
    @ApiModelProperty(value = "本卡年报告份数(份/年)")
    private BigDecimal qty;

    /** 本卡复核时限(工作日)（负数不进库） */
    @TableField("fine_amt")
    @ApiModelProperty(value = "本卡复核时限(工作日)")
    private BigDecimal fineAmt;

    /** 开口时刻（写得比收口还晚，先问一句才收） */
    @TableField("open_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "开口时刻")
    private Date openAt;

    /** 收口时刻 */
    @TableField("close_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "收口时刻")
    private Date closeAt;

    /** 校验串（走到已核对才算封住，封过覆不掉；随版次序重算，两版共用一串就算错） */
    @TableField("content")
    @ApiModelProperty(value = "校验串（随版次序走）")
    private String content;

    /** 年报告份数折到的监督层级（系统照三条界线折，不设手工录入口） */
    @TableField("grade_level")
    @ApiModelProperty(value = "年报告份数折到的监督层级")
    private Integer gradeLevel;

    /** 现行版次标记 1现行 0历史（旧版不删，只挪进历史层备追溯） */
    @TableField("current_flag")
    @ApiModelProperty(value = "现行版次标记 1现行 0历史")
    private Integer currentFlag;

    /** 进展 0待核对 1已核对 2已冻住（旧纸卡空着照待核对认） */
    @TableField("status")
    @ApiModelProperty(value = "进展 0待核对 1已核对 2已冻住")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
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

    public Integer getExtendGroupCount() {
        return extendGroupCount;
    }

    public void setExtendGroupCount(Integer extendGroupCount) {
        this.extendGroupCount = extendGroupCount;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(Integer gradeLevel) {
        this.gradeLevel = gradeLevel;
    }

    public Integer getCurrentFlag() {
        return currentFlag;
    }

    public void setCurrentFlag(Integer currentFlag) {
        this.currentFlag = currentFlag;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
