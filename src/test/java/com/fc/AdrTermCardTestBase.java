package com.fc;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.TAdrOrgArchMapper;
import com.fc.v2.mapper.auto.TAdrTermCardMapper;
import com.fc.v2.mapper.auto.TAdrTermTaxonMapper;
import com.fc.v2.model.auto.TAdrOrgArch;
import com.fc.v2.model.auto.TAdrTermCard;
import com.fc.v2.model.auto.TAdrTermTaxon;
import com.fc.v2.service.impl.TAdrTermCardServiceImpl;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;

import javax.sql.DataSource;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 术语分类卡领域服务集成测试基台：H2(MODE=MySQL) + MyBatis-Plus，不起 Spring。
 */
public abstract class AdrTermCardTestBase {

    protected TAdrTermCardServiceImpl service;
    protected TAdrTermCardMapper cardMapper;
    protected TAdrTermTaxonMapper taxonMapper;
    protected TAdrOrgArchMapper orgArchMapper;

    private final AtomicLong idSeq = new AtomicLong(5000);

    /** 三界线 20/80/300，与种子底册一致 */
    protected static final BigDecimal LOW = new BigDecimal("20");
    protected static final BigDecimal MID = new BigDecimal("80");
    protected static final BigDecimal HIGH = new BigDecimal("300");

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:term_" + getClass().getSimpleName() + "_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");

        try (Connection conn = ds.getConnection();
             Reader reader = new InputStreamReader(
                     getClass().getResourceAsStream("/schema-h2.sql"), StandardCharsets.UTF_8)) {
            RunScript.execute(conn, reader);
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource((DataSource) ds);
        factoryBean.setConfiguration(configuration);
        SqlSessionFactory factory = factoryBean.getObject();
        factory.getConfiguration().addMapper(TAdrTermCardMapper.class);
        factory.getConfiguration().addMapper(TAdrTermTaxonMapper.class);
        factory.getConfiguration().addMapper(TAdrOrgArchMapper.class);

        SqlSession session = factory.openSession(true);
        this.cardMapper = session.getMapper(TAdrTermCardMapper.class);
        this.taxonMapper = session.getMapper(TAdrTermTaxonMapper.class);
        this.orgArchMapper = session.getMapper(TAdrOrgArchMapper.class);

        this.service = new TAdrTermCardServiceImpl();
        inject(this.service, "baseMapper",
                com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField("baseMapper"),
                this.cardMapper);
        inject(this.service, "orgArchMapper", TAdrTermCardServiceImpl.class.getDeclaredField("orgArchMapper"), this.orgArchMapper);
        inject(this.service, "termTaxonMapper", TAdrTermCardServiceImpl.class.getDeclaredField("termTaxonMapper"), this.taxonMapper);

        seedOrg("YF00", 0, LOW, MID, HIGH);
        seedOrg("YF01", 1, LOW, MID, HIGH);
    }

    private void inject(Object target, String name, Field f, Object value) throws Exception {
        f.setAccessible(true);
        f.set(target, value);
    }

    protected long nextId() {
        return idSeq.incrementAndGet();
    }

    protected void seedOrg(String siteNo, int status, BigDecimal th1, BigDecimal th2, BigDecimal th3) {
        TAdrOrgArch org = new TAdrOrgArch();
        org.setId(nextId());
        org.setSiteNo(siteNo);
        org.setSiteName(siteNo + "底册");
        org.setSiteType("医疗机构");
        org.setRoadName("澜川省—澜川市—澜川县");
        org.setTh1Max(th1);
        org.setTh2Max(th2);
        org.setTh3Max(th3);
        org.setStatus(status);
        org.setDelFlag(0);
        this.orgArchMapper.insert(org);
    }

    /** 登记一条术语树条目（走服务发号），返回落库条目 */
    protected TAdrTermTaxon taxon(int level, String name, String parentCode, int ownLevel) {
        TAdrTermTaxon t = new TAdrTermTaxon();
        t.setTermName(name);
        t.setSymptomWords(name + "症状");
        t.setTaxonLevel(level);
        t.setOwnLevel(ownLevel);
        t.setParentCode(parentCode);
        if (level == 1) {
            t.setClauseNo("CLA-1");
        }
        if (level == 2) {
            t.setCollectYear(2025);
        }
        return service.registerTaxon(t);
    }

    /** 组一张待立卡的表单（层级相关栏随后按用例补） */
    protected TAdrTermCard draft(String siteNo, String termCode, Integer level, String qty, String fine) {
        TAdrTermCard c = new TAdrTermCard();
        c.setSiteNo(siteNo);
        c.setTermCode(termCode);
        c.setTaxonLevel(level);
        c.setQty(new BigDecimal(qty));
        c.setFineAmt(new BigDecimal(fine));
        return c;
    }

    /** 直接落一张旧纸卡：步数/版次/校验串空着 */
    protected TAdrTermCard seedPaperCard(String siteNo, String termCode) {
        TAdrTermCard c = new TAdrTermCard();
        c.setId(nextId());
        c.setBillNo("PAPER-" + nextId());
        c.setSiteNo(siteNo);
        c.setTermCode(termCode);
        c.setFamilyNo(siteNo + "#" + termCode);
        c.setVersionFlag(0);
        c.setQty(new BigDecimal("50"));
        c.setFineAmt(new BigDecimal("5"));
        c.setDelFlag(0);
        // status/nodeNo/content 全空——模拟从纸质簿搬进的旧卡
        this.cardMapper.insert(c);
        return c;
    }
}
