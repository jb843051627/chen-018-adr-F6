package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TAdrTermCard;
import com.fc.v2.model.auto.TAdrTermTaxon;
import com.fc.v2.model.custom.adr.CardOpenReceiptVo;
import com.fc.v2.model.custom.adr.CardRosterVo;
import com.fc.v2.model.custom.adr.TermSearchHitVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 术语分类卡 Service接口。
 *
 * 能不能给一个分类立卡、这一张眼下算哪一步、能不能再迈一步或退一步，只由这一层的方法定夺；
 * 哪几栏非写不可，也由它按分类层级当场点出来——旁处没有第二道门绕得过去。
 * 本级名录进几个数，一样听 {@link #roster} 交回来的结果，屏上不另点第二回。
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITAdrTermCardService {

    /** 按主键查询 */
    TAdrTermCard selectTAdrTermCardById(Long id);

    /** 按条件查询列表（分页由调用方统一处理；只照 wrapper 查，不私自添一档） */
    List<TAdrTermCard> selectTAdrTermCardList(Wrapper<TAdrTermCard> queryWrapper);

    /**
     * 旧立卡走法，签名原样留着。内部已改走新规矩——不进库就回 0，缘故看
     * {@link #insertTAdrTermCard(TAdrTermCard, int)} 的指名回执。
     */
    int insertTAdrTermCard(TAdrTermCard record);

    /**
     * 新立卡走法（同名重载）：规矩全走这一道——
     * 分类卡号/版次序系统发；必填栏按分类层级当场点，缺一栏回一指名；
     * 年报告份数靠三界线折监督层级；同单位同分类已有在走卡则回绝。
     *
     * @param ownLevel 本级 1省 2市 3县
     */
    CardOpenReceiptVo insertTAdrTermCard(TAdrTermCard record, int ownLevel);

    /** 修改（旧走法：冻住的卡一句改不得） */
    int updateTAdrTermCard(TAdrTermCard record);

    /** 批量删除（冻住的卡撤不下来） */
    int deleteTAdrTermCardByIds(String ids);

    /** 按主键删除（冻住的卡撤不下来） */
    int deleteTAdrTermCardById(Long id);

    /**
     * 按分类层级当场点非写不可的栏；另把数不像话的栏一并点出。
     * 空单表示齐了。层级没选时，先点「分类层级」这一栏。
     */
    List<CardOpenReceiptVo.Missing> pointMissing(TAdrTermCard record);

    /** 份数有增有减：三界线当场重折监督层级；冻住的卡、负数/零回绝 */
    TAdrTermCard changeQty(Long id, BigDecimal qty);

    /** 改挂靠的底册：只认底册代号，名号撞着不算；三界线照新底册重折 */
    TAdrTermCard changeSite(Long id, String siteNo);

    /**
     * 改版：新版一立，旧版随即挪进历史层备追溯（不删、不与新版同摆现行名录）；
     * 版次序系统发（加一），校验串随新版重算，新版新串、旧版留旧串。
     * 冻住的卡要动只能够另开一张，不走这道。
     */
    TAdrTermCard revise(Long id, TAdrTermCard changes);

    /** 往前迈一步（待核对→已核对→已冻住）；步子不许乱迈，迈不得回 null */
    TAdrTermCard advance(Long id);

    /** 退回一步（只有已核对退得回待核对）；冻住之后无步可退，退不得回 null */
    TAdrTermCard rollback(Long id);

    /** 这一张当下走到哪一步，由系统依已走完的级数给出，屏上录的不算 */
    TAdrTermCard currentState(Long id);

    /** 术语树登记一条（术语编码系统发；层级套层级，上位码须对得上树） */
    TAdrTermTaxon registerTaxon(TAdrTermTaxon taxon);

    /**
     * 分类树检索：术语名号、症状词、上位分类码三处都开搜，从头匹配与中间模糊都给得出；
     * 命中直接带出上位分类码与所在层级。只摆本级翻得着的条目，空着不摆空行。
     */
    List<TermSearchHitVo> searchTerms(String keyword, int ownLevel);

    /**
     * 本级名录一屏：占数随立卡加一、随冻住减一；数与这一屏的卡同一回点出来，
     * 只摆库里已有的现行卡，不凭空补一张，也不盖掉在场的旧版次。
     */
    CardRosterVo roster(int ownLevel);
}
