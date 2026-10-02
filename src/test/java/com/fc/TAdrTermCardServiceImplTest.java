package com.fc;

import com.fc.v2.model.auto.TAdrTermCard;
import com.fc.v2.model.auto.TAdrTermTaxon;
import com.fc.v2.model.custom.adr.CardOpenReceiptVo;
import com.fc.v2.model.custom.adr.CardRosterVo;
import com.fc.v2.model.custom.adr.TermSearchHitVo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 术语分类卡规矩集成测试——把去年抽查追出的缝一条条堵上：
 * 一卡两挂、换单位不换底册、份数改两回层级算两样、旧版并排现行、空栏回"数不像话"。
 */
class TAdrTermCardServiceImplTest extends AdrTermCardTestBase {

    private List<String> fields(CardOpenReceiptVo vo) {
        return vo.getMissingFields().stream()
                .map(CardOpenReceiptVo.Missing::getField).collect(Collectors.toList());
    }

    // ---------------- 层级不同，交的栏不同；缺栏指名 ----------------

    @Test
    void 层级空着没选_只点层级等公共缺栏_卡不进库() {
        TAdrTaxonPair soc = soc(3);
        TAdrTermCard c = draft("YF00", soc.code, null, "50", "5");
        CardOpenReceiptVo vo = service.insertTAdrTermCard(c, 3);
        assertFalse(vo.isAccepted());
        assertTrue(fields(vo).contains("taxonLevel"));
        assertNull(vo.getBillNo());
    }

    @Test
    void 系统器官分类层_缺上位码与条款号_逐条指名() {
        TAdrTaxonPair soc = soc(3);
        TAdrTermCard c = draft("YF00", soc.code, 1, "50", "5");
        CardOpenReceiptVo vo = service.insertTAdrTermCard(c, 3);
        assertFalse(vo.isAccepted());
        List<String> f = fields(vo);
        assertTrue(f.contains("parentCode"));
        assertTrue(f.contains("clauseNo"));
    }

    @Test
    void 首选术语层_缺本市细化与收词年度_逐条指名() {
        TAdrTaxonPair pt = pt(3);
        TAdrTermCard c = draft("YF00", pt.code, 2, "50", "5");
        CardOpenReceiptVo vo = service.insertTAdrTermCard(c, 3);
        assertTrue(fields(vo).contains("cityItems"));
        assertTrue(fields(vo).contains("collectYear"));
    }

    @Test
    void 延伸术语层_缺专业组_逐条指名并由系统点组数() {
        TAdrTaxonPair et = et(3);
        TAdrTermCard c = draft("YF00", et.code, 3, "50", "5");
        CardOpenReceiptVo vo = service.insertTAdrTermCard(c, 3);
        assertTrue(fields(vo).contains("extGroups"));

        TAdrTermCard ok = draft("YF00", et.code, 3, "50", "5");
        ok.setExtGroups("消化组、心血管组、消化组"); // 重复组只点一次
        CardOpenReceiptVo accepted = service.insertTAdrTermCard(ok, 3);
        assertTrue(accepted.isAccepted());
        assertEquals(2, cardMapper.selectById(accepted.getId()).getExtGroupCount());
    }

    @Test
    void 份数零或负_复核时限负_都不进库且指名到栏() {
        TAdrTaxonPair pt = pt(3);
        TAdrTermCard zero = draft("YF00", pt.code, 2, "0", "5");
        zero.setCityItems("细化一");
        zero.setCollectYear(2025);
        assertTrue(fields(service.insertTAdrTermCard(zero, 3)).contains("qty"));

        TAdrTermCard neg = draft("YF00", pt.code, 2, "-3", "5");
        neg.setCityItems("细化一");
        neg.setCollectYear(2025);
        assertTrue(fields(service.insertTAdrTermCard(neg, 3)).contains("qty"));

        TAdrTermCard fineNeg = draft("YF00", pt.code, 2, "3", "-1");
        fineNeg.setCityItems("细化一");
        fineNeg.setCollectYear(2025);
        assertTrue(fields(service.insertTAdrTermCard(fineNeg, 3)).contains("fineAmt"));
    }

    // ---------------- 号由系统发，自拟号不存 ----------------

    @Test
    void 卡号系统发_自拟的号不存() {
        TAdrTaxonPair pt = pt(3);
        TAdrTermCard c = draft("YF00", pt.code, 2, "50", "5");
        c.setCityItems("细化一、细化二");
        c.setCollectYear(2025);
        c.setBillNo("WO-ZI-JI-NI-DE-HAO");
        CardOpenReceiptVo vo = service.insertTAdrTermCard(c, 3);
        assertFalse(vo.isAccepted());
        assertTrue(fields(vo).contains("billNo"));
    }

