package com.fc;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TAdrTermCard;
import com.fc.v2.model.custom.adr.TermCardIntakeVo;
import com.fc.v2.model.custom.adr.TermCardOpenForm;
import com.fc.v2.model.custom.adr.TermRosterVo;
import com.fc.v2.model.custom.adr.TermSearchHit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 术语分类卡领域规矩集成测试。三句硬规矩：分类不重复立卡、一卡不许两挂、
 * 一册不许两家单位共用；系统发三号、系统折监督层级、系统数步子；旧版沉历史、
 * 校验串封在已核对；旧纸卡空步数/版次/串也得接住。
 */
class TAdrTermCardServiceImplTest extends AdrTermCardTestBase {

    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static Date at(String ymd) throws Exception {
        return FMT.parse(ymd);
    }

    private TermCardOpenForm ptForm(String siteNo, String qty) {
        TermCardOpenForm f = form(siteNo, qty);
        f.setTermLevel(2);
        f.setParentCode(null);
        f.setBasisClause(null);
        f.setRefineItems("本市细化：皮疹伴瘙痒");
        f.setCollectYear("2025");
        return f;
    }

    private TermCardOpenForm etForm(String siteNo, String qty, String groups) {
        TermCardOpenForm f = form(siteNo, qty);
        f.setTermLevel(3);
        f.setTermName("注射部位皮疹");
        f.setParentCode("PT-RASH");
        f.setBasisClause(null);
        f.setExtendGroups(groups);
        return f;
    }

    // ---------------- 立卡与系统发号/折档 ----------------

    @Test
    void 立卡齐整_卡号术语编码版次序系统发_停在待核对_监督层级照界线折() {
        TermCardIntakeVo r = service.insertTAdrTermCard(form(SITE_A, "50"));

        assertTrue(r.isAccepted(), r.getMessage());
        TAdrTermCard c = r.getCard();
        assertTrue(c.getBillNo().startsWith("TC"), "卡号系统发：" + c.getBillNo());
        assertTrue(c.getTermCode().startsWith("TE"), "术语编码系统发");
        assertEquals(1, c.getNodeNo(), "头一版版次序为 1");
        assertEquals(0, c.getStatus(), "立卡即待核对，步子系统数");
        assertNull(c.getContent(), "校验串走到已核对才封，立卡时留空");
        assertEquals(2, c.getGradeLevel(), "50：过 20 没到 80，进一层=2 档");
        assertEquals(SITE_A, c.getSiteNo());
    }

    @Test
    void 三条界线折档_恰好持平算跨过去取上一层() {
        assertEquals(1, service.insertTAdrTermCard(namedForm(SITE_A, "10", "损害十")).getCard().getGradeLevel(), "够不着低界停最底");
        assertEquals(2, service.insertTAdrTermCard(namedForm(SITE_A, "20", "损害二十")).getCard().getGradeLevel(), "与低界持平算跨过");
        assertEquals(2, service.insertTAdrTermCard(namedForm(SITE_A, "79", "损害七十九")).getCard().getGradeLevel(), "到低界没到中界");
        assertEquals(3, service.insertTAdrTermCard(namedForm(SITE_A, "80", "损害八十")).getCard().getGradeLevel(), "与中界持平算跨过");
        assertEquals(4, service.insertTAdrTermCard(namedForm(SITE_A, "300", "损害三百")).getCard().getGradeLevel(), "与高界持平到顶");
        assertEquals(4, service.insertTAdrTermCard(namedForm(SITE_A, "999", "损害九百")).getCard().getGradeLevel(), "够得上高界到顶");
    }

    /** 换个术语名号的齐整表单（同单位下逐档验证时避开在走唯一） */
    private TermCardOpenForm namedForm(String siteNo, String qty, String termName) {
        TermCardOpenForm f = form(siteNo, qty);
        f.setTermName(termName);
        return f;
    }

