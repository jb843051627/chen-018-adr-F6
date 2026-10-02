package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TAdrOrgArchMapper;
import com.fc.v2.mapper.auto.TAdrTermCardMapper;
import com.fc.v2.model.auto.TAdrOrgArch;
import com.fc.v2.model.auto.TAdrTermCard;
import com.fc.v2.model.custom.adr.TermCardIntakeVo;
import com.fc.v2.model.custom.adr.TermCardOpenForm;
import com.fc.v2.model.custom.adr.TermRosterVo;
import com.fc.v2.model.custom.adr.TermSearchHit;
import com.fc.v2.service.ITAdrTermCardService;
import com.fc.v2.util.MD5Util;
import com.fc.v2.util.SnowflakeIdWorker;
import com.fc.v2.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 术语分类卡 Service业务层处理。
 *
 * 去年省里抽查两桩旧案的断点：
 * 一、同一个「消化系统损害」，县里的卡挂在首选术语那一级、市里的卡挂在系统器官分类
 *    那一级，两张都标现行，翻着哪张用哪张——今年从立卡管起，三句硬规矩：
 *    分类不重复立卡，一卡不许两挂，一册不许两家单位共用。
 * 二、一张卡换了报告单位，名下还压着原来那本底册；年报告份数改过两回，监督层级
 *    算出两个结果——今年归类只认底册代号，监督层级只由系统照三条界线折，
 *    立卡、改份数、换底册三个当口重算，栏上不设手工录入口。
 *
 * 卡走三态：待核对→已核对→已冻住。当前停在哪一档由系统数，屏上录的、心里记的
 * 都不算；冻住即终局，加不进、改不动、撤不下，要动只够另开一张。旧版不删，
 * 新版一立旧版即挪进历史层；校验串走到已核对才封住，封过覆不掉，新版配新串。
 *
 * 从纸质簿搬进来的旧卡，步数、版次序、校验串可能空着：空步数照待核对、空版次
 * 照第 1 版、空串到核对那一步再封——不允许一碰就报错，更不允许把仍在走的旧卡
 * 当成已经停了的。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TAdrTermCardServiceImpl extends ServiceImpl<TAdrTermCardMapper, TAdrTermCard> implements ITAdrTermCardService {

    /** 分类层级 */
    private static final int LEVEL_SOC = 1;
    private static final int LEVEL_PT = 2;
    private static final int LEVEL_ET = 3;

    /** 进展三态 */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_CHECKED = 1;
    private static final int STATUS_FROZEN = 2;

    /** 现行版次标记 */
    private static final int CURRENT_YES = 1;
    private static final int CURRENT_HISTORY = 0;

    private static final int DEL_NORMAL = 0;
    private static final int DEL_SUNK = 1;

    /** 底册情形：已退出（通道封住，新卡不挂；旧卡照旧查得着） */
    private static final int ORG_EXITED = 1;

    /** 专业组分隔栏：顿号、逗号、分号（全半角）都认 */
    private static final String GROUP_SPLIT = "[、,，;；]+";

    @javax.annotation.Resource
    private TAdrOrgArchMapper adrOrgArchMapper;

    // ============================== 查询 ==============================

    @Override
    public TAdrTermCard selectTAdrTermCardById(Long id) {
        if (id == null) {
            return null;
        }
        TAdrTermCard card = this.baseMapper.selectById(id);
        if (card == null || !Integer.valueOf(DEL_NORMAL).equals(card.getDelFlag())) {
            return card == null ? null : card; // 沉底的旧行原样回，只供查账
        }
        return legacyView(card);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<TAdrTermCard> selectTAdrTermCardList(Wrapper<TAdrTermCard> queryWrapper) {
        // 日常翻见的只许最新那一版：旧版已挪历史层，不上这一屏
        QueryWrapper<TAdrTermCard> wrapper = queryWrapper instanceof QueryWrapper
                ? (QueryWrapper<TAdrTermCard>) queryWrapper
                : new QueryWrapper<TAdrTermCard>();
        // 日常翻见的只许最新那一版：旧版已挪历史层，不上这一屏
        wrapper.eq("current_flag", CURRENT_YES).eq("del_flag", DEL_NORMAL).orderByAsc("id");
        List<TAdrTermCard> rows = this.baseMapper.selectList(wrapper);
        List<TAdrTermCard> views = new ArrayList<>();
        for (TAdrTermCard r : rows) {
            views.add(legacyView(r));
        }
        return views;
    }

    @Override
    public List<TAdrTermCard> listVersions(Long id) {
        TAdrTermCard any = id == null ? null : this.baseMapper.selectById(id);
        if (any == null || StringUtils.isEmpty(any.getBillNo())) {
            return new ArrayList<>();
        }
        // 历史旧版、沉底行都一句不删，事后追溯都翻得着；版次序空着的旧纸卡排最前（照第 1 版认）
        return this.baseMapper.selectList(new QueryWrapper<TAdrTermCard>()
                .eq("bill_no", any.getBillNo())
                .orderByAsc("node_no", "id"));
    }

    // ============================== 立卡（旧签名原样留着） ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTAdrTermCard(TAdrTermCard record) {
        if (record == null) {
            return 0;
        }
        TermCardOpenForm form = new TermCardOpenForm();
        form.setSiteNo(record.getSiteNo());
        form.setTermName(record.getTermName());
        form.setSymptom(record.getSymptom());
        form.setTermLevel(record.getTermLevel());
        form.setParentCode(record.getParentCode());
        form.setBasisClause(record.getBasisClause());
        form.setRefineItems(record.getRefineItems());
        form.setCollectYear(record.getCollectYear());
        form.setExtendGroups(record.getExtendGroups());
        form.setQty(record.getQty());
        form.setFineAmt(record.getFineAmt());
        form.setOpenAt(record.getOpenAt());
        form.setCloseAt(record.getCloseAt());
        form.setOperator(record.getCreateBy());
        form.setRemark(record.getRemark());
        // 自家拟的卡号/术语编码/版次序/监督层级/状态一律不从实体收，号系统发、档系统折
        TermCardIntakeVo vo = insertTAdrTermCard(form, false);
        return vo.isAccepted() ? 1 : 0;
    }

    // ============================== 立卡（表单走法，同名重载） ==============================

    @Override
    public TermCardIntakeVo insertTAdrTermCard(TermCardOpenForm form) {
        return insertTAdrTermCard(form, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TermCardIntakeVo insertTAdrTermCard(TermCardOpenForm form, boolean confirm) {
        if (form == null) {
            return TermCardIntakeVo.rejected(Collections.singletonList("卡表单是空的，没东西可立"));
        }
        List<String> faults = new ArrayList<>();
        TAdrOrgArch arch = resolveArch(form.getSiteNo(), faults);
        validateCommon(form, faults);
        if (!faults.isEmpty()) {
            return TermCardIntakeVo.rejected(faults);
        }
        if (!confirm && form.getOpenAt() != null && form.getCloseAt() != null
                && form.getOpenAt().after(form.getCloseAt())) {
            // 开口比收口还晚：先问一句才收，库里不落半截账
            return TermCardIntakeVo.needConfirm(copyForm(form));
        }

        // 分类码由系统认：同一单位同一分类（层级＋名号＋上位码）沿用头一回发的术语编码，
        // 头一回家的分类才发新码；配平只看 site_no＋term_code，不看谁先到
        String termCode = resolveTermCode(arch.getSiteNo(), form);
        Integer occupy = this.baseMapper.selectCount(new QueryWrapper<TAdrTermCard>()
                .eq("site_no", arch.getSiteNo())
                .eq("term_code", termCode)
                .eq("current_flag", CURRENT_YES)
                .eq("del_flag", DEL_NORMAL)
                .ne("status", STATUS_FROZEN));
        if (occupy != null && occupy > 0) {
            return TermCardIntakeVo.rejected(Collections.singletonList(
                    "同一单位名下同一分类已有一张在走的卡——头一张没走到冻住之前，第二张立不起来"));
        }

        TAdrTermCard card = new TAdrTermCard();
        // 底册代号、分类卡号、术语编码三样号一律系统发，塞自家拟的号不存
        card.setBillNo("TC" + SnowflakeIdWorker.getUUID());
        card.setTermCode(termCode == null ? "TE" + SnowflakeIdWorker.getUUID() : termCode);
        card.setNodeNo(1);
        applyForm(card, form, arch);
        card.setContent(null); // 走到已核对那一步才封校验串
        card.setGradeLevel(foldGrade(form.getQty(), arch));
        card.setCurrentFlag(CURRENT_YES);
        card.setStatus(STATUS_PENDING); // 当前停在哪一档由系统数，立卡即待核对
        card.setDelFlag(DEL_NORMAL);
        this.baseMapper.insert(card);
        return TermCardIntakeVo.accepted(legacyView(card), "卡已立，停在待核对");
    }

    // ============================== 改版 ==============================

    @Override
    public TermCardIntakeVo revise(Long id, TermCardOpenForm form) {
        return revise(id, form, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TermCardIntakeVo revise(Long id, TermCardOpenForm form, boolean confirm) {
        TAdrTermCard old = loadLive(id);
        if (old == null) {
            return TermCardIntakeVo.rejected(Collections.singletonList("查无此张现行卡，改版无从改起"));
        }
        if (Integer.valueOf(STATUS_FROZEN).equals(normStatus(old.getStatus()))) {
            return TermCardIntakeVo.rejected(Collections.singletonList(
                    "这张卡已走到冻住：加不进去、改不动、也撤不下来，要动只够另开一张"));
        }
        if (form == null) {
            return TermCardIntakeVo.rejected(Collections.singletonList("改版表单是空的"));
        }
        List<String> faults = new ArrayList<>();
        TAdrOrgArch arch = resolveArch(form.getSiteNo(), faults);
        validateCommon(form, faults);
        if (!faults.isEmpty()) {
            return TermCardIntakeVo.rejected(faults);
        }
        if (!confirm && form.getOpenAt() != null && form.getCloseAt() != null
                && form.getOpenAt().after(form.getCloseAt())) {
            return TermCardIntakeVo.needConfirm(copyForm(form));
        }

        // 旧版不删：挪进历史层备事后追溯，旧行那串留旧串、一句不改
        this.baseMapper.update(null, new UpdateWrapper<TAdrTermCard>()
                .eq("id", old.getId())
                .set("current_flag", CURRENT_HISTORY));

        TAdrTermCard card = new TAdrTermCard();
        card.setBillNo(old.getBillNo());                 // 同一张卡，卡号不变
        card.setTermCode(old.getTermCode());             // 同一分类，分类码不变
        card.setNodeNo(normNodeNo(old.getNodeNo()) + 1); // 版次序加一
        applyForm(card, form, arch);
        card.setContent(null); // 新版从头走，到已核对再封新串
        card.setGradeLevel(foldGrade(form.getQty(), arch));
        card.setCurrentFlag(CURRENT_YES);
        card.setStatus(STATUS_PENDING);
        card.setDelFlag(DEL_NORMAL);
        this.baseMapper.insert(card);
        return TermCardIntakeVo.accepted(legacyView(card),
                "新版已立（第" + card.getNodeNo() + "版），旧版随即挪进历史层，日常名录只见新版");
    }

    // ============================== 三态推进/退回 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrTermCard advance(Long id) {
        TAdrTermCard card = loadLive(id);
        if (card == null) {
            return null;
        }
        int status = normStatus(card.getStatus());
        if (status == STATUS_PENDING) {
            // 待核对→已核对：这一步才算封住校验串；旧纸卡串空着，就此封上
            UpdateWrapper<TAdrTermCard> uw = new UpdateWrapper<TAdrTermCard>()
                    .eq("id", card.getId())
                    .set("status", STATUS_CHECKED)
                    .set("update_time", new Date());
            if (StringUtils.isEmpty(card.getContent())) {
                uw.set("content", checksum(card));
            } // 封过覆不掉：退回后重做，先前封住的串照旧保留
            this.baseMapper.update(null, uw);
        } else if (status == STATUS_CHECKED) {
            // 已核对→已冻住：终局。串已封在卡上，原样带着
            this.baseMapper.update(null, new UpdateWrapper<TAdrTermCard>()
                    .eq("id", card.getId())
                    .set("status", STATUS_FROZEN)
                    .set("update_time", new Date()));
        } else {
            return null; // 已冻住，没有下一步可迈
        }
        return selectTAdrTermCardById(card.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrTermCard rollback(Long id) {
        TAdrTermCard card = loadLive(id);
        if (card == null) {
            return null;
        }
        int status = normStatus(card.getStatus());
        if (status != STATUS_CHECKED) {
            return null; // 只许从已核对退回待核对；待核对无步可退，冻住后路不通
        }
        // 退回一步重做：先前那趟的东西（封住的校验串）保留，不与后补的并排摆
        this.baseMapper.update(null, new UpdateWrapper<TAdrTermCard>()
                .eq("id", card.getId())
                .set("status", STATUS_PENDING)
                .set("update_time", new Date()));
        return selectTAdrTermCardById(card.getId());
    }

    // ============================== 旧签名修改/删除 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTAdrTermCard(TAdrTermCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        TAdrTermCard card = loadLive(record.getId());
        if (card == null) {
            return 0;
        }
        if (Integer.valueOf(STATUS_FROZEN).equals(normStatus(card.getStatus()))) {
            return 0; // 冻住的卡改不动，要动另开一张
        }
        BigDecimal qty = record.getQty() == null ? card.getQty() : record.getQty();
        if (qty != null && qty.compareTo(BigDecimal.ZERO) <= 0) {
            return 0; // 年报告份数写零、写负不进库
        }
        BigDecimal fineAmt = record.getFineAmt() == null ? card.getFineAmt() : record.getFineAmt();
        if (fineAmt != null && fineAmt.compareTo(BigDecimal.ZERO) < 0) {
            return 0; // 复核时限填负数不进
        }
        Date openAt = record.getOpenAt() == null ? card.getOpenAt() : record.getOpenAt();
        Date closeAt = record.getCloseAt() == null ? card.getCloseAt() : record.getCloseAt();
        if (openAt != null && closeAt != null && openAt.after(closeAt)) {
            return 0; // 开口比收口晚须先问一句，旧口回收不了这个确认
        }
        // 换挂靠底册是重算监督层级的当口之一；新底册只认代号，且不能是已退出的
        TAdrOrgArch arch;
        boolean siteChanged = StringUtils.isNotEmpty(record.getSiteNo())
                && !record.getSiteNo().equals(card.getSiteNo());
        if (siteChanged) {
            List<String> faults = new ArrayList<>();
            arch = resolveArch(record.getSiteNo(), faults);
            if (!faults.isEmpty()) {
                return 0;
            }
        } else {
            arch = adrOrgArchMapper.selectOne(new QueryWrapper<TAdrOrgArch>()
                    .eq("site_no", card.getSiteNo()).eq("del_flag", DEL_NORMAL).last("limit 1"));
        }
        boolean qtyChanged = record.getQty() != null
                && (card.getQty() == null || record.getQty().compareTo(card.getQty()) != 0);

        UpdateWrapper<TAdrTermCard> uw = new UpdateWrapper<TAdrTermCard>()
                .eq("id", card.getId())
                .set("qty", qty)
                .set("fine_amt", fineAmt)
                .set("open_at", openAt)
                .set("close_at", closeAt)
                .set("update_time", new Date());
        if (record.getTermName() != null) {
            uw.set("term_name", record.getTermName());
        }
        if (record.getSymptom() != null) {
            uw.set("symptom", record.getSymptom());
        }
        if (record.getTermLevel() != null) {
            uw.set("term_level", record.getTermLevel());
        }
        if (record.getParentCode() != null) {
            uw.set("parent_code", record.getParentCode());
        }
        if (record.getBasisClause() != null) {
            uw.set("basis_clause", record.getBasisClause());
        }
        if (record.getRefineItems() != null) {
            uw.set("refine_items", record.getRefineItems());
        }
        if (record.getCollectYear() != null) {
            uw.set("collect_year", record.getCollectYear());
        }
        if (record.getExtendGroups() != null) {
            String[] groups = splitGroups(record.getExtendGroups());
            uw.set("extend_groups", joinGroups(groups));
            uw.set("extend_group_count", groups.length);
        }
        if (record.getRemark() != null) {
            uw.set("remark", record.getRemark());
        }
        if (siteChanged) {
            uw.set("site_id", arch.getId()).set("site_no", arch.getSiteNo());
        }
        // 卡号、术语编码、版次序、校验串、现行标记、状态都不由这个口改
        if (qtyChanged || siteChanged) {
            Integer grade = arch == null ? null : foldGrade(qty, arch);
            uw.set("grade_level", grade); // 份数增减、换底册当场重折，栏不给人手填
        }
        return this.baseMapper.update(null, uw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTAdrTermCardByIds(String ids) {
        int n = 0;
        for (Long id : Arrays.asList(ConvertUtil.toLongArray(ids))) {
            n += deleteTAdrTermCardById(id);
        }
        return n;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTAdrTermCardById(Long id) {
        TAdrTermCard card = loadLive(id);
        if (card == null) {
            return 0;
        }
        if (Integer.valueOf(STATUS_FROZEN).equals(normStatus(card.getStatus()))) {
            return 0; // 冻住的卡撤不下来
        }
        // 在走的卡撤下：同卡号各版（含历史层旧版）整批沉底，一句不毁、名额让出
        return this.baseMapper.update(null, new UpdateWrapper<TAdrTermCard>()
                .eq("bill_no", card.getBillNo())
                .eq("del_flag", DEL_NORMAL)
                .set("del_flag", DEL_SUNK)
                .set("update_time", new Date()));
    }

    // ============================== 层级点栏/检索/名录 ==============================

    @Override
    public List<String> requiredFields(Integer termLevel) {
        if (termLevel == null) {
            return new ArrayList<>();
        }
        switch (termLevel) {
            case LEVEL_SOC:
                // 挂系统器官分类那一级：交出上位那一条的分类码与设定依据的条款号
                return new ArrayList<>(Arrays.asList("parentCode", "basisClause"));
            case LEVEL_PT:
                // 挂首选术语那一级：交出本市细化的那几条与收词年度
                return new ArrayList<>(Arrays.asList("refineItems", "collectYear"));
            case LEVEL_ET:
                // 挂延伸术语那一级：交出延伸到哪些专业组（几组由系统点）
                return new ArrayList<>(Collections.singletonList("extendGroups"));
            default:
                return new ArrayList<>();
        }
    }

    @Override
    public List<TermSearchHit> search(String siteNo, String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return new ArrayList<>(); // 空着不摆空行，更不凭空补一张
        }
        String kw = keyword.trim();
        QueryWrapper<TAdrTermCard> wrapper = new QueryWrapper<TAdrTermCard>()
                .eq("current_flag", CURRENT_YES)
                .eq("del_flag", DEL_NORMAL);
        if (StringUtils.isNotEmpty(siteNo)) {
            wrapper.eq("site_no", siteNo); // 检索只摆本级翻得着的，别级的卡不同屏并列
        }
        // 术语名号、症状词、上位分类码三处开搜；从头匹配与中间模糊都给得出
        // （中间模糊本就盖得住从头匹配，三处同走 contains）
        wrapper.and(w -> w.like("term_name", kw).or().like("symptom", kw).or().like("parent_code", kw))
                .orderByAsc("term_level", "id");
        List<TermSearchHit> hits = new ArrayList<>();
        for (TAdrTermCard c : this.baseMapper.selectList(wrapper)) {
            TermSearchHit hit = new TermSearchHit();
            hit.setCardId(c.getId());
            hit.setTermCode(c.getTermCode());
            hit.setTermName(c.getTermName());
            hit.setSymptom(c.getSymptom());
            hit.setTermLevel(c.getTermLevel());
            hit.setParentCode(c.getParentCode()); // 命中直接带上位码，不劳人再抄第二遍
            hit.setNodeNo(normNodeNo(c.getNodeNo()));
            hits.add(hit);
        }
        return hits;
    }

    @Override
    public TermRosterVo roster(String siteNo) {
        QueryWrapper<TAdrTermCard> wrapper = new QueryWrapper<TAdrTermCard>()
                .eq("current_flag", CURRENT_YES)
                .eq("del_flag", DEL_NORMAL);
        if (StringUtils.isNotEmpty(siteNo)) {
            wrapper.eq("site_no", siteNo);
        }
        List<TAdrTermCard> rows = this.baseMapper.selectList(wrapper.orderByAsc("id"));

        TermRosterVo vo = new TermRosterVo();
        vo.setSiteNo(siteNo);
        int pending = 0;
        int checked = 0;
        int frozen = 0;
        List<TAdrTermCard> views = new ArrayList<>();
        for (TAdrTermCard r : rows) {
            views.add(legacyView(r)); // 这一屏只摆库里已有的卡，不凭空补、不盖旧版次
            int status = normStatus(r.getStatus()); // 步数空着的旧纸卡照在走（待核对）认
            if (status == STATUS_FROZEN) {
                frozen++;
            } else if (status == STATUS_CHECKED) {
                checked++;
            } else {
                pending++;
            }
        }
        vo.setCards(views);
        vo.setTotalCount(rows.size());
        vo.setPendingCount(pending);
        vo.setCheckedCount(checked);
        vo.setFrozenCount(frozen);
        // 名录占数与屏上卡同一回点算：立卡加一、冻住减一
        vo.setOccupyingCount(pending + checked);
        return vo;
    }

    // ============================== 内里规矩 ==============================

    /** 现行在册行（历史版次、沉底行都不算） */
    private TAdrTermCard loadLive(Long id) {
        if (id == null) {
            return null;
        }
        TAdrTermCard card = this.baseMapper.selectById(id);
        if (card == null || !Integer.valueOf(DEL_NORMAL).equals(card.getDelFlag())
                || !Integer.valueOf(CURRENT_YES).equals(card.getCurrentFlag() == null ? CURRENT_YES : card.getCurrentFlag())) {
            return null;
        }
        return card;
    }

    /** 归类只认底册代号这一把尺：名号读着像不算对上，也不靠人手工核一遍 */
    private TAdrOrgArch resolveArch(String siteNo, List<String> faults) {
        if (StringUtils.isEmpty(siteNo)) {
            faults.add("报告单位底册代号（siteNo）空着——一册对一家，归类只认代号这把尺");
            return null;
        }
        TAdrOrgArch arch = this.adrOrgArchMapper.selectOne(new QueryWrapper<TAdrOrgArch>()
                .eq("site_no", siteNo.trim())
                .eq("del_flag", DEL_NORMAL)
                .last("limit 1"));
        if (arch == null) {
            faults.add("底册代号「" + siteNo + "」在册子里查无此家——名号读着像不算对上");
            return null;
        }
        if (Integer.valueOf(ORG_EXITED).equals(arch.getStatus())) {
            // 年内退出随即封住通道：新卡不再往里挂；先前所挂旧卡照旧查得着
            faults.add("底册「" + siteNo + "」年内已退出，通道封住，新卡不再往里挂");
        }
        return arch;
    }

    /** 随层级走的进门校验，缺栏逐栏指名；光回一句"数不像话"不交差 */
    private void validateCommon(TermCardOpenForm form, List<String> faults) {
        if (StringUtils.isEmpty(form.getTermName())) {
            faults.add("术语名号（termName）空着——分类树检索也要靠它认门");
        }
        Integer level = form.getTermLevel();
        if (level == null) {
            faults.add("分类层级（termLevel）空着没选，卡不进库");
        } else if (level < LEVEL_SOC || level > LEVEL_ET) {
            faults.add("分类层级（termLevel=" + level + "）只认 1系统器官分类/2首选术语/3延伸术语");
        } else {
            if (level == LEVEL_SOC) {
                if (StringUtils.isEmpty(form.getParentCode())) {
                    faults.add("上位分类码（parentCode）空着——挂系统器官分类那一级，上位那一条的分类码非交不可");
                }
                if (StringUtils.isEmpty(form.getBasisClause())) {
                    faults.add("设定依据条款号（basisClause）空着——挂系统器官分类那一级非交不可");
                }
            } else if (level == LEVEL_PT) {
                if (StringUtils.isEmpty(form.getRefineItems())) {
                    faults.add("本市细化条目（refineItems）空着——挂首选术语那一级非交不可");
                }
                if (StringUtils.isEmpty(form.getCollectYear())) {
                    faults.add("收词年度（collectYear）空着——挂首选术语那一级非交不可");
                }
            } else {
                if (StringUtils.isEmpty(form.getExtendGroups())) {
                    faults.add("延伸专业组（extendGroups）空着——挂延伸术语那一级，延伸到哪几组非交不可");
                }
            }
        }
        if (form.getQty() == null) {
            faults.add("年报告份数（qty）空着——该填份数的格子不能留白");
        } else if (form.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            faults.add("年报告份数（qty）写成 " + form.getQty().stripTrailingZeros().toPlainString()
                    + "——零、负数不进库");
        }
        if (form.getFineAmt() == null) {
            faults.add("复核时限（fineAmt）空着——该填时限的格子不能留白");
        } else if (form.getFineAmt().compareTo(BigDecimal.ZERO) < 0) {
            faults.add("复核时限（fineAmt）写成 " + form.getFineAmt().stripTrailingZeros().toPlainString()
                    + "——负数不进库");
        }
    }

    /**
     * 三条界线自低往高排，拿年报告份数去靠：够不着低界停最底一层；到得了低界没到
     * 中界进一层；到中界未及高界再进一层；够得上高界到顶。恰好持平算跨过去，取上一层。
     */
    private int foldGrade(BigDecimal qty, TAdrOrgArch arch) {
        if (qty == null || arch == null) {
            return 1;
        }
        if (arch.getTh1Max() != null && qty.compareTo(arch.getTh1Max()) < 0) {
            return 1;
        }
        if (arch.getTh2Max() != null && qty.compareTo(arch.getTh2Max()) < 0) {
            return 2;
        }
        if (arch.getTh3Max() != null && qty.compareTo(arch.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }

    /** 把表单盖到卡上（不含系统发号/系统折档/状态那些栏） */
    private void applyForm(TAdrTermCard card, TermCardOpenForm form, TAdrOrgArch arch) {
        card.setSiteId(arch.getId());
        card.setSiteNo(arch.getSiteNo());
        card.setTermName(trim(form.getTermName()));
        card.setSymptom(trim(form.getSymptom()));
        card.setTermLevel(form.getTermLevel());
        card.setParentCode(trim(form.getParentCode()));
        card.setBasisClause(trim(form.getBasisClause()));
        card.setRefineItems(trim(form.getRefineItems()));
        card.setCollectYear(trim(form.getCollectYear()));
        String[] groups = splitGroups(form.getExtendGroups());
        card.setExtendGroups(joinGroups(groups));
        card.setExtendGroupCount(groups.length); // 几组由系统按分隔栏点，不由人报
        card.setQty(form.getQty());
        card.setFineAmt(form.getFineAmt());
        card.setOpenAt(form.getOpenAt());
        card.setCloseAt(form.getCloseAt());
        card.setRemark(form.getRemark());
        card.setCreateBy(StringUtils.isEmpty(form.getOperator()) ? "system" : form.getOperator());
        card.setCreateTime(new Date());
    }

    /** 分类身份：同一单位下层级＋名号＋上位码三项归一；沿用头一回发的术语编码 */
    private String resolveTermCode(String siteNo, TermCardOpenForm form) {
        String level = String.valueOf(form.getTermLevel());
        String name = form.getTermName() == null ? "" : form.getTermName().trim();
        String parent = form.getParentCode() == null ? "" : form.getParentCode().trim();
        List<TAdrTermCard> kin = this.baseMapper.selectList(new QueryWrapper<TAdrTermCard>()
                .eq("site_no", siteNo)
                .eq("term_level", form.getTermLevel())
                .eq("del_flag", DEL_NORMAL)
                .orderByDesc("node_no", "id"));
        for (TAdrTermCard c : kin) {
            String cName = c.getTermName() == null ? "" : c.getTermName().trim();
            String cParent = c.getParentCode() == null ? "" : c.getParentCode().trim();
            if (cName.equals(name) && cParent.equals(parent)) {
                return c.getTermCode();
            }
        }
        return "TE" + SnowflakeIdWorker.getUUID() + "-" + level;
    }

    /**
     * 校验串：卡号＋术语编码＋版次序＋盖到卡上的各栏一锅烩。版次序在串里，
     * 改版后新版那行必配新串，两版共用一串在结构上就不可能；封过覆不掉。
     */
    private String checksum(TAdrTermCard c) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        StringBuilder sb = new StringBuilder();
        sb.append(nz(c.getBillNo())).append('|')
                .append(nz(c.getTermCode())).append('|')
                .append(normNodeNo(c.getNodeNo())).append('|')
                .append(nz(c.getSiteNo())).append('|')
                .append(c.getTermLevel() == null ? "" : c.getTermLevel()).append('|')
                .append(nz(c.getParentCode())).append('|')
                .append(c.getQty() == null ? "" : c.getQty().stripTrailingZeros().toPlainString()).append('|')
                .append(c.getFineAmt() == null ? "" : c.getFineAmt().stripTrailingZeros().toPlainString()).append('|')
                .append(c.getOpenAt() == null ? "" : fmt.format(c.getOpenAt())).append('|')
                .append(c.getCloseAt() == null ? "" : fmt.format(c.getCloseAt())).append('|')
                .append(c.getExtendGroupCount() == null ? "" : c.getExtendGroupCount());
        return MD5Util.encode(sb.toString());
    }

    /** 旧纸卡兼容视图：步数空着照待核对、版次空着照第 1 版、现行标记空着照现行（只改回话，不落库） */
    private TAdrTermCard legacyView(TAdrTermCard src) {
        if (src == null) {
            return null;
        }
        src.setStatus(normStatus(src.getStatus()));
        src.setNodeNo(normNodeNo(src.getNodeNo()));
        if (src.getCurrentFlag() == null) {
            src.setCurrentFlag(CURRENT_YES);
        }
        if (src.getDelFlag() == null) {
            src.setDelFlag(DEL_NORMAL);
        }
        return src;
    }

    private static int normStatus(Integer status) {
        return status == null ? STATUS_PENDING : status;
    }

    private static int normNodeNo(Integer nodeNo) {
        return nodeNo == null ? 1 : nodeNo;
    }

    private static String[] splitGroups(String raw) {
        if (StringUtils.isEmpty(raw)) {
            return new String[0];
        }
        List<String> groups = new ArrayList<>();
        for (String g : raw.trim().split(GROUP_SPLIT)) {
            if (StringUtils.isNotEmpty(g.trim())) {
                groups.add(g.trim());
            }
        }
        return groups.toArray(new String[0]);
    }

    private static String joinGroups(String[] groups) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < groups.length; i++) {
            if (i > 0) {
                sb.append('、');
            }
            sb.append(groups[i]);
        }
        return sb.toString();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private TermCardOpenForm copyForm(TermCardOpenForm f) {
        TermCardOpenForm c = new TermCardOpenForm();
        c.setSiteNo(f.getSiteNo());
        c.setTermName(f.getTermName());
        c.setSymptom(f.getSymptom());
        c.setTermLevel(f.getTermLevel());
        c.setParentCode(f.getParentCode());
        c.setBasisClause(f.getBasisClause());
        c.setRefineItems(f.getRefineItems());
        c.setCollectYear(f.getCollectYear());
        c.setExtendGroups(f.getExtendGroups());
        c.setQty(f.getQty());
        c.setFineAmt(f.getFineAmt());
        c.setOpenAt(f.getOpenAt());
        c.setCloseAt(f.getCloseAt());
        c.setOperator(f.getOperator());
        c.setRemark(f.getRemark());
        return c;
    }
}
