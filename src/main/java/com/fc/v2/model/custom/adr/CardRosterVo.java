package com.fc.v2.model.custom.adr;

import com.fc.v2.model.auto.TAdrTermCard;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 本级名录一屏：占数与这一屏摆着的卡同一回算出来——都从现行卡行当场点，
 * 不另存一份计数、不采信屏上自点。空着就空屏，不补空行。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "CardRosterVo", description = "本级术语卡名录")
public class CardRosterVo {

    @ApiModelProperty(value = "本级 1省 2市 3县")
    private Integer ownLevel;

    @ApiModelProperty(value = "现行版卡总数（这一屏点出来的行数）")
    private int currentCount;

    @ApiModelProperty(value = "占名额数：在走未冻住的卡（立卡+1、冻住-1）")
    private int occupiedCount;

    @ApiModelProperty(value = "已冻住卡数（名额已空出，可接新卡）")
    private int frozenCount;

    @ApiModelProperty(value = "这一屏的现行卡（只摆库里已有的，不凭空补）")
    private List<TAdrTermCard> cards = new ArrayList<>();

    public Integer getOwnLevel() {
        return ownLevel;
    }

    public void setOwnLevel(Integer ownLevel) {
        this.ownLevel = ownLevel;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public void setCurrentCount(int currentCount) {
        this.currentCount = currentCount;
    }

    public int getOccupiedCount() {
        return occupiedCount;
    }

    public void setOccupiedCount(int occupiedCount) {
        this.occupiedCount = occupiedCount;
    }

    public int getFrozenCount() {
        return frozenCount;
    }

    public void setFrozenCount(int frozenCount) {
        this.frozenCount = frozenCount;
    }

    public List<TAdrTermCard> getCards() {
        return cards;
    }

    public void setCards(List<TAdrTermCard> cards) {
        this.cards = cards;
    }
}