    @Test
    void 自家拟的卡号术语编码监督层级状态_塞进来也不存() {
        TAdrTermCard in = new TAdrTermCard();
        in.setSiteNo(SITE_A);
        in.setTermName("消化系统损害");
        in.setTermLevel(1);
        in.setParentCode("SOC-GI");
        in.setBasisClause("第十二条");
        in.setQty(new BigDecimal("50"));
        in.setFineAmt(new BigDecimal("15"));
        in.setBillNo("我自己编的号");
        in.setTermCode("我自己编的码");
        in.setGradeLevel(99);
        in.setStatus(2);

        assertEquals(1, service.insertTAdrTermCard(in), "旧保存方法签名照旧，回收行数");
        TAdrTermCard saved = service.selectTAdrTermCardList(null).get(0);
        assertNotEquals("我自己编的号", saved.getBillNo());
        assertNotEquals("我自己编的码", saved.getTermCode());
        assertEquals(2, saved.getGradeLevel(), "监督层级只由系统折，手填 99 不存");
        assertEquals(0, saved.getStatus(), "步子不由人报");
    }

    // ---------------- 缺栏逐栏指名 ----------------

    @Test
    void 层级空着不进库_回执指名缺在哪一栏() {
        TermCardOpenForm f = form(SITE_A, "50");
        f.setTermLevel(null);
        TermCardIntakeVo r = service.insertTAdrTermCard(f);

        assertFalse(r.isAccepted());
        assertTrue(r.getFaults().get(0).contains("分类层级"), r.getFaults().toString());
        assertTrue(r.getFaults().get(0).contains("termLevel"), "指名到栏，不光回一句数不像话");
        assertEquals(0, cardMapper.selectCount(null).intValue(), "卡不进库");
    }

    @Test
    void 挂系统器官分类层级_上位分类码与条款号缺一不进() {
        TermCardOpenForm f = form(SITE_A, "50");
        f.setParentCode(null);
        f.setBasisClause(null);
        TermCardIntakeVo r = service.insertTAdrTermCard(f);

        assertFalse(r.isAccepted());
        assertEquals(2, r.getFaults().size());
        assertTrue(r.getFaults().get(0).contains("parentCode"));
        assertTrue(r.getFaults().get(1).contains("basisClause"));
    }

    @Test
    void 挂首选术语层级_本市细化条目与收词年度缺一不进() {
        TermCardOpenForm f = ptForm(SITE_A, "50");
        f.setCollectYear(null);
        TermCardIntakeVo r = service.insertTAdrTermCard(f);

        assertFalse(r.isAccepted());
        assertEquals(1, r.getFaults().size());
        assertTrue(r.getFaults().get(0).contains("collectYear"));
    }

    @Test
    void 挂延伸术语层级_专业组非交_组数由系统点不由人报() {
        TermCardIntakeVo bad = service.insertTAdrTermCard(etForm(SITE_A, "30", null));
        assertFalse(bad.isAccepted());
        assertTrue(bad.getFaults().get(0).contains("extendGroups"));

        TermCardIntakeVo ok = service.insertTAdrTermCard(etForm(SITE_A, "30", "皮肤科、儿科，内科; 急诊科"));
        assertTrue(ok.isAccepted(), ok.getMessage());
        assertEquals(4, ok.getCard().getExtendGroupCount(), "顿号逗号分号分隔，系统点出 4 组");
    }

    @Test
    void 份数零或负_复核时限负_逐栏指名不进库() {
        TermCardOpenForm f0 = form(SITE_A, "0");
        TermCardIntakeVo r0 = service.insertTAdrTermCard(f0);
        assertFalse(r0.isAccepted());
        assertTrue(r0.getFaults().get(0).contains("qty") && r0.getFaults().get(0).contains("0"));

        TermCardOpenForm fn = form(SITE_A, "-8");
        fn.setFineAmt(new BigDecimal("-3"));
        TermCardIntakeVo rn = service.insertTAdrTermCard(fn);
        assertFalse(rn.isAccepted());
        assertEquals(2, rn.getFaults().size(), "份数负、时限负，两栏各指一名");
        assertEquals(0, cardMapper.selectCount(null).intValue());
    }

    // ---------------- 开口收口先问一句 ----------------

    @Test
    void 开口比收口晚_先问一句不进库_确认后才收() throws Exception {
        TermCardOpenForm f = form(SITE_A, "50");
        f.setOpenAt(at("2026-09-10 00:00:00"));
        f.setCloseAt(at("2026-09-01 00:00:00"));

        TermCardIntakeVo ask = service.insertTAdrTermCard(f);
        assertFalse(ask.isAccepted());
        assertTrue(ask.isNeedConfirm());
        assertNotNull(ask.getPending(), "备一份原样回话");
        assertEquals(0, cardMapper.selectCount(null).intValue(), "库里暂不落账");

        TermCardIntakeVo yes = service.insertTAdrTermCard(f, true);
        assertTrue(yes.isAccepted(), yes.getMessage());
        assertEquals(1, cardMapper.selectCount(null).intValue());
    }