    @Test
    void 术语编码系统发_层级套层级() {
        TAdrTaxonPair soc = soc(3);
        TAdrTermTaxon made = taxon(2, "市里首选", soc.code, 3);
        assertTrue(made.getTermCode().startsWith("TM"));
        assertEquals(soc.code, made.getParentCode());

        TAdrTermTaxon bad = new TAdrTermTaxon();
        bad.setTermName("越级的");
        bad.setTaxonLevel(3);
        bad.setOwnLevel(3);
        bad.setParentCode(soc.code); // 系统器官分类与延伸术语中间还隔着首选术语
        assertThrows(IllegalArgumentException.class, () -> service.registerTaxon(bad));
    }

    // ---------------- 底册：只认代号、退出封通道 ----------------

    @Test
    void 底册代号查无_名号像不算对上_不进库() {
        TAdrTaxonPair pt = pt(3);
        TAdrTermCard c = draft("YF99", pt.code, 2, "50", "5");
        c.setCityItems("细化一");
        c.setCollectYear(2025);
        assertTrue(fields(service.insertTAdrTermCard(c, 3)).contains("siteNo"));
    }

    @Test
    void 年内退出的底册封新卡通道_旧卡照旧查得着() {
        TAdrTaxonPair pt = pt(3);
        TAdrTermCard old = seedPaperCard("YF01", pt.code); // 已退出底册下的旧卡
        assertNotNull(service.selectTAdrTermCardById(old.getId()));

        TAdrTermCard c = draft("YF01", pt.code, 2, "50", "5");
        c.setCityItems("细化一");
        c.setCollectYear(2025);
        assertTrue(fields(service.insertTAdrTermCard(c, 3)).contains("siteNo"));
    }

    // ---------------- 监督层级：三界线，压线取上，只在三当口重算 ----------------

    @Test
    void 三条界线折监督层级_恰好压线算跨过去取上层() {
        TAdrTaxonPair pt = pt(3);
        // 同单位同分类只容一张在走，每回立完冻住腾出名额再立下一版
        TAdrTermCard c;
        c = openAt(pt, "19"); assertEquals(1, c.getGradeLevel()); freeze(c);  // 够不着低界
        c = openAt(pt, "20"); assertEquals(2, c.getGradeLevel()); freeze(c);  // 与低界持平→跨过去
        c = openAt(pt, "79"); assertEquals(2, c.getGradeLevel()); freeze(c);
        c = openAt(pt, "80"); assertEquals(3, c.getGradeLevel()); freeze(c);  // 与中界持平
        c = openAt(pt, "299"); assertEquals(3, c.getGradeLevel()); freeze(c);
        c = openAt(pt, "300"); assertEquals(4, c.getGradeLevel());            // 与高界持平→到顶
    }

    private void freeze(TAdrTermCard c) {
        service.advance(c.getId());
        service.advance(c.getId());
    }

    @Test
    void 份数改两回_层级每回照界线重算落栏() {
        TAdrTaxonPair pt = pt(3);
        TAdrTermCard card = openAt(pt, "50");
        assertEquals(2, card.getGradeLevel());
        card = service.changeQty(card.getId(), new BigDecimal("80"));
        assertEquals(3, card.getGradeLevel());
        card = service.changeQty(card.getId(), new BigDecimal("300"));
        assertEquals(4, card.getGradeLevel());
        final Long cardId = card.getId();
        // 零、负不收
        assertThrows(IllegalArgumentException.class,
                () -> service.changeQty(cardId, BigDecimal.ZERO));
    }

    @Test
    void 改挂靠底册_照新底册界线重折_只认代号() {
        seedOrg("YF02", 0, new BigDecimal("10"), new BigDecimal("40"), new BigDecimal("100"));
        TAdrTaxonPair pt = pt(3);
        TAdrTermCard card = openAt(pt, "50");
        assertEquals(2, card.getGradeLevel()); // 老底册 20/80/300 下是第2层
        card = service.changeSite(card.getId(), "YF02");
        assertEquals("YF02", card.getSiteNo());
        assertEquals(3, card.getGradeLevel()); // 新底册 10/40/100 下 50 到中界、未及高界→第3层
        final Long cardId = card.getId();
        assertThrows(IllegalArgumentException.class, () -> service.changeSite(cardId, "YF99"));
    }

    // ---------------- 同单位同分类一张在走卡 ----------------

