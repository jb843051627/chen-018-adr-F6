package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TAdrOrgArchMapper;
import com.fc.v2.mapper.auto.TAdrTermCardMapper;
import com.fc.v2.mapper.auto.TAdrTermTaxonMapper;
import com.fc.v2.model.auto.TAdrOrgArch;
import com.fc.v2.model.auto.TAdrTermCard;
import com.fc.v2.model.auto.TAdrTermTaxon;
import com.fc.v2.model.custom.adr.CardOpenReceiptVo;
import com.fc.v2.model.custom.adr.CardRosterVo;
import com.fc.v2.model.custom.adr.TermSearchHitVo;
import com.fc.v2.service.ITAdrTermCardService;
import com.fc.v2.util.MD5Util;
import com.fc.v2.util.SnowflakeIdWorker;
import com.fc.v2.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 术语分类卡 Service 业务层。
 *
 * 去年省里抽查追出两条缝，今年都在这一道里堵死：
 * 1) 换了报告单位、名下仍压着原来那本底册——对底册只认 site_no 这一把尺，
 *    名号读着像不算对上，换挂靠走 {@link #changeSite} 当场重折监督层级；
 * 2) 年报告份数改过两回、监督层级算出两个结果——监督层级栏不设手工口子，
 *    只在立卡/份数增减/换底册三个当口由系统照三条界线重算落栏。
 *
 * 三句规矩：分类不重复立卡（同 site_no+term_code 在走的只一张），一卡不许两挂
 * （卡挂术语树只认一个 term_code），一册不许两家单位共用（site_no 唯一钉住底册）。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TAdrTermCardServiceImpl extends ServiceImpl<TAdrTermCardMapper, TAdrTermCard>
        implements ITAdrTermCardService {

    /** 分类层级：系统器官分类 */
    private static final int TAXON_SOC = 1;
    /** 分类层级：首选术语 */
    private static final int TAXON_PT = 2;
    /** 分类层级：延伸术语 */
    private static final int TAXON_ET = 3;

    /** 进展：待核对 */
    private static final int STATUS_PENDING = 0;
    /** 进展：已核对（到此封一版校验串，封过不覆） */
    private static final int STATUS_CHECKED = 1;
    /** 进展：已冻住（加不进、改不动、撤不下；要动另开一张） */
    private static final int STATUS_FROZEN = 2;

    /** 版层：现行（日常名录只见这一层） */
    private static final int VERSION_CURRENT = 0;
    /** 版层：历史（新版一立旧版即挪入，不删、不与新版同摆） */
    private static final int VERSION_HISTORY = 1;

    /** 底册情形：在册 */
    private static final int ORG_LIVE = 0;
    /** 底册情形：已退出（封住新卡通道，旧卡照旧查得着） */
    private static final int ORG_EXITED = 1;

    private static final int DEL_NORMAL = 0;

    /** 卡号/术语编码发号器（worker 号避开代码生成器那台） */
    private static final SnowflakeIdWorker NODE = new SnowflakeIdWorker(2, 2);

    @Resource
    private TAdrOrgArchMapper orgArchMapper;
    @Resource
    private TAdrTermTaxonMapper termTaxonMapper;

    // ---------------------------------------------------------------- 查

    @Override
    public TAdrTermCard selectTAdrTermCardById(Long id) {
        if (id == null) {
            return null;
        }
        TAdrTermCard card = this.baseMapper.selectById(id);
        if (card == null || !Integer.valueOf(DEL_NORMAL).equals(card.getDelFlag())) {
            return null;
        }
        // 当下停在哪一档以库里系统落的那格为准；旧纸卡这格空着，就照空着回，不替它猜成停了
        return card;
    }

    @Override
    public List<TAdrTermCard> selectTAdrTermCardList(Wrapper<TAdrTermCard> queryWrapper) {
        // 只照调用方给的条件查，不私自添一档、不锁死页码——名录怎么点由 service 方法说了算
        return this.baseMapper.selectList(queryWrapper);
    }

    // ---------------------------------------------------------------- 立卡（旧走法，签名原样留着）

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTAdrTermCard(TAdrTermCard record) {
        if (record == null) {
            return 0;
        }
        int ownLevel = record.getOwnLevel() == null ? 3 : record.getOwnLevel();
        CardOpenReceiptVo receipt = insertTAdrTermCard(record, ownLevel);
        return receipt.isAccepted() ? 1 : 0;
    }

    // ---------------------------------------------------------------- 立卡（新走法，同名重载）

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CardOpenReceiptVo insertTAdrTermCard(TAdrTermCard record, int ownLevel) {
        if (record == null) {
            return CardOpenReceiptVo.reject(null, "空卡不进库");
        }
        record.setOwnLevel(ownLevel);

        // 卡号只许系统发；自拟的号、自报的版次/层级/状态/校验串一律不存
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            return CardOpenReceiptVo.reject(Arrays.asList(new CardOpenReceiptVo.Missing(
                    "billNo", "分类卡号", "分类卡号由系统发给，自拟的号不存")), "卡不进库");
        }

        List<CardOpenReceiptVo.Missing> missing = pointMissing(record);
        if (!missing.isEmpty()) {
            return CardOpenReceiptVo.reject(missing, "有" + missing.size() + "栏没交齐或数不像话，卡不进库");
        }

        TAdrOrgArch arch = liveOrg(record.getSiteNo());
        TAdrTermTaxon taxon = liveTaxon(record.getTermCode());

        // 同一单位名下同一分类，同时只容一张在走的卡——配平只认「底册代号＋术语编码」这一对
        String familyNo = familyNo(record.getSiteNo(), record.getTermCode());
        if (hasWalkingCard(familyNo, null)) {
            return CardOpenReceiptVo.reject(Arrays.asList(new CardOpenReceiptVo.Missing(
                    "familyNo", "同分类在走卡",
                    "同一单位名下同一分类头一张还没冻住、也没打住，第二张立不起来；不看谁先到")),
                    "卡不进库");
        }

        fillFromTaxon(record, taxon);
        record.setFamilyNo(familyNo);
        record.setBillNo(issueBillNo());
        record.setNodeNo(1);
        record.setVersionFlag(VERSION_CURRENT);
        record.setSiteId(arch.getId());
        record.setSiteNo(arch.getSiteNo());
        record.setExtGroupCount(countGroups(record.getExtGroups()));
        record.setGradeLevel(gradeOf(arch, record.getQty()));
        record.setStatus(STATUS_PENDING);
        record.setContent(null);
        record.setDelFlag(DEL_NORMAL);
        this.baseMapper.insert(record);

        CardOpenReceiptVo vo = new CardOpenReceiptVo();
        vo.setAccepted(true);
        vo.setId(record.getId());
        vo.setBillNo(record.getBillNo());
        vo.setNodeNo(record.getNodeNo());
        vo.setFamilyNo(record.getFamilyNo());
        vo.setGradeLevel(record.getGradeLevel());
        vo.setStatus(record.getStatus());
        vo.setOwnLevel(record.getOwnLevel());
        vo.setMessage("卡已立成，停在「待核对」；监督层级由三条界线当场折为第" + record.getGradeLevel() + "层");
        return vo;
    }

    /**
     * 按分类层级当场点非写不可的栏；数不像话的栏也一并点出。空单表示齐了。
     * 回执指名到栏：层级空、上位码空、份数零/负、时限负、开口晚于收口……各点各的名。
     */
    @Override
    public List<CardOpenReceiptVo.Missing> pointMissing(TAdrTermCard r) {
        List<CardOpenReceiptVo.Missing> miss = new ArrayList<>();

        if (r.getTaxonLevel() == null) {
            miss.add(new CardOpenReceiptVo.Missing("taxonLevel", "分类层级", "层级空着没选，卡不进库"));
            // 层级没选，层级项下各栏无从点起，先点公共栏，再请它把层级选上
        }
        if (StringUtils.isEmpty(r.getSiteNo())) {
            miss.add(new CardOpenReceiptVo.Missing("siteNo", "报告单位底册代号", "底册代号空着；归类只认代号这一把尺"));
        } else if (liveOrg(r.getSiteNo()) == null) {
            TAdrOrgArch any = this.orgArchMapper.selectOne(new QueryWrapper<TAdrOrgArch>()
                    .eq("site_no", r.getSiteNo()).eq("del_flag", DEL_NORMAL).last("limit 1"));
            if (any == null) {
                miss.add(new CardOpenReceiptVo.Missing("siteNo", "报告单位底册代号",
                        "底册代号" + r.getSiteNo() + "在册上查无；名号读着像不算对上，也不许手工核一遍了事"));
            } else {
                miss.add(new CardOpenReceiptVo.Missing("siteNo", "报告单位底册代号",
                        "这本底册年内已退出，新卡不再往里挂；先前挂在底下的旧卡照旧查得着"));
            }
        }
        if (StringUtils.isEmpty(r.getTermCode())) {
            miss.add(new CardOpenReceiptVo.Missing("termCode", "术语编码", "术语编码空着——配平认的是「底册代号＋分类码」这一对"));
        } else {
            TAdrTermTaxon taxon = liveTaxon(r.getTermCode());
            if (taxon == null) {
                miss.add(new CardOpenReceiptVo.Missing("termCode", "术语编码",
                        "术语编码" + r.getTermCode() + "在本级分类树里找不着现行那一条"));
            } else if (r.getTaxonLevel() != null && !r.getTaxonLevel().equals(taxon.getTaxonLevel())) {
                miss.add(new CardOpenReceiptVo.Missing("taxonLevel", "分类层级",
                        "卡上选的层级与分类树里这一条所在层级对不上"));
            } else if (r.getOwnLevel() != null && !r.getOwnLevel().equals(taxon.getOwnLevel())) {
                miss.add(new CardOpenReceiptVo.Missing("termCode", "术语编码",
                        "这一条不在本级分类树上，别级的卡不许同屏并列任人挑"));
            }
        }

        if (r.getQty() == null) {
            miss.add(new CardOpenReceiptVo.Missing("qty", "年报告份数", "该填份数的格子空着"));
        } else if (r.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            miss.add(new CardOpenReceiptVo.Missing("qty", "年报告份数",
                    "年报告份数写零、写负数（" + r.getQty().stripTrailingZeros().toPlainString() + "）不进库"));
        }
        if (r.getFineAmt() == null) {
            miss.add(new CardOpenReceiptVo.Missing("fineAmt", "复核时限", "复核时限那栏空着"));
        } else if (r.getFineAmt().compareTo(BigDecimal.ZERO) < 0) {
            miss.add(new CardOpenReceiptVo.Missing("fineAmt", "复核时限",
                    "复核时限填成负数（" + r.getFineAmt().stripTrailingZeros().toPlainString() + "）不进库"));
        }
        if (r.getValidStart() != null && r.getValidEnd() != null
                && r.getValidEnd().before(r.getValidStart())) {
            miss.add(new CardOpenReceiptVo.Missing("validEnd", "收口时刻",
                    "开口那一栏写得比收口还晚，得先问一句才收"));
        }

        Integer lv = r.getTaxonLevel();
        if (lv != null) {
            if (lv == TAXON_SOC) {
                if (StringUtils.isEmpty(r.getParentCode())) {
                    miss.add(new CardOpenReceiptVo.Missing("parentCode", "上位分类码",
                            "挂系统器官分类那一级，得交出上位那一条的分类码"));
                } else {
                    TAdrTermTaxon soc = liveTaxon(r.getTermCode());
                    if (soc != null && soc.getParentCode() != null
                            && !soc.getParentCode().equals(r.getParentCode().trim())) {
                        miss.add(new CardOpenReceiptVo.Missing("parentCode", "上位分类码",
                                "交来的上位分类码与分类树上这一条对不上"));
                    }
                }
                if (StringUtils.isEmpty(r.getClauseNo())) {
                    miss.add(new CardOpenReceiptVo.Missing("clauseNo", "设定依据条款号",
                            "挂系统器官分类那一级，得交出设定依据的条款号"));
                }
            } else if (lv == TAXON_PT) {
                if (StringUtils.isEmpty(r.getCityItems())) {
                    miss.add(new CardOpenReceiptVo.Missing("cityItems", "本市细化条目",
                            "挂首选术语那一级，得交出本市细化的那几条"));
                }
                if (r.getCollectYear() == null) {
                    miss.add(new CardOpenReceiptVo.Missing("collectYear", "收词年度",
                            "挂首选术语那一级，得交出它收词的年度"));
                }
            } else if (lv == TAXON_ET) {
                if (StringUtils.isEmpty(r.getExtGroups())) {
                    miss.add(new CardOpenReceiptVo.Missing("extGroups", "延伸专业组",
                            "挂延伸术语那一级，得交出延伸到哪些专业组"));
                } else if (countGroups(r.getExtGroups()) <= 0) {
                    miss.add(new CardOpenReceiptVo.Missing("extGroups", "延伸专业组",
                            "专业组名单里点不出一组来"));
                }
            } else {
                miss.add(new CardOpenReceiptVo.Missing("taxonLevel", "分类层级",
                        "层级只许选系统器官分类/首选术语/延伸术语这三档"));
            }
        }
        return miss;
    }

    // ---------------------------------------------------------------- 份数 / 换底册

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrTermCard changeQty(Long id, BigDecimal qty) {
        TAdrTermCard card = requireWalking(id);
        if (card == null) {
            return null;
        }
        if (qty == null) {
            throw new IllegalArgumentException("年报告份数那栏空着，没法重折监督层级");
        }
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("年报告份数写零、写负数（"
                    + qty.stripTrailingZeros().toPlainString() + "）不进库");
        }
        card.setQty(qty);
        // 份数有增有减这一当口，照三条界线重折，结果落进卡上层级栏——那栏没有手工口子
        card.setGradeLevel(gradeOf(liveOrg(card.getSiteNo()), qty));
        card.setUpdateTime(new Date());
        this.baseMapper.updateById(card);
        return card;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrTermCard changeSite(Long id, String siteNo) {
        TAdrTermCard card = requireWalking(id);
        if (card == null) {
            return null;
        }
        if (StringUtils.isEmpty(siteNo)) {
            throw new IllegalArgumentException("底册代号空着；换挂靠只认代号，名号撞着不算");
        }
        TAdrOrgArch arch = liveOrg(siteNo);
        if (arch == null) {
            throw new IllegalArgumentException("底册代号" + siteNo
                    + "在册上查无或已退出——名号读着像不算对上，年内退出的底册不挂新卡");
        }
        // 换了挂靠，配平那一对也换了：新单位同分类已有在走卡，一样不许两卡叠着
        String newFamily = familyNo(siteNo, card.getTermCode());
        if (hasWalkingCard(newFamily, id)) {
            throw new IllegalStateException("新底册名下这一分类已有一张在走的卡，一卡不许两挂");
        }
        card.setSiteId(arch.getId());
        card.setSiteNo(arch.getSiteNo());
        card.setFamilyNo(newFamily);
        // 改了挂靠的底册这一当口，照新底册三条界线重折
        card.setGradeLevel(gradeOf(arch, card.getQty()));
        card.setUpdateTime(new Date());
        this.baseMapper.updateById(card);
        return card;
    }

    // ---------------------------------------------------------------- 改版

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrTermCard revise(Long id, TAdrTermCard changes) {
        TAdrTermCard old = requireWalking(id);
        if (old == null) {
            // 冻住的卡要动只能够另开一张，不走改版这道
            return null;
        }
        TAdrTermCard next = new TAdrTermCard();
        next.setSiteNo(old.getSiteNo());
        next.setSiteId(old.getSiteId());
        next.setTermCode(old.getTermCode()); // 换分类等于另一个分类，该另立卡，不许借改版挪挂
        next.setTaxonLevel(old.getTaxonLevel());
        next.setOwnLevel(old.getOwnLevel());
        next.setFamilyNo(old.getFamilyNo());
        next.setQty(changes.getQty() != null ? changes.getQty() : old.getQty());
        next.setFineAmt(changes.getFineAmt() != null ? changes.getFineAmt() : old.getFineAmt());
        next.setValidStart(changes.getValidStart() != null ? changes.getValidStart() : old.getValidStart());
        next.setValidEnd(changes.getValidEnd() != null ? changes.getValidEnd() : old.getValidEnd());
        next.setParentCode(changes.getParentCode() != null ? changes.getParentCode() : old.getParentCode());
        next.setClauseNo(changes.getClauseNo() != null ? changes.getClauseNo() : old.getClauseNo());
        next.setCityItems(changes.getCityItems() != null ? changes.getCityItems() : old.getCityItems());
        next.setCollectYear(changes.getCollectYear() != null ? changes.getCollectYear() : old.getCollectYear());
        next.setExtGroups(changes.getExtGroups() != null ? changes.getExtGroups() : old.getExtGroups());
        next.setRemark(changes.getRemark() != null ? changes.getRemark() : old.getRemark());

        List<CardOpenReceiptVo.Missing> missing = pointMissing(next);
        if (!missing.isEmpty()) {
            StringBuilder sb = new StringBuilder("新版有栏没交齐，不立：");
            for (CardOpenReceiptVo.Missing m : missing) {
                sb.append("「").append(m.getLabel()).append("」").append(m.getReason()).append("；");
            }
            throw new IllegalArgumentException(sb.toString());
        }
        TAdrTermTaxon taxon = liveTaxon(next.getTermCode());
        fillFromTaxon(next, taxon);
        next.setExtGroupCount(countGroups(next.getExtGroups()));

        // 旧版不删，挪进历史层备事后追溯；新版那行走起来，日常名录只见新版
        this.baseMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TAdrTermCard>()
                .eq("id", old.getId())
                .set("version_flag", VERSION_HISTORY));

        int newNode = (old.getNodeNo() == null ? 1 : old.getNodeNo() + 1);
        next.setBillNo(old.getBillNo());
        next.setNodeNo(newNode);
        next.setVersionFlag(VERSION_CURRENT);
        next.setGradeLevel(gradeOf(liveOrg(next.getSiteNo()), next.getQty()));
        next.setStatus(STATUS_PENDING);
        next.setContent(null); // 校验串随版重算：走到已核对才封新串；旧版那行留旧串
        next.setDelFlag(DEL_NORMAL);
        this.baseMapper.insert(next);
        return next;
    }

    // ---------------------------------------------------------------- 三态步子

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrTermCard advance(Long id) {
        TAdrTermCard card = this.baseMapper.selectById(id);
        if (card == null || !Integer.valueOf(DEL_NORMAL).equals(card.getDelFlag())
                || !Integer.valueOf(VERSION_CURRENT).equals(card.getVersionFlag())) {
            return null;
        }
        // 旧纸卡步数空着：当它停在待核对接住，不能一碰就报错
        int step = card.getStatus() == null ? STATUS_PENDING : card.getStatus();
        if (step == STATUS_FROZEN) {
            return null; // 冻住即走完，没有下一步
        }
        if (step == STATUS_CHECKED) {
            card.setStatus(STATUS_FROZEN);
        } else {
            card.setStatus(STATUS_CHECKED);
            // 走到「已核对」才算封住：封一版校验串。版次序空着的旧纸卡，封之前先补成 1
            if (card.getNodeNo() == null) {
                card.setNodeNo(1);
            }
            card.setContent(seal(card));
        }
        card.setUpdateTime(new Date());
        this.baseMapper.updateById(card);
        return card;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrTermCard rollback(Long id) {
        TAdrTermCard card = this.baseMapper.selectById(id);
        if (card == null || !Integer.valueOf(DEL_NORMAL).equals(card.getDelFlag())
                || !Integer.valueOf(VERSION_CURRENT).equals(card.getVersionFlag())) {
            return null;
        }
        if (!Integer.valueOf(STATUS_CHECKED).equals(card.getStatus())) {
            // 还没核对无步可退；冻住之后撤不下来；旧纸卡空档也不退
            return null;
        }
        card.setStatus(STATUS_PENDING);
        // 先前那一趟的校验串原样留着，不删；后补封的是新串，两串不并排成两行走
        card.setUpdateTime(new Date());
        this.baseMapper.updateById(card);
        return card;
    }

    @Override
    public TAdrTermCard currentState(Long id) {
        TAdrTermCard card = this.baseMapper.selectById(id);
        if (card == null || !Integer.valueOf(DEL_NORMAL).equals(card.getDelFlag())) {
            return null;
        }
        // 停在哪一档只认库里系统落的格；屏上录入的档、经办人心里记的档，进不到这张回票里
        return card;
    }

    // ---------------------------------------------------------------- 术语树与检索

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrTermTaxon registerTaxon(TAdrTermTaxon taxon) {
        if (taxon == null || StringUtils.isEmpty(taxon.getTermName()) || taxon.getTaxonLevel() == null) {
            throw new IllegalArgumentException("术语名号与所在层级不许空着");
        }
        int lv = taxon.getTaxonLevel();
        if (lv < TAXON_SOC || lv > TAXON_ET) {
            throw new IllegalArgumentException("层级只许 1系统器官分类 2首选术语 3延伸术语");
        }
        // 术语编码由系统发，自拟的不存
        if (StringUtils.isNotEmpty(taxon.getTermCode())) {
            throw new IllegalArgumentException("术语编码由系统发给，自拟的号不存");
        }
        int ownLevel = taxon.getOwnLevel() == null ? 3 : taxon.getOwnLevel();
        String code = "TM" + Long.toUnsignedString(NODE.nextId(), 36).toUpperCase();

        if (lv == TAXON_SOC) {
            // 系统器官分类是树顶，上位码即这一条自己的分类码
            taxon.setParentCode(code);
        } else {
            if (StringUtils.isEmpty(taxon.getParentCode())) {
                throw new IllegalArgumentException("首选/延伸术语得交出上位那一条的分类码");
            }
            TAdrTermTaxon parent = liveTaxon(taxon.getParentCode());
            if (parent == null) {
                throw new IllegalArgumentException("上位分类码" + taxon.getParentCode() + "在分类树里找不着");
            }
            if (!Integer.valueOf(lv - 1).equals(parent.getTaxonLevel())) {
                throw new IllegalArgumentException("上位那一条不在紧邻的上一层，树不是这么套的");
            }
            if (!Integer.valueOf(ownLevel).equals(parent.getOwnLevel())) {
                throw new IllegalArgumentException("上位那条不在本级，不许跨级套树");
            }
        }
        taxon.setTermCode(code);
        taxon.setOwnLevel(ownLevel);
        taxon.setStatus(0);
        taxon.setDelFlag(DEL_NORMAL);
        this.termTaxonMapper.insert(taxon);
        return taxon;
    }

    @Override
    public List<TermSearchHitVo> searchTerms(String keyword, int ownLevel) {
        if (StringUtils.isEmpty(keyword)) {
            // 空口不搜，更不把整树倒出来任人挑
            return TermSearchHitVo.empty();
        }
        String kw = keyword.trim();
        List<TAdrTermTaxon> rows = this.termTaxonMapper.selectList(new QueryWrapper<TAdrTermTaxon>()
                .eq("own_level", ownLevel)
                .eq("del_flag", DEL_NORMAL)
                .eq("status", 0)
                .and(w -> w.likeRight("term_name", kw).or().like("term_name", kw)
                        .or().like("symptom_words", kw)
                        .or().likeRight("parent_code", kw).or().like("parent_code", kw))
                .orderByAsc("taxon_level", "term_code"));
        List<TermSearchHitVo> hits = new ArrayList<>();
        for (TAdrTermTaxon t : rows) {
            // 命中即带出上位分类码与所在层级，不劳人再抄第二遍
            hits.add(new TermSearchHitVo(t.getTermCode(), t.getTermName(), t.getSymptomWords(),
                    t.getTaxonLevel(), t.getParentCode(), t.getOwnLevel()));
        }
        return hits;
    }

    // ---------------------------------------------------------------- 本级名录

    @Override
    public CardRosterVo roster(int ownLevel) {
        // 占数与这一屏的卡同一回点出来：只点现行版、未删的卡，不另存一份计数
        List<TAdrTermCard> cards = this.baseMapper.selectList(new QueryWrapper<TAdrTermCard>()
                .eq("own_level", ownLevel)
                .eq("version_flag", VERSION_CURRENT)
                .eq("del_flag", DEL_NORMAL)
                .orderByAsc("id"));
        CardRosterVo vo = new CardRosterVo();
        vo.setOwnLevel(ownLevel);
        int occupied = 0;
        int frozen = 0;
        for (TAdrTermCard c : cards) {
            if (Integer.valueOf(STATUS_FROZEN).equals(c.getStatus())) {
                frozen++;
            } else {
                // status 空着的旧纸卡仍是在走的卡，名额照旧占着，不能当成已停了的
                occupied++;
            }
        }
        vo.setCards(cards);
        vo.setCurrentCount(cards.size());
        vo.setOccupiedCount(occupied);
        vo.setFrozenCount(frozen);
        return vo;
    }

    // ---------------------------------------------------------------- 旧门（签名留着，规矩不留后门）

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTAdrTermCard(TAdrTermCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        TAdrTermCard card = this.baseMapper.selectById(record.getId());
        if (card == null || !Integer.valueOf(DEL_NORMAL).equals(card.getDelFlag())
                || !Integer.valueOf(VERSION_CURRENT).equals(card.getVersionFlag())) {
            return 0;
        }
        if (Integer.valueOf(STATUS_FROZEN).equals(card.getStatus())) {
            // 冻住之后改不动；要动另开一张
            return 0;
        }
        if (record.getQty() != null) {
            card.setQty(record.getQty());
        }
        if (record.getFineAmt() != null) {
            card.setFineAmt(record.getFineAmt());
        }
        if (record.getValidStart() != null) {
            card.setValidStart(record.getValidStart());
        }
        if (record.getValidEnd() != null) {
            card.setValidEnd(record.getValidEnd());
        }
        if (record.getParentCode() != null) {
            card.setParentCode(record.getParentCode());
        }
        if (record.getClauseNo() != null) {
            card.setClauseNo(record.getClauseNo());
        }
        if (record.getCityItems() != null) {
            card.setCityItems(record.getCityItems());
        }
        if (record.getCollectYear() != null) {
            card.setCollectYear(record.getCollectYear());
        }
        if (record.getExtGroups() != null) {
            card.setExtGroups(record.getExtGroups());
            card.setExtGroupCount(countGroups(record.getExtGroups()));
        }
        // 卡号/版次/校验串/状态/监督层级/底册归属/术语编码一概不从这道门进——各有各的正当入口
        List<CardOpenReceiptVo.Missing> missing = pointMissing(card);
        if (!missing.isEmpty()) {
            return 0;
        }
        card.setGradeLevel(gradeOf(liveOrg(card.getSiteNo()), card.getQty()));
        card.setUpdateTime(new Date());
        return this.baseMapper.updateById(card);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTAdrTermCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        for (Long id : idArr) {
            TAdrTermCard card = this.baseMapper.selectById(id);
            if (card != null && Integer.valueOf(STATUS_FROZEN).equals(card.getStatus())) {
                // 冻住的撤不下来；一批里夹一张，整批不动
                return 0;
            }
        }
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTAdrTermCardById(Long id) {
        TAdrTermCard card = this.baseMapper.selectById(id);
        if (card == null) {
            return 0;
        }
        if (Integer.valueOf(STATUS_FROZEN).equals(card.getStatus())) {
            return 0;
        }
        return this.baseMapper.deleteById(id);
    }

    // ---------------------------------------------------------------- 里子

    /** 取在册（未删、未退出）底册；退出的封住新卡通道 */
    private TAdrOrgArch liveOrg(String siteNo) {
        if (StringUtils.isEmpty(siteNo)) {
            return null;
        }
        return this.orgArchMapper.selectOne(new QueryWrapper<TAdrOrgArch>()
                .eq("site_no", siteNo)
                .eq("status", ORG_LIVE)
                .eq("del_flag", DEL_NORMAL)
                .last("limit 1"));
    }

    /** 取本分类树现行那一条 */
    private TAdrTermTaxon liveTaxon(String termCode) {
        if (StringUtils.isEmpty(termCode)) {
            return null;
        }
        return this.termTaxonMapper.selectOne(new QueryWrapper<TAdrTermTaxon>()
                .eq("term_code", termCode)
                .eq("status", 0)
                .eq("del_flag", DEL_NORMAL)
                .last("limit 1"));
    }

    /** 配平只认这一对，不看谁先到 */
    private static String familyNo(String siteNo, String termCode) {
        return siteNo + "#" + termCode;
    }

    /**
     * 同一卡族名下有没有在走（未冻住）的现行卡。
     * 步数空着的旧纸卡也算在走；excludeId 给换底册重验时撇开本行用。
     */
    private boolean hasWalkingCard(String familyNo, Long excludeId) {
        List<TAdrTermCard> rows = this.baseMapper.selectList(new QueryWrapper<TAdrTermCard>()
                .eq("family_no", familyNo)
                .eq("version_flag", VERSION_CURRENT)
                .eq("del_flag", DEL_NORMAL));
        for (TAdrTermCard c : rows) {
            if (excludeId != null && excludeId.equals(c.getId())) {
                continue;
            }
            if (!Integer.valueOf(STATUS_FROZEN).equals(c.getStatus())) {
                return true;
            }
        }
        return false;
    }

    /** 卡上的上位码/术语名号照分类树带出来，留痕不靠人手抄 */
    private static void fillFromTaxon(TAdrTermCard card, TAdrTermTaxon taxon) {
        card.setTermName(taxon.getTermName());
        if (card.getTaxonLevel() != null && card.getTaxonLevel() > TAXON_SOC) {
            card.setParentCode(taxon.getParentCode());
        }
    }

    /**
     * 三条界线自低往高排，拿年报告份数去靠；恰好与界线持平算跨过去，取上一层。
     * 够不着低界→1，到低界未到中界→2，到中界未及高界→3，够得上高界→4。
     */
    private static int gradeOf(TAdrOrgArch arch, BigDecimal qty) {
        if (arch == null || qty == null) {
            return 1;
        }
        BigDecimal low = arch.getTh1Max();
        BigDecimal mid = arch.getTh2Max();
        BigDecimal high = arch.getTh3Max();
        if (low != null && qty.compareTo(low) < 0) {
            return 1;
        }
        if (mid == null || qty.compareTo(mid) < 0) {
            return 2;
        }
        if (high == null || qty.compareTo(high) < 0) {
            return 3;
        }
        return 4;
    }

    /** 延伸专业组照名单逐个数：顿号/逗号/分号/空白切开，去空白、去重，几组就是几组 */
    private static int countGroups(String groups) {
        if (StringUtils.isEmpty(groups)) {
            return 0;
        }
        Set<String> set = new LinkedHashSet<>();
        for (String g : groups.split("[、,，;；/\\s]+")) {
            if (StringUtils.isNotEmpty(g)) {
                set.add(g.trim());
            }
        }
        return set.size();
    }

    /** 取现行、未删、还没冻住的卡；冻住的（要动只能够另开一张）回 null */
    private TAdrTermCard requireWalking(Long id) {
        if (id == null) {
            return null;
        }
        TAdrTermCard card = this.baseMapper.selectById(id);
        if (card == null || !Integer.valueOf(DEL_NORMAL).equals(card.getDelFlag())
                || !Integer.valueOf(VERSION_CURRENT).equals(card.getVersionFlag())) {
            return null;
        }
        if (Integer.valueOf(STATUS_FROZEN).equals(card.getStatus())) {
            return null;
        }
        return card;
    }

    private static String issueBillNo() {
        return "TC" + Long.toUnsignedString(NODE.nextId(), 36).toUpperCase();
    }

    /**
     * 封一版的校验串：版次序、卡族、份数折出的层级都进串，
     * 改一次版 node_no 变，新串必与旧串不同——两版共用一串即错。
     */
    private static String seal(TAdrTermCard c) {
        String raw = join(
                c.getBillNo(),
                String.valueOf(c.getNodeNo()),
                c.getFamilyNo(),
                c.getSiteNo(),
                c.getTermCode(),
                String.valueOf(c.getTaxonLevel()),
                nz(c.getParentCode()),
                nz(c.getClauseNo()),
                nz(c.getCityItems()),
                c.getCollectYear() == null ? "" : String.valueOf(c.getCollectYear()),
                nz(c.getExtGroups()),
                c.getExtGroupCount() == null ? "" : String.valueOf(c.getExtGroupCount()),
                c.getQty() == null ? "" : c.getQty().stripTrailingZeros().toPlainString(),
                c.getFineAmt() == null ? "" : c.getFineAmt().stripTrailingZeros().toPlainString(),
                c.getValidStart() == null ? "" : String.valueOf(c.getValidStart().getTime()),
                c.getValidEnd() == null ? "" : String.valueOf(c.getValidEnd().getTime()),
                String.valueOf(c.getGradeLevel()),
                String.valueOf(c.getOwnLevel()));
        return MD5Util.encode(raw);
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(parts[i] == null ? "" : parts[i]);
        }
        return sb.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
