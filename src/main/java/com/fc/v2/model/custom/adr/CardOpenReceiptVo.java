package com.fc.v2.model.custom.adr;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 立卡回执：进不进得了库，缺在哪一栏，逐条指名道姓。
 * 光回一句"数不像话"不交差——每一条缺漏都落到具体栏位（field）与人话标签（label）。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "CardOpenReceiptVo", description = "术语分类卡立卡回执")
public class CardOpenReceiptVo {

    @ApiModelProperty(value = "是否进库")
    private boolean accepted;

    @ApiModelProperty(value = "卡主键（进库后才有）")
    private Long id;

    @ApiModelProperty(value = "系统发的分类卡号")
    private String billNo;

    @ApiModelProperty(value = "系统发的版次序")
    private Integer nodeNo;

    @ApiModelProperty(value = "卡族号（底册代号+术语编码）")
    private String familyNo;

    @ApiModelProperty(value = "系统算定的监督层级1..4")
    private Integer gradeLevel;

    @ApiModelProperty(value = "当下走到哪一步 0待核对 1已核对 2已冻住")
    private Integer status;

    @ApiModelProperty(value = "本级 1省 2市 3县")
    private Integer ownLevel;

    @ApiModelProperty(value = "缺漏栏位清单（逐条指名）")
    private List<Missing> missingFields = new ArrayList<>();

    @ApiModelProperty(value = "回执话")
    private String message;

    /** 一处缺漏：栏位名、栏位人话名、缘故 */
    public static class Missing {
        private final String field;
        private final String label;
        private final String reason;

        public Missing(String field, String label, String reason) {
            this.field = field;
            this.label = label;
            this.reason = reason;
        }

        public String getField() {
            return field;
        }

        public String getLabel() {
            return label;
        }

        public String getReason() {
            return reason;
        }
    }

    public static CardOpenReceiptVo reject(List<Missing> missing, String message) {
        CardOpenReceiptVo vo = new CardOpenReceiptVo();
        vo.accepted = false;
        vo.missingFields = missing == null ? new ArrayList<>() : missing;
        vo.message = message;
        return vo;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

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

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(Integer gradeLevel) {
        this.gradeLevel = gradeLevel;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getOwnLevel() {
        return ownLevel;
    }

    public void setOwnLevel(Integer ownLevel) {
        this.ownLevel = ownLevel;
    }

    public List<Missing> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<Missing> missingFields) {
        this.missingFields = missingFields;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
