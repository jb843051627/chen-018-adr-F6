package com.fc.v2.model.custom.adr;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 术语树检索命中一条：命中即带出上位分类码与所在层级，不劳人再抄第二遍。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "TermSearchHitVo", description = "术语分类检索命中")
public class TermSearchHitVo {

    @ApiModelProperty(value = "术语编码（系统发）")
    private String termCode;

    @ApiModelProperty(value = "术语名号")
    private String termName;

    @ApiModelProperty(value = "症状词")
    private String symptomWords;

    @ApiModelProperty(value = "所在层级 1系统器官分类 2首选术语 3延伸术语")
    private Integer taxonLevel;

    @ApiModelProperty(value = "上位分类码（命中直接带出）")
    private String parentCode;

    @ApiModelProperty(value = "本级 1省 2市 3县")
    private Integer ownLevel;

    public TermSearchHitVo() {
    }

    public TermSearchHitVo(String termCode, String termName, String symptomWords,
                           Integer taxonLevel, String parentCode, Integer ownLevel) {
        this.termCode = termCode;
        this.termName = termName;
        this.symptomWords = symptomWords;
        this.taxonLevel = taxonLevel;
        this.parentCode = parentCode;
        this.ownLevel = ownLevel;
    }

    /** 只摆本级翻得着的条目：空结果就给空单，不凭空补一行 */
    public static List<TermSearchHitVo> empty() {
        return new ArrayList<>();
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
}
