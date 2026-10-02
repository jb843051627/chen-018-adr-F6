package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TAdrTermCard;
import com.fc.v2.model.custom.adr.TermCardIntakeVo;
import com.fc.v2.model.custom.adr.TermCardOpenForm;
import com.fc.v2.model.custom.adr.TermRosterVo;
import com.fc.v2.model.custom.adr.TermSearchHit;

import java.util.List;

/**
 * 术语分类卡 Service接口。
 *
 * 三句硬规矩：分类不重复立卡，一卡不许两挂，一册不许两家单位共用。
 * 能不能立卡、这一张眼下算哪一步、能不能再迈一步或退一步，只由本接口的实现
 * 定夺；哪几栏非写不可由它按分类层级当场点出来，旁处没有第二道门绕得过去。
 * 监督层级不设手工录入口——系统照底册三条界线折，落进卡上那个层级栏。
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITAdrTermCardService {

    /** 按主键查询（旧版次也查得着；旧纸卡空着步数/版次序的，照待核对、第 1 版认） */
    TAdrTermCard selectTAdrTermCardById(Long id);

    /** 按条件查询列表：日常翻见的只许现行那一版，历史版次不上这一屏 */
    List<TAdrTermCard> selectTAdrTermCardList(Wrapper<TAdrTermCard> queryWrapper);

    /**
     * 旧保存方法，签名原样留着：仍收实体，仍回收影响行数。
     * 自家拟的卡号/术语编码/版次序/监督层级一概不存，规矩与立卡走法同一套。
     */
    int insertTAdrTermCard(TAdrTermCard record);

    /**
     * 立卡走法（同名重载）：交表单，回执逐栏指名缺在哪一栏；
     * 卡号、术语编码由系统发，监督层级由系统折，走到哪一步由系统数。
     * 开口晚于收口时先回一句 needConfirm，不进库。
     */
    TermCardIntakeVo insertTAdrTermCard(TermCardOpenForm form);

    /** 立卡走法·确认重载：confirm=true 表示"开口晚于收口"那一问已问过，照收 */
    TermCardIntakeVo insertTAdrTermCard(TermCardOpenForm form, boolean confirm);

    /**
     * 改版：只许在冻住之前。旧版一行不删、校验串留旧串，挪进历史层；
     * 新版版次序加一、配新串，同一卡号同一术语编码，日常名录只见新版。
     */
    TermCardIntakeVo revise(Long id, TermCardOpenForm form);

    /** 改版·确认重载（开口晚于收口那一问问过了） */
    TermCardIntakeVo revise(Long id, TermCardOpenForm form, boolean confirm);

    /**
     * 往前迈一步：待核对→已核对（这一步才算封住校验串，封过覆不掉）；
     * 已核对→已冻住（走完：加不进、改不动、撤不下）。抢步、越级都不放行。
     */
    TAdrTermCard advance(Long id);

    /**
     * 退回一步重做：冻住之前只许从已核对退回待核对；先前那趟的东西保留
     * （封住的校验串不覆），不与后补的并排摆。冻住后无路可退。
     */
    TAdrTermCard rollback(Long id);

    /** 修改（旧签名）：冻住的卡改不动；改份数/换底册当场重折监督层级；
     *  卡号、术语编码、版次序、状态不由这个口改 */
    int updateTAdrTermCard(TAdrTermCard record);

    /** 批量删除（旧签名）：冻住的卡撤不下来；历史旧版一句不删 */
    int deleteTAdrTermCardByIds(String ids);

    /** 按主键删除（旧签名）：冻住的卡撤不下来 */
    int deleteTAdrTermCardById(Long id);

    /**
     * 按分类层级当场点出非写不可的栏：
     * 1 系统器官分类：上位分类码＋设定依据条款号；
     * 2 首选术语：本市细化条目＋收词年度；
     * 3 延伸术语：延伸专业组（组数系统点）。
     * 层级空着/越界给回空单并由调用方按层级空着拦。
     */
    List<String> requiredFields(Integer termLevel);

    /**
     * 分类树检索：术语名号、症状词、上位分类码三处开搜，从头匹配与中间模糊都算；
     * 命中条直接带出上位分类码与所在层级。只摆本级现行卡，别级/历史版次不同屏并列。
     * siteNo 空着则跨全部在册单位。
     */
    List<TermSearchHit> search(String siteNo, String keyword);

    /** 本级名录与占数：卡与数同一回点算；占数随立卡加一、随冻住减一 */
    TermRosterVo roster(String siteNo);

    /** 这张卡各版次（含历史层旧版），按版次序升序，备事后追溯 */
    List<TAdrTermCard> listVersions(Long id);
}