    @Test
    void 同单位同分类_头一张未冻住_第二张立不起来() {
        TAdrTaxonPair pt = pt(3);
        assertTrue(openAt(pt, "50") != null);
        CardOpenReceiptVo again = receiptAt(pt, "60");
        assertFalse(again.isAccepted());
        assertTrue(fields(again).contains("familyNo"));
    }

    @Test
    void 冻住后名额空出_同分类才接得上新卡() {
        TAdrTaxonPair pt = pt(3);
        TAdrTermCard first = openAt(pt, "50");
        service.advance(first.getId()); // 已核对
        service.advance(first.getId()); // 已冻住
        TAdrTermCard second = openAt(pt, "60");
        assertNotNull(second);
        assertNotEquals(first.getId(), second.getId());
    }

    // ---------------- 三态步子 ----------------

    @Test
    void 步子只许顺序迈_冻住后改不动撤不下() {
        TAdrTaxonPair pt = pt(3);
        TAdrTermCard c = openAt(pt, "50");
        assertEquals(0, c.getStatus());
        assertNull(c.getContent());
        c = service.advance(c.getId());
        assertEquals(1, c.getStatus());
        assertNotNull(c.getContent()); // 已核对封一版校验串
        c = service.advance(c.getId());
        assertEquals(2, c.getStatus());
        assertNull(service.advance(c.getId())); // 没有下一格

        // 冻住后份数、底册、改版、删除一概不动
        assertNull(service.changeQty(c.getId(), new BigDecimal("51")));
        assertNull(service.changeSite(c.getId(), "YF00"));
        assertNull(service.revise(c.getId(), new TAdrTermCard()));
        assertEquals(0, service.deleteTAdrTermCardById(c.getId()));
    }

    @Test
    void 已核对退得回待核对_先前校验串留着不毁() {
        TAdrTaxonPair pt = pt(3);
        TAdrTermCard c = openAt(pt, "50");
        service.advance(c.getId());
        String seal = cardMapper.selectById(c.getId()).getContent();
        assertNotNull(seal);
        c = service.rollback(c.getId());
        assertEquals(0, c.getStatus());
        assertEquals(seal, cardMapper.selectById(c.getId()).getContent()); // 先前那趟原样保留
        assertNull(service.rollback(c.getId())); // 待核对无步可退
    }

    // ---------------- 改版：旧版入历史、新串旧串分开 ----------------

    @Test
    void 新版一立旧版入历史_名录只见新版_校验串各是各() {
        TAdrTaxonPair pt = pt(3);
        TAdrTermCard v1 = openAt(pt, "50");
        service.advance(v1.getId()); // v1 已核对，封了旧串
        String oldSeal = cardMapper.selectById(v1.getId()).getContent();

        TAdrTermCard changes = new TAdrTermCard();
        changes.setQty(new BigDecimal("90"));
        TAdrTermCard v2 = service.revise(v1.getId(), changes);
        assertNotNull(v2);
        assertEquals(2, v2.getNodeNo());
        assertEquals(0, v2.getVersionFlag());
        assertEquals(1, (int) cardMapper.selectById(v1.getId()).getVersionFlag()); // 旧版挪历史
        assertNull(v2.getContent()); // 新版待核对，串还没封

        service.advance(v2.getId());
        String newSeal = cardMapper.selectById(v2.getId()).getContent();
        assertNotEquals(oldSeal, newSeal); // 两版共用一串就算错

        CardRosterVo roster = service.roster(3);
        assertEquals(1, roster.getCurrentCount()); // 现行名录只摆新版
        assertEquals(v2.getId(), roster.getCards().get(0).getId());
    }

    // ---------------- 旧纸卡接住 ----------------

    @Test
    void 旧纸卡空步空版空串_不报错且仍算在走_名录占名额() {
        TAdrTaxonPair pt = pt(3);
        TAdrTermCard paper = seedPaperCard("YF00", pt.code);
        TAdrTermCard state = service.currentState(paper.getId());
        assertNull(state.getStatus()); // 空步照旧空，不猜成停了
        CardRosterVo roster = service.roster(3);
        assertEquals(1, roster.getOccupiedCount()); // 仍在走，占名额
        assertEquals(0, roster.getFrozenCount());

        // 同分类第二张新卡立不起来（旧纸卡占着名额）
        assertFalse(receiptAt(pt, "50").isAccepted());

        // 一推进去，先补版次序再封串
        TAdrTermCard advanced = service.advance(paper.getId());
        assertEquals(1, (int) advanced.getStatus());
        assertEquals(1, (int) advanced.getNodeNo());
        assertNotNull(advanced.getContent());
    }

    // ---------------- 检索 ----------------

