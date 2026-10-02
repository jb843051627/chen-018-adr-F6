package com.fc.v2.model.custom.adr;

import com.fc.v2.model.auto.TAdrTermCard;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 本级术语分类卡名录。
 *
 * 名录上的数与这一屏摆的卡必须出自同一回点算——服务从库里现行版次逐张点，
 * 屏上不许自己数一版、也不许凭空补一张或盖掉在场的旧版次。
 * 占数随立卡加一、随冻住减一：待核对＋已核对（还没走到冻住）才算占着名额，
 * 冻住那头名额空出来，同分类才接得上新卡。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "TermRosterVo", description = "本级术语分类卡名录")
public class TermRosterVo {

    @ApiModelProperty(value = "本级单位底册代号（空=全辖）")
    private String siteNo;

    @ApiModelProperty(value = "现行版次卡（旧版次已挪历史层，不在此屏）")
    private List<TAdrTermCard> cards = new ArrayList<>();

    @ApiModelProperty(value = "现行卡总数")
    private int totalCount;

    @ApiModelProperty(value = "①待核对张数")
    private int pendingCount;

    @ApiModelProperty(value = "②已核对张数")
    private int checkedCount;

    @ApiModelProperty(value = "③已冻住张数")
    private int frozenCount;

    @ApiModelProperty(value = "占着名额的张数（①＋②：立卡加一、冻住减一）")
    private int occupyingCount;

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public List<TAdrTermCard> getCards() {
        return cards;
    }

    public void setCards(List<TAdrTermCard> cards) {
        this.cards = cards;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(int pendingCount) {
        this.pendingCount = pendingCount;
    }

    public int getCheckedCount() {
        return checkedCount;
    }

    public void setCheckedCount(int checkedCount) {
        this.checkedCount = checkedCount;
    }

    public int getFrozenCount() {
        return frozenCount;
    }

    public void setFrozenCount(int frozenCount) {
        this.frozenCount = frozenCount;
    }

    public int getOccupyingCount() {
        return occupyingCount;
    }

    public void setOccupyingCount(int occupyingCount) {
        this.occupyingCount = occupyingCount;
    }
}
