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
import java.util.Date;

/**
 * 术语分类树对象 t_adr_term_taxon
 *
 * 一级套一级：系统器官分类（层级1）套首选术语（层级2），首选术语再往外延伸（层级3）。
 * 术语编码一律系统发给；检索命中后直接带出上位分类码与所在层级，不劳人再抄第二遍。
 *
 * @author fuce
 * @date 2026-10-02
 */
@TableName("t_adr_term_taxon")
@ApiModel(value = "TAdrTermTaxon", description = "术语分类树")
public class TAdrTermTaxon implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 术语编码（系统发，全局唯一） */
    @TableField("term_code")
    @ApiModelProperty(value = "术语编码")
    private String termCode;

    /** 术语名号 */
    @TableField("term_name")
    @ApiModelProperty(value = "术语名号")
    private String termName;

    /** 症状词（顿号/逗号分隔，供检索） */
    @TableField("symptom_words")
    @ApiModelProperty(value = "症状词")
    private String symptomWords;

    /** 所在层级 1系统器官分类 2首选术语 3延伸术语 */
    @TableField("taxon_level")
    @ApiModelProperty(value = "所在层级 1系统器官分类 2首选术语 3延伸术语")
    private Integer taxonLevel;

    /** 上位分类码（系统器官分类层即自身术语编码） */
    @TableField("parent_code")
    @ApiModelProperty(value = "上位分类码")
    private String parentCode;

    /** 归哪一级用 1省 2市 3县（检索只摆本级） */
    @TableField("own_level")
    @ApiModelProperty(value = "归哪一级用 1省 2市 3县")
    private Integer ownLevel;

    /** 设定依据条款号（系统器官分类层立卡时要交） */
    @TableField("clause_no")
    @ApiModelProperty(value = "设定依据条款号")
    private String clauseNo;

    /** 收词年度（首选术语层立卡时要交） */
    @TableField("collect_year")
    @ApiModelProperty(value = "收词年度")
    private Integer collectYear;

    /** 情形 0现行 1停用 */
    @TableField("status")
    @ApiModelProperty(value = "情形 0现行 1停用")
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

    public String getSymptomWords() {
        return symptomWords;
    }

    public void setSymptomWords(String symptomWords) {
        this.symptomWords = symptomWords;
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

    public Integer getOwnLevel() {
        return ownLevel;
    }

    public void setOwnLevel(Integer ownLevel) {
        this.ownLevel = ownLevel;
    }

    public String getClauseNo() {
        return clauseNo;
    }

    public void setClauseNo(String clauseNo) {
        this.clauseNo = clauseNo;
    }

    public Integer getCollectYear() {
        return collectYear;
    }

    public void setCollectYear(Integer collectYear) {
        this.collectYear = collectYear;
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