    // ---------------- 底册：只认代号、一册一家、退出封通道 ----------------

    @Test
    void 底册代号空着或查无_名号像也不算对上() {
        TermCardIntakeVo empty = service.insertTAdrTermCard(form("", "50"));
        assertFalse(empty.isAccepted());
        assertTrue(empty.getFaults().get(0).contains("siteNo"));

        TermCardIntakeVo ghost = service.insertTAdrTermCard(form("YFXX", "50"));
        assertFalse(ghost.isAccepted());
        assertTrue(ghost.getFaults().get(0).contains("查无此家"));
    }

    @Test
    void 年内退出的底册封住通道_新卡不挂_先前旧卡照旧查得着() {
        TAdrTermCard legacy = rawCard(c -> c.setSiteNo(SITE_EXIT));

        TermCardIntakeVo r = service.insertTAdrTermCard(form(SITE_EXIT, "50"));
        assertFalse(r.isAccepted());
        assertTrue(r.getFaults().get(0).contains("已退出"));
        assertEquals(1, cardMapper.selectCount(null).intValue(), "先前所挂那张照旧在");
        assertNotNull(service.selectTAdrTermCardById(legacy.getId()), "旧卡查得着，不跟着底册一起消失");
    }

    // ---------------- 同单位同分类在走唯一 ----------------

    @Test
    void 同单位同分类头一张没冻住_第二张立不起来() {
        assertTrue(service.insertTAdrTermCard(form(SITE_A, "50")).isAccepted());

        TermCardIntakeVo second = service.insertTAdrTermCard(form(SITE_A, "60"));
        assertFalse(second.isAccepted());
        assertTrue(second.getMessage().contains("一张在走的卡"));
        assertEquals(1, cardMapper.selectCount(null).intValue());
    }

    @Test
    void 别单位同一天报同一分类_或同分类改名重报_都不许叠两卡() {
        assertTrue(service.insertTAdrTermCard(form(SITE_A, "50")).isAccepted());

        // 另一家单位同分类：另册各挂各的，互不顶替（一卡只挂一家）
        assertTrue(service.insertTAdrTermCard(form(SITE_B, "50")).isAccepted());
        assertEquals(2, cardMapper.selectCount(null).intValue());

        // 同单位换个症状词再报，分类身份（层级+名号+上位码）没变，仍挡
        TermCardOpenForm again = form(SITE_A, "70");
        again.setSymptom("另一个症状");
        assertFalse(service.insertTAdrTermCard(again).isAccepted());
    }

    @Test
    void 走到冻住名额空出_同分类才接得上新卡() {
        TermCardIntakeVo first = service.insertTAdrTermCard(form(SITE_A, "50"));
        Long id = first.getCard().getId();
        assertNotNull(service.advance(id)); // 已核对
        assertNotNull(service.advance(id)); // 已冻住

        TermCardIntakeVo next = service.insertTAdrTermCard(form(SITE_A, "120"));
        assertTrue(next.isAccepted(), next.getMessage());
        assertEquals(3, next.getCard().getGradeLevel(), "新卡份数 120 照界线折 3 档");
        assertNotEquals(id, next.getCard().getId());
    }

    // ---------------- 三态步子 ----------------

    @Test
    void 步子只许顺序迈_抢步越级不放行() {
        TAdrTermCard c = service.insertTAdrTermCard(form(SITE_A, "50")).getCard();

        assertEquals(0, c.getStatus());
        TAdrTermCard checked = service.advance(c.getId());
        assertEquals(1, checked.getStatus());
        assertNotNull(checked.getContent(), "已核对这一步封校验串");
        TAdrTermCard frozen = service.advance(c.getId());
        assertEquals(2, frozen.getStatus());

        assertNull(service.advance(c.getId()), "冻住后迈不动");
    }