    @Test
    void 检索三处开搜_前缀与中间模糊_命中带上位码_只摆本级() {
        TAdrTaxonPair pt3 = pt(3);   // 自带上级（系统器官分类）编码
        et(3);
        soc(2); // 别级词条，不该混进来

        List<TermSearchHitVo> prefix = service.searchTerms("消化", 3);
        TermSearchHitVo hit = prefix.stream()
                .filter(h -> h.getTermCode().equals(pt3.code)).findFirst().orElse(null);
        assertNotNull(hit);
        assertEquals(pt3.parent, hit.getParentCode()); // 上位码直接带出
        assertEquals(2, (int) hit.getTaxonLevel());
        assertTrue(prefix.stream().allMatch(h -> h.getOwnLevel() == 3)); // 只摆本级

        // 中间模糊：拿症状词的一截也搜得到
        List<TermSearchHitVo> fuzzy = service.searchTerms("状", 3);
        assertTrue(fuzzy.stream().anyMatch(h -> h.getTermCode().equals(pt3.code)));

        // 上位分类码也开搜
        assertTrue(service.searchTerms(pt3.parent, 3).stream()
                .anyMatch(h -> h.getTermCode().equals(pt3.code)));

        // 空口不搜；本级底下空着不摆空行
        assertTrue(service.searchTerms("", 3).isEmpty());
        assertTrue(service.searchTerms("压根没有的词", 3).isEmpty());
    }

    // ---------------- 名录占数与屏同源 ----------------

    @Test
    void 名录占数_立卡加一冻住减一_数与行同回点算() {
        TAdrTaxonPair a = pt(3);
        TAdrTaxonPair b = et(3);
        TAdrTermCard c1 = openAt(a, "50");
        openAt(b, "50");
        CardRosterVo r1 = service.roster(3);
        assertEquals(2, r1.getCurrentCount());
        assertEquals(2, r1.getOccupiedCount());
        assertEquals(0, r1.getFrozenCount());

        service.advance(c1.getId());
        service.advance(c1.getId());
        CardRosterVo r2 = service.roster(3);
        assertEquals(2, r2.getCurrentCount());
        assertEquals(1, r2.getOccupiedCount()); // 冻住那张名额空出
        assertEquals(1, r2.getFrozenCount());
    }

    // ---------------- 旧门签名留着，规矩不留后门 ----------------

    @Test
    void 旧签名insert不合规回0() {
        TAdrTaxonPair pt = pt(3);
        TAdrTermCard c = draft("YF00", pt.code, null, "50", "5");
        assertEquals(0, service.insertTAdrTermCard(c));
    }

    // =============== 帮手 ===============

    private TAdrTermCard openAt(TAdrTaxonPair t, String qty) {
        CardOpenReceiptVo vo = receiptAt(t, qty);
        assertTrue(vo.isAccepted(), () -> vo.getMissingFields().stream()
                .map(m -> m.getField() + ":" + m.getReason()).collect(Collectors.joining(";")));
        return cardMapper.selectById(vo.getId());
    }

    private CardOpenReceiptVo receiptAt(TAdrTaxonPair t, String qty) {
        TAdrTermCard c = draft("YF00", t.code, t.level, qty, "5");
        if (t.level == 1) {
            c.setParentCode(t.parent);
            c.setClauseNo("CLA-9");
        } else if (t.level == 2) {
            c.setCityItems("本市细化甲、本市细化乙");
            c.setCollectYear(2025);
        } else {
            c.setExtGroups("消化组、心血管组");
        }
        return service.insertTAdrTermCard(c, 3);
    }

    private TAdrTaxonPair soc(int own) {
        TAdrTermTaxon t = taxon(1, "消化系统损害", null, own);
        return new TAdrTaxonPair(t.getTermCode(), t.getTermCode(), 1);
    }

    private TAdrTaxonPair pt(int own) {
        TAdrTaxonPair s = soc(own);
        TAdrTermTaxon t = taxon(2, "消化不适首选", s.code, own);
        return new TAdrTaxonPair(t.getTermCode(), s.code, 2);
    }

    private TAdrTaxonPair et(int own) {
        TAdrTaxonPair p = pt(own);
        TAdrTermTaxon t = taxon(3, "消化延伸术语", p.code, own);
        return new TAdrTaxonPair(t.getTermCode(), p.code, 3);
    }

    private static class TAdrTaxonPair {
        final String code;
        final String parent;
        final int level;

        TAdrTaxonPair(String code, String parent, int level) {
            this.code = code;
            this.parent = parent;
            this.level = level;
        }
    }
}
