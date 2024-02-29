package org.logdoc.fairhttp.modules;

import com.typesafe.config.Config;
import com.zaxxer.hikari.HikariConfig;
import org.apache.ibatis.session.Configuration;
import org.logdoc.fairhttp.service.DI;
import org.logdoc.fairhttp.service.api.helpers.Preloaded;
import org.logdoc.fairhttp.service.tools.MapperProvider;

import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | denis@danilin.name
 * 12.09.2017 14:09
 * core-router ☭ sweat and blood
 */
public abstract class ABatisModule implements Preloaded {
    @Override
    public final void configure(final Config rootConfig) {
        if (!rootConfig.hasPath("db")) return;

        final Config dbConfig = rootConfig.getConfig("db");

        if (dbConfig.hasPath("url"))
            configNamed(null, dbConfig);
        else
            dbConfig.root().forEach((key, value) -> configNamed(key.equals("default") ? null : key, dbConfig.getConfig(key)));

        init();
    }

    private void configNamed(final String name, final Config dbConfig) {
        final HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setJdbcUrl(dbConfig.getString("url"));
        hikariConfig.setDriverClassName(dbConfig.getString("driver"));
        hikariConfig.setUsername(dbConfig.getString("username"));
        hikariConfig.setPassword(dbConfig.getString("password"));

        final Config config = dbConfig.hasPath("hikaricp") ? dbConfig.getConfig("hikaricp") : null;

        if (config != null) {
            if (config.hasPath("dataSourceCLassName"))
                hikariConfig.setDataSourceClassName(config.getString("dataSourceClassName"));

            final Config dataSourceConfig = config.getConfig("dataSource");
            dataSourceConfig.root().keySet().forEach(key -> hikariConfig.addDataSourceProperty(key, dataSourceConfig.getAnyRef(key)));

            hikariConfig.setAutoCommit(config.getBoolean("autoCommit"));
            hikariConfig.setConnectionTimeout(config.getLong("connectionTimeout") * 1000L);
            hikariConfig.setIdleTimeout(config.getLong("idleTimeout") * 1000L);
            hikariConfig.setMaxLifetime(config.getLong("maxLifetime") * 1000L);
            if (config.hasPath("connectionTestQuery"))
                hikariConfig.setConnectionTestQuery(config.getString("connectionTestQuery"));

            if (config.hasPath("minimumIdle"))
                hikariConfig.setMinimumIdle(config.getInt("minimumIdle"));
            hikariConfig.setMaximumPoolSize(config.getInt("maximumPoolSize"));
            if (config.hasPath("poolName"))
                hikariConfig.setPoolName(config.getString("poolName"));

            hikariConfig.setInitializationFailTimeout(config.getLong("initializationFailTimeout"));
            hikariConfig.setIsolateInternalQueries(config.getBoolean("isolateInternalQueries"));
            hikariConfig.setAllowPoolSuspension(config.getBoolean("allowPoolSuspension"));
            hikariConfig.setReadOnly(config.getBoolean("readOnly"));
            hikariConfig.setRegisterMbeans(config.getBoolean("registerMbeans"));
            if (config.hasPath("connectionInitSql"))
                hikariConfig.setConnectionInitSql(config.getString("connectionInitSql"));
            if (config.hasPath("catalog"))
                hikariConfig.setCatalog(config.getString("catalog"));
            if (config.hasPath("transactionIsolation"))
                hikariConfig.setTransactionIsolation(config.getString("transactionIsolation"));
            hikariConfig.setValidationTimeout(config.getLong("validationTimeout") * 1000L);
            hikariConfig.setLeakDetectionThreshold(config.getLong("leakDetectionThreshold") * 1000L);

            hikariConfig.validate();
        }

        DI.hikariDataSource(name, hikariConfig);
    }

    public abstract void init();

    protected final void addTypeHandler(final Class<?> clas) {
        addTypeHandler(null, clas);
    }

    protected final void addTypeHandler(final String named, final Class<?> clas) {
        if (notNull(named).equals("default")) {
            addTypeHandler(null, clas);
            return;
        }

        DI.gain(named, Configuration.class).getTypeHandlerRegistry().register(clas);
    }

    protected final void addAlias(final String alias, final Class<?> cls) {
        addAlias(null, alias, cls);
    }

    protected final void addAlias(final String alias, final String cls) {
        addAlias(null, alias, cls);
    }

    protected final void addSimpleAlias(final Class<?> cls) {
        addSimpleAlias(null, cls);
    }

    protected final void addAlias(final String named, final String alias, final Class<?> cls) {
        if (notNull(named).equals("default")) {
            addAlias(null, alias, cls);
            return;
        }

        DI.gain(named, Configuration.class).getTypeAliasRegistry().registerAlias(alias, cls);
    }

    protected final void addSimpleAlias(final String named, final Class<?> cls) {
        if (notNull(named).equals("default")) {
            addSimpleAlias(null, cls);
            return;
        }

        DI.gain(named, Configuration.class).getTypeAliasRegistry().registerAlias(cls);
    }

    protected final void addAlias(final String named, final String alias, final String cls) {
        if (notNull(named).equals("default")) {
            addAlias(null, alias, cls);
            return;
        }

        DI.gain(named, Configuration.class).getTypeAliasRegistry().registerAlias(alias, cls);
    }

    protected final <A> void addMapper(final Class<A> clas) {
        addMapper(null, clas);
    }

    protected final <A> void addMapper(final String named, final Class<A> clas) {
        if (notNull(named).equals("default")) {
            addMapper(null, clas);
            return;
        }

        DI.gain(named, Configuration.class).addMapper(clas);
        DI.bindProvider(named, clas, new MapperProvider<>(named, clas));
    }
}