    @Test
    void 冻住即终局_加不进改不动撤不下() {
        TAdrTermCard c = service.insertTAdrTermCard(form(SITE_A, "50")).getCard();
        service.advance(c.getId());
        service.advance(c.getId());

        TAdrTermCard patch = new TAdrTermCard();
        patch.setId(c.getId());
        patch.setQty(new BigDecimal("66"));
        assertEquals(0, service.updateTAdrTermCard(patch), "冻住改不动");
        assertEquals(0, service.deleteTAdrTermCardById(c.getId()), "冻住撤不下");
        assertFalse(service.revise(c.getId(), form(SITE_A, "60")).isAccepted(), "冻住要改版也不行，只够另开一张");
    }

    @Test
    void 已核对可退回待核对重做_先前封住的串保留不覆() {
        TAdrTermCard c = service.insertTAdrTermCard(form(SITE_A, "50")).getCard();
        service.advance(c.getId());
        String sealed = service.selectTAdrTermCardById(c.getId()).getContent();
        assertNotNull(sealed);

        TAdrTermCard back = service.rollback(c.getId());
        assertEquals(0, back.getStatus());
        assertEquals(sealed, service.selectTAdrTermCardById(c.getId()).getContent(), "先前那趟的串保留，不与后补并排摆");

        assertNull(service.rollback(c.getId()), "待核对无步可退");
        service.advance(c.getId());
        service.advance(c.getId());
        assertNull(service.rollback(c.getId()), "冻住后路不通");
    }

    // ---------------- 改版：旧版沉历史、新串不共用 ----------------

    @Test
    void 改版新版一立旧版挪历史_版次加一卡号术语码不变_两版各配各串() {
        TAdrTermCard v1 = service.insertTAdrTermCard(form(SITE_A, "50")).getCard();
        service.advance(v1.getId()); // v1 封串
        String v1Sum = service.selectTAdrTermCardById(v1.getId()).getContent();

        TermCardOpenForm f2 = form(SITE_A, "120");
        TermCardIntakeVo r2 = service.revise(v1.getId(), f2);
        assertTrue(r2.isAccepted(), r2.getMessage());
        TAdrTermCard v2 = r2.getCard();
        assertEquals(v1.getBillNo(), v2.getBillNo(), "同一张卡卡号不变");
        assertEquals(v1.getTermCode(), v2.getTermCode(), "同一分类术语编码不变");
        assertEquals(2, v2.getNodeNo(), "版次序加一");
        assertEquals(0, v2.getStatus(), "新版从头走");
        assertNull(v2.getContent(), "新版未到已核对，先不封串");
        assertEquals(3, v2.getGradeLevel(), "120 重折为 3 档");

        TAdrTermCard oldRow = cardMapper.selectById(v1.getId());
        assertEquals(0, oldRow.getCurrentFlag().intValue(), "旧版挪进历史层");
        assertEquals(v1Sum, oldRow.getContent(), "旧版那行留旧串");

        service.advance(v2.getId());
        String v2Sum = service.selectTAdrTermCardById(v2.getId()).getContent();
        assertNotEquals(v1Sum, v2Sum, "新版配新串，两版共用一串就算错");

        assertEquals(1, service.selectTAdrTermCardList(null).size(), "日常名录只见最新一版");
        List<TAdrTermCard> versions = service.listVersions(v2.getId());
        assertEquals(2, versions.size(), "旧版不删，备事后追溯");
    }

    @Test
    void 旧版在历史层不占名额_但同分类新版在走仍挡第二张() {
        TAdrTermCard v1 = service.insertTAdrTermCard(form(SITE_A, "50")).getCard();
        assertTrue(service.revise(v1.getId(), form(SITE_A, "60")).isAccepted());

        TermCardIntakeVo second = service.insertTAdrTermCard(form(SITE_A, "70"));
        assertFalse(second.isAccepted(), "新版还在走，名额没腾空");
    }

    // ---------------- 份数改、底册改：重算监督层级 ----------------

