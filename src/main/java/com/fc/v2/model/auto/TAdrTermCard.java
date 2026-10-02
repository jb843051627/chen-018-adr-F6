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
 * 三句规矩：分类不重复立卡，一卡不许两挂，一册不许两家单位共用。
 * 同一单位名下同一分类只认「底册代号＋术语编码」这一对；同卡族分版次（node_no），
 * 新版一立旧版即挪历史层（version_flag=1），日常只见现行那一行。
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

    /** 术语分类卡号（系统发；同卡族各版共用一个号） */
    @TableField("bill_no")
    @ApiModelProperty(value = "术语分类卡号")
    private String billNo;

    /** 本卡版次序（系统发；1起，改版加一；旧纸卡留空） */
    @TableField("node_no")
    @ApiModelProperty(value = "本卡版次序")
    private Integer nodeNo;

    /** 卡族号（底册代号+术语编码折出；配平与占名额只认这一对） */
    @TableField("family_no")
    @ApiModelProperty(value = "卡族号")
    private String familyNo;

    /** 版层 0现行 1历史 */
    @TableField("version_flag")
    @ApiModelProperty(value = "版层 0现行 1历史")
    private Integer versionFlag;

    /** 所属报告单位底册id（只作冗余；对底册只认 siteNo） */
    @TableField("site_id")
    @ApiModelProperty(value = "所属报告单位底册id")
    private Long siteId;

    /** 所属单位底册代号（归类只认这把尺，名号撞着不算） */
    @TableField("site_no")
    @ApiModelProperty(value = "所属单位底册代号")
    private String siteNo;

    /** 术语编码（系统发，指向术语分类树） */
    @TableField("term_code")
    @ApiModelProperty(value = "术语编码")
    private String termCode;

    /** 术语名号（立卡时自分类树带出留痕） */
    @TableField("term_name")
    @ApiModelProperty(value = "术语名号")
    private String termName;

    /** 分类层级 1系统器官分类 2首选术语 3延伸术语 */
    @TableField("taxon_level")
    @ApiModelProperty(value = "分类层级 1系统器官分类 2首选术语 3延伸术语")
    private Integer taxonLevel;

    /** 上位分类码（层级1须交） */
    @TableField("parent_code")
    @ApiModelProperty(value = "上位分类码")
    private String parentCode;

    /** 设定依据条款号（层级1须交） */
    @TableField("clause_no")
    @ApiModelProperty(value = "设定依据条款号")
    private String clauseNo;

    /** 本市细化那几条（层级2须交；顿号分隔） */
    @TableField("city_items")
    @ApiModelProperty(value = "本市细化条目")
    private String cityItems;

    /** 收词年度（层级2须交） */
    @TableField("collect_year")
    @ApiModelProperty(value = "收词年度")
    private Integer collectYear;

    /** 延伸到哪些专业组（层级3须交；顿号分隔） */
    @TableField("ext_groups")
    @ApiModelProperty(value = "延伸专业组名单")
    private String extGroups;

    /** 延伸专业组一共几组（层级3须交，系统照名单点数） */
    @TableField("ext_group_count")
    @ApiModelProperty(value = "延伸专业组数")
    private Integer extGroupCount;

    /** 本卡年报告份数(份/年)（不许零或负） */
    @TableField("qty")
    @ApiModelProperty(value = "本卡年报告份数(份/年)")
    private BigDecimal qty;

    /** 本卡复核时限(工作日)（不许负） */
    @TableField("fine_amt")
    @ApiModelProperty(value = "本卡复核时限(工作日)")
    private BigDecimal fineAmt;

    /** 开口时刻（不许晚于收口） */
    @TableField("valid_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "开口时刻")
    private Date validStart;

    /** 收口时刻（晚于开口才收） */
    @TableField("valid_end")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "收口时刻")
    private Date validEnd;

    /** 校验串（走到已核对封一版；改版随新版重算，两版共用一串即错；旧纸卡留空） */
    @TableField("content")
    @ApiModelProperty(value = "校验串（随版次走）")
    private String content;

    /** 年报告份数折到的监督层级1..4（系统算，栏上不设手工录入口） */
    @TableField("grade_level")
    @ApiModelProperty(value = "年报告份数折到的监督层级")
    private Integer gradeLevel;

    /** 本级 1省 2市 3县（名录/检索只摆本级） */
    @TableField("own_level")
    @ApiModelProperty(value = "本级 1省 2市 3县")
    private Integer ownLevel;

    /** 进展 0待核对 1已核对 2已冻住（由系统依已走完级数自数，旧纸卡留空） */
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

    public String getFamilyNo() {
        return familyNo;
    }

    public void setFamilyNo(String familyNo) {
        this.familyNo = familyNo;
    }

    public Integer getVersionFlag() {
        return versionFlag;
    }

    public void setVersionFlag(Integer versionFlag) {
        this.versionFlag = versionFlag;
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

    public Integer getTaxonLevel() {
        return taxonLevel;
    }

    public void setTaxonLevel(Integer taxonLevel) {
        this.taxonLevel = taxonLevel;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public String getClauseNo() {
        return clauseNo;
    }

    public void setClauseNo(String clauseNo) {
        this.clauseNo = clauseNo;
    }

    public String getCityItems() {
        return cityItems;
    }

    public void setCityItems(String cityItems) {
        this.cityItems = cityItems;
    }

    public Integer getCollectYear() {
        return collectYear;
    }

    public void setCollectYear(Integer collectYear) {
        this.collectYear = collectYear;
    }

    public String getExtGroups() {
        return extGroups;
    }

    public void setExtGroups(String extGroups) {
        this.extGroups = extGroups;
    }

    public Integer getExtGroupCount() {
        return extGroupCount;
    }

    public void setExtGroupCount(Integer extGroupCount) {
        this.extGroupCount = extGroupCount;
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

    public Date getValidStart() {
        return validStart;
    }

    public void setValidStart(Date validStart) {
        this.validStart = validStart;
    }

    public Date getValidEnd() {
        return validEnd;
    }

    public void setValidEnd(Date validEnd) {
        this.validEnd = validEnd;
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

    public Integer getOwnLevel() {
        return ownLevel;
    }

    public void setOwnLevel(Integer ownLevel) {
        this.ownLevel = ownLevel;
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
