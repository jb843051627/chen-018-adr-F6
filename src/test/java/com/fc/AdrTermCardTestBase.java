package com.fc;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.TAdrOrgArchMapper;
import com.fc.v2.mapper.auto.TAdrTermCardMapper;
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
 * 术语分类卡领域服务集成测试基台：H2(MODE=MySQL) + MyBatis-Plus，不起 Spring、不连 MySQL/Redis。
 */
public abstract class AdrTermCardTestBase {

    protected TAdrTermCardServiceImpl service;
    protected TAdrTermCardMapper cardMapper;
    protected TAdrOrgArchMapper orgMapper;

    private final AtomicLong idSeq = new AtomicLong(1000);

    /** 在册那家，三界线 20/80/300（与省里种子底册同一把尺） */
    protected static final String SITE_A = "YF00";
    /** 另一家在册单位 */
    protected static final String SITE_B = "YF09";
    /** 年内退出的那家 */
    protected static final String SITE_EXIT = "YF01";

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
        factory.getConfiguration().addMapper(TAdrOrgArchMapper.class);

        SqlSession session = factory.openSession(true);
        this.cardMapper = session.getMapper(TAdrTermCardMapper.class);
        this.orgMapper = session.getMapper(TAdrOrgArchMapper.class);

        this.service = new TAdrTermCardServiceImpl();
        inject(this.service, "baseMapper", this.cardMapper);
        inject(this.service, "adrOrgArchMapper", this.orgMapper);

        seedOrg(SITE_A, 0);
        seedOrg(SITE_B, 0);
        seedOrg(SITE_EXIT, 1);
    }

    /** 落一家底册：三界线 20/80/300，status 0在册 1已退出 */
    protected void seedOrg(String siteNo, int status) {
        seedOrg(siteNo, status, "20", "80", "300");
    }

    /** 落一家底册：界线自定（换底册重折监督层级用） */
    protected void seedOrg(String siteNo, int status, String th1, String th2, String th3) {
        com.fc.v2.model.auto.TAdrOrgArch org = new com.fc.v2.model.auto.TAdrOrgArch();
        org.setId(idSeq.incrementAndGet());
        org.setSiteNo(siteNo);
        org.setSiteName(siteNo + "号报告单位底册");
        org.setSiteType("医疗机构");
        org.setRoadName("澜川省—澜川市—澜川县");
        org.setTh1Max(new BigDecimal(th1));
        org.setTh2Max(new BigDecimal(th2));
        org.setTh3Max(new BigDecimal(th3));
        org.setStatus(status);
        org.setDelFlag(0);
        this.orgMapper.insert(org);
    }

    /** 一张齐整的立卡表单（系统器官分类层级，份数自定） */
    protected com.fc.v2.model.custom.adr.TermCardOpenForm form(String siteNo, String qty) {
        com.fc.v2.model.custom.adr.TermCardOpenForm f = new com.fc.v2.model.custom.adr.TermCardOpenForm();
        f.setSiteNo(siteNo);
        f.setTermName("消化系统损害");
        f.setSymptom("恶心、呕吐");
        f.setTermLevel(1);
        f.setParentCode("SOC-GI");
        f.setBasisClause("第十二条");
        f.setQty(new BigDecimal(qty));
        f.setFineAmt(new BigDecimal("15"));
        return f;
    }

    /** 直插一条卡（绕开服务，模拟存量册/旧纸卡里各式各样的账） */
    protected com.fc.v2.model.auto.TAdrTermCard rawCard(java.util.function.Consumer<com.fc.v2.model.auto.TAdrTermCard> mut) {
        com.fc.v2.model.auto.TAdrTermCard c = new com.fc.v2.model.auto.TAdrTermCard();
        c.setId(idSeq.incrementAndGet());
        c.setBillNo("RAW" + idSeq.get());
        c.setTermCode("TE-RAW-" + idSeq.get());
        c.setNodeNo(1);
        c.setSiteNo(SITE_A);
        c.setSiteId(1001L);
        c.setTermName("存量损害");
        c.setTermLevel(1);
        c.setParentCode("SOC-OLD");
        c.setQty(new BigDecimal("50"));
        c.setFineAmt(new BigDecimal("10"));
        c.setCurrentFlag(1);
        c.setStatus(0);
        c.setDelFlag(0);
        if (mut != null) {
            mut.accept(c);
        }
        this.cardMapper.insert(c);
        return c;
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException ignore) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