    @Test
    void 份数有增有减当场重折监督层级_栏不给手填() {
        TAdrTermCard c = service.insertTAdrTermCard(form(SITE_A, "50")).getCard();
        assertEquals(2, c.getGradeLevel());

        TAdrTermCard up = new TAdrTermCard();
        up.setId(c.getId());
        up.setQty(new BigDecimal("90"));
        assertEquals(1, service.updateTAdrTermCard(up));
        assertEquals(3, service.selectTAdrTermCardById(c.getId()).getGradeLevel(), "增到 90 重折 3 档");

        TAdrTermCard down = new TAdrTermCard();
        down.setId(c.getId());
        down.setQty(new BigDecimal("10"));
        service.updateTAdrTermCard(down);
        assertEquals(1, service.selectTAdrTermCardById(c.getId()).getGradeLevel(), "减到 10 重折 1 档");

        TAdrTermCard hand = new TAdrTermCard();
        hand.setId(c.getId());
        hand.setGradeLevel(9);
        service.updateTAdrTermCard(hand);
        assertEquals(1, service.selectTAdrTermCardById(c.getId()).getGradeLevel(), "手填层级栏没有口子");
    }

    @Test
    void 换挂靠底册照新底册界线重折_只认代号_旧底册不再压着() {
        seedOrg("YF77", 0, "5", "10", "15");
        TAdrTermCard c = service.insertTAdrTermCard(form(SITE_A, "12")).getCard();
        assertEquals(1, c.getGradeLevel(), "旧底册 20/80/300 下 12 份归 1 档");

        TAdrTermCard move = new TAdrTermCard();
        move.setId(c.getId());
        move.setSiteNo("YF77");
        assertEquals(1, service.updateTAdrTermCard(move));
        TAdrTermCard after = service.selectTAdrTermCardById(c.getId());
        assertEquals("YF77", after.getSiteNo());
        assertEquals(3, after.getGradeLevel(), "新底册 5/10/15 下 12 份归 3 档");
    }

    @Test
    void 修改时份数零负时限负开口倒置_一律不收() throws Exception {
        TAdrTermCard c = service.insertTAdrTermCard(form(SITE_A, "50")).getCard();

        TAdrTermCard zero = new TAdrTermCard();
        zero.setId(c.getId());
        zero.setQty(BigDecimal.ZERO);
        assertEquals(0, service.updateTAdrTermCard(zero));

        TAdrTermCard negFine = new TAdrTermCard();
        negFine.setId(c.getId());
        negFine.setFineAmt(new BigDecimal("-1"));
        assertEquals(0, service.updateTAdrTermCard(negFine));

        TAdrTermCard badTime = new TAdrTermCard();
        badTime.setId(c.getId());
        badTime.setOpenAt(at("2026-12-01 00:00:00"));
        badTime.setCloseAt(at("2026-01-01 00:00:00"));
        assertEquals(0, service.updateTAdrTermCard(badTime), "开口晚于收口须先问，旧口收不了这个确认");
    }

    // ---------------- 旧纸卡兼容 ----------------

    @Test
    void 旧纸卡步数版次校验串空着_不报错_照在走的卡接住() {
        TAdrTermCard legacy = rawCard(c -> {
            c.setStatus(null);
            c.setNodeNo(null);
            c.setContent(null);
            c.setCurrentFlag(null);
        });

        TAdrTermCard view = service.selectTAdrTermCardById(legacy.getId());
        assertEquals(0, view.getStatus(), "步数空着照待核对认，不当成已停了的");
        assertEquals(1, view.getNodeNo(), "版次空着照第 1 版认");
        assertEquals(1, view.getCurrentFlag().intValue());

        TAdrTermCard checked = service.advance(legacy.getId());
        assertEquals(1, checked.getStatus());
        assertNotNull(checked.getContent(), "空串到核对这一步现封，不报错");
    }

    // ---------------- 检索 ----------------

    @Test
    void 三处开搜_从头与中间模糊都中_命中带上位码与层级_只摆本级现行() {
        service.insertTAdrTermCard(form(SITE_A, "50"));
        service.insertTAdrTermCard(form(SITE_B, "50"));

        List<TermSearchHit> byName = service.search(SITE_A, "消化");
        assertEquals(1, byName.size());
        assertEquals("SOC-GI", byName.get(0).getParentCode(), "命中原样带出上位分类码");
        assertEquals(1, byName.get(0).getTermLevel(), "带出所在层级，不劳再抄");

        assertEquals(1, service.search(SITE_A, "恶心").size(), "症状词中间模糊中");
        assertEquals(1, service.search(SITE_A, "GI").size(), "上位码中间模糊中");
        assertEquals(1, service.search(SITE_A, "SOC").size(), "上位码从头匹配中");
        assertTrue(service.search(SITE_A, "心血管").isEmpty(), "本级底下空着不摆空行");
        assertTrue(service.search(SITE_A, "").isEmpty(), "空词不搜、不凭空补卡");

        // 别级的卡不同屏并列
        assertEquals(0, service.search("YF-NO-SUCH", "消化").size());
        // 不给本级口径时跨在册单位，但历史版次不摆
        assertEquals(2, service.search(null, "消化").size());
    }

