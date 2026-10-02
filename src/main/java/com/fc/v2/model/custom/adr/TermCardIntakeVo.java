package com.fc.v2.model.custom.adr;

import com.fc.v2.model.auto.TAdrTermCard;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 术语分类卡立卡／改版进门回执。
 *
 * 三种落点：
 * ① 进库了（accepted=true）；
 * ② 拦在门外（accepted=false，faults 逐栏指名缺在哪一栏、错在哪一栏——
 *    光回一句"数不像话"不交差，库里也不落半截账）；
 * ③ 先问一句（needConfirm=true）：开口写得比收口还晚，这一张先不进库，
 *    备一份原样回话，调用方拿着 confirm=true 再走一趟才算数。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "TermCardIntakeVo", description = "术语分类卡进门回执")
public class TermCardIntakeVo {

    @ApiModelProperty(value = "true=进库了；false=没进库（被拦或要先问一句）")
    private boolean accepted;

    @ApiModelProperty(value = "逐栏指名的问题清单")
    private List<String> faults = new ArrayList<>();

    @ApiModelProperty(value = "true=开口晚于收口，先问一句，库里暂不落账")
    private boolean needConfirm;

    @ApiModelProperty(value = "进库的那张卡（被拦/待问时为 null）")
    private TAdrTermCard card;

    @ApiModelProperty(value = "待问一句时备下的原样回话（系统发号栏仍留空，问过了才发）")
    private TermCardOpenForm pending;

    @ApiModelProperty(value = "回执一句话")
    private String message;

    public TermCardIntakeVo() {
    }

    /** 进库 */
    public static TermCardIntakeVo accepted(TAdrTermCard card, String message) {
        TermCardIntakeVo r = new TermCardIntakeVo();
        r.accepted = true;
        r.card = card;
        r.message = message;
        return r;
    }

    /** 拦在门外：faults 逐栏指名，库里不落半截账 */
    public static TermCardIntakeVo rejected(List<String> faults) {
        TermCardIntakeVo r = new TermCardIntakeVo();
        r.accepted = false;
        r.faults = faults == null ? new ArrayList<String>() : faults;
        r.message = "这张卡不进库：" + String.join("；", r.faults);
        return r;
    }

    /** 先问一句：开口晚于收口 */
    public static TermCardIntakeVo needConfirm(TermCardOpenForm pending) {
        TermCardIntakeVo r = new TermCardIntakeVo();
        r.accepted = false;
        r.needConfirm = true;
        r.pending = pending;
        r.message = "开口那一栏写得比收口还晚——先问一句：确要这么立，拿 confirm=true 再走一趟";
        return r;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public List<String> getFaults() {
        return faults;
    }

    public void setFaults(List<String> faults) {
        this.faults = faults;
    }

    public boolean isNeedConfirm() {
        return needConfirm;
    }

    public void setNeedConfirm(boolean needConfirm) {
        this.needConfirm = needConfirm;
    }

    public TAdrTermCard getCard() {
        return card;
    }

    public void setCard(TAdrTermCard card) {
        this.card = card;
    }

    public TermCardOpenForm getPending() {
        return pending;
    }

    public void setPending(TermCardOpenForm pending) {
        this.pending = pending;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