    @Test
    void 检索不摆历史版次不盖在场旧版() {
        TAdrTermCard v1 = service.insertTAdrTermCard(form(SITE_A, "50")).getCard();
        service.revise(v1.getId(), form(SITE_A, "60"));

        List<TermSearchHit> hits = service.search(SITE_A, "消化");
        assertEquals(1, hits.size(), "旧版沉历史，检索只见新版那一张");
        assertEquals(2, hits.get(0).getNodeNo().intValue());
    }

    // ---------------- 名录占数同源 ----------------

    @Test
    void 名录卡数与占数同一回点算_立卡加一冻住减一() {
        TermRosterVo empty = service.roster(SITE_A);
        assertEquals(0, empty.getTotalCount());
        assertEquals(0, empty.getOccupyingCount());
        assertTrue(empty.getCards().isEmpty(), "不凭空补一张");

        TAdrTermCard a = service.insertTAdrTermCard(form(SITE_A, "50")).getCard();
        TermCardOpenForm other = ptForm(SITE_A, "50");
        other.setTermName("皮疹");
        other.setParentCode("SOC-SKIN");
        TAdrTermCard b = service.insertTAdrTermCard(other).getCard();

        TermRosterVo two = service.roster(SITE_A);
        assertEquals(2, two.getTotalCount());
        assertEquals(2, two.getPendingCount());
        assertEquals(2, two.getOccupyingCount(), "立卡加一");
        assertEquals(2, two.getCards().size(), "屏上摆的与点的数同源");

        service.advance(a.getId());
        TermRosterVo checked = service.roster(SITE_A);
        assertEquals(1, checked.getCheckedCount());
        assertEquals(2, checked.getOccupyingCount(), "已核对仍占名额");

        service.advance(a.getId());
        TermRosterVo frozen = service.roster(SITE_A);
        assertEquals(1, frozen.getFrozenCount());
        assertEquals(1, frozen.getOccupyingCount(), "冻住减一，名额空出");

        service.advance(b.getId());
        service.advance(b.getId());
        assertEquals(0, service.roster(SITE_A).getOccupyingCount());
    }

    // ---------------- 层级点栏 ----------------

    @Test
    void 哪几栏非写不可由服务按层级当场点() {
        assertTrue(service.requiredFields(1).contains("parentCode"));
        assertTrue(service.requiredFields(1).contains("basisClause"));
        assertTrue(service.requiredFields(2).contains("refineItems"));
        assertTrue(service.requiredFields(2).contains("collectYear"));
        assertEquals(1, service.requiredFields(3).size());
        assertEquals("extendGroups", service.requiredFields(3).get(0));
        assertTrue(service.requiredFields(null).isEmpty(), "层级空着没选，点不出栏，进门那关另拦");
    }

    // ---------------- 删除沉底、名额让出 ----------------

    @Test
    void 在走的卡撤下_同卡号各版整批沉底_名额让出_旧版不物理删() {
        TAdrTermCard v1 = service.insertTAdrTermCard(form(SITE_A, "50")).getCard();
        service.revise(v1.getId(), form(SITE_A, "60"));
        Long v2Id = service.selectTAdrTermCardList(null).get(0).getId();

        assertEquals(2, service.deleteTAdrTermCardById(v2Id), "同卡号两版一起沉底");
        assertEquals(0, service.roster(SITE_A).getTotalCount());
        List<TAdrTermCard> versions = service.listVersions(v2Id);
        assertEquals(2, versions.size(), "一句不毁，事后追溯还翻得着两版");

        assertTrue(service.insertTAdrTermCard(form(SITE_A, "50")).isAccepted(), "名额让出来，同分类接得上新卡");
    }
}
