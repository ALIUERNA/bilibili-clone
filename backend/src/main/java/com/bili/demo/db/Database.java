package com.bili.demo.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MySQL 连接与迁移管理。
 *
 * 设计要点：
 * 1. 数据库不可用时**不会**让应用启动失败（Hikari 的 initialization-fail-timeout = -1），
 *    此时 {@link #available()} 返回 false，上层会自动退回内存模式，保证「下载下来就能跑」。
 * 2. 迁移脚本放在 classpath:db/migration/V*.sql，按版本号顺序执行，
 *    执行记录写进 schema_migrations，脚本只会跑一次。
 * 3. 迁移脚本只做「新增表 / 新增列 / 新增索引」，不删表不删列，兼容已有数据库。
 *
 * <h3>性能约定：请求线程绝不等待数据库重连</h3>
 * 数据库挂掉时，底层连接池要等满 {@code connection-timeout}（默认 5 秒）才会抛错。
 * 如果每次查询都去试探一下，一个要查两次库的接口就会卡 10 秒——
 * 用户看到的就是「首页空白十秒」，非常致命。
 * <p>
 * 所以这里把「探测」和「使用」彻底分开：
 * <ul>
 *   <li>{@link #available()} 只读内存里的状态位，<b>永不阻塞</b>，必要时丢一个后台探测任务；</li>
 *   <li>探测跑在独立的单线程调度器里，失败有 15 秒节流，不会把连接池占满；</li>
 *   <li>给仓储层用的是 {@link FailFastDataSource}：数据库被判定为不可用时<b>立刻抛异常</b>，
 *       让上层那些 {@code try/catch} 兜底逻辑瞬间生效，而不是干等 5 秒。</li>
 * </ul>
 * 效果：MySQL 没启动时，接口响应从 10s 降到 1ms 级，同时保留「MySQL 恢复后自动切回」的能力。
 */
@Component
public class Database {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    /** 判定不可用后，多久重新探测一次 */
    private static final long RETRY_INTERVAL_MS = 15_000L;

    /** 原始数据源：只有「探测」和「迁移」才允许直接碰它 */
    private final DataSource rawDataSource;

    /** 给仓储层用的数据源：不可用时立刻抛错，不阻塞请求线程 */
    private final JdbcTemplate jdbc;

    /** 后台探测线程（守护线程，不阻止 JVM 退出） */
    private final ScheduledExecutorService prober = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "bili-db-prober");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean dbAvailable = false;
    private volatile long lastProbeAt = 0L;
    private volatile String lastError = "";
    private volatile boolean schemaReady = false;

    private final AtomicBoolean probing = new AtomicBoolean(false);

    public Database(DataSource dataSource) {
        this.rawDataSource = dataSource;
        this.jdbc = new JdbcTemplate(new FailFastDataSource(dataSource));
        this.jdbc.setQueryTimeout(8);
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    /** 数据库当前是否可用。只读缓存状态，永不阻塞。 */
    public boolean available() {
        if (dbAvailable) {
            return true;
        }
        triggerProbe();
        return false;
    }

    /**
     * 启动时同步探测一次。
     * 启动阶段阻塞几秒是可以接受的（换来的是「数据库在就直接进 MySQL 模式」的确定性），
     * 但请求路径上绝不允许这么做。
     */
    public boolean probeNow() {
        return probe();
    }

    /** 丢一个后台探测任务（15 秒节流 + 单飞，避免把连接池打满） */
    private void triggerProbe() {
        long now = System.currentTimeMillis();
        if (now - lastProbeAt < RETRY_INTERVAL_MS) {
            return;
        }
        if (!probing.compareAndSet(false, true)) {
            return;
        }
        lastProbeAt = now;
        try {
            prober.execute(() -> {
                try {
                    probe();
                } catch (Exception e) {
                    log.debug("探测 MySQL 时出错：{}", shortMsg(e));
                } finally {
                    probing.set(false);
                }
            });
        } catch (Exception e) {
            // 线程池已关闭（应用正在退出），忽略即可
            probing.set(false);
        }
    }

    /** 真去连一次数据库。可能阻塞一个 connection-timeout，只允许在后台线程/启动阶段调用。 */
    private boolean probe() {
        try (Connection conn = rawDataSource.getConnection()) {
            boolean ok = conn.isValid(3);
            if (ok && !dbAvailable) {
                log.info(">>> MySQL 连接成功：{}", conn.getMetaData().getURL());
            }
            dbAvailable = ok;
            return ok;
        } catch (Exception e) {
            if (dbAvailable) {
                log.warn(">>> MySQL 连接已断开，暂时切回内存模式：{}", shortMsg(e));
            }
            dbAvailable = false;
            lastError = shortMsg(e);
            return false;
        }
    }

    /**
     * 请求过程中发现连不上数据库时由 {@link FailFastDataSource} 回调。
     * 立刻把状态位打成「不可用」，这样后续请求直接快速失败，不用每个都去等超时。
     */
    void markDown(String reason) {
        if (dbAvailable) {
            log.warn(">>> MySQL 连接异常，切换内存模式兜底：{}", reason);
        }
        dbAvailable = false;
        lastError = reason;
        // 允许尽快再试一次，便于数据库恢复后马上回来
        lastProbeAt = 0L;
    }

    public String lastError() {
        return lastError;
    }

    /** 执行迁移脚本，返回是否成功 */
    public synchronized boolean ensureSchema() {
        if (schemaReady) {
            return true;
        }
        if (!available()) {
            return false;
        }
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                      version varchar(80) NOT NULL,
                      applied_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      note varchar(200) DEFAULT NULL,
                      PRIMARY KEY (version)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='迁移执行记录'
                    """);
            List<String> applied = jdbc.queryForList("SELECT version FROM schema_migrations", String.class);
            List<Resource> scripts = findScripts();
            for (Resource resource : scripts) {
                String version = resource.getFilename() == null ? resource.getDescription() : resource.getFilename();
                String prefix = version.split("__")[0];
                if (applied.contains(prefix)) {
                    continue;
                }
                String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                runScript(sql);
                jdbc.update("INSERT INTO schema_migrations(version, note) VALUES (?, ?)", prefix, version);
                log.info(">>> 已执行数据库迁移：{}", version);
            }
            schemaReady = true;
            return true;
        } catch (Exception e) {
            log.error(">>> 数据库迁移失败：{}", shortMsg(e));
            return false;
        }
    }

    private List<Resource> findScripts() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:db/migration/V*.sql");
        List<Resource> list = new ArrayList<>(List.of(resources));
        list.sort(Comparator.comparing(r -> String.valueOf(r.getFilename())));
        return list;
    }

    /** 极简 SQL 脚本执行器：去掉 -- 注释后按分号切分 */
    private void runScript(String sql) {
        StringBuilder sb = new StringBuilder();
        for (String rawLine : sql.split("\\R")) {
            String line = rawLine;
            int idx = line.indexOf("--");
            if (idx >= 0) {
                line = line.substring(0, idx);
            }
            if (line.isBlank()) {
                continue;
            }
            sb.append(line).append('\n');
        }
        for (String statement : sb.toString().split(";")) {
            String s = statement.trim();
            if (s.isEmpty()) {
                continue;
            }
            try (Connection conn = rawDataSource.getConnection(); Statement st = conn.createStatement()) {
                st.execute(s);
            } catch (Exception e) {
                String msg = String.valueOf(e.getMessage());
                // 幂等兜底：重复的列/索引/表直接忽略，不影响其它语句
                if (msg.contains("Duplicate column") || msg.contains("Duplicate key name")
                        || msg.contains("already exists") || msg.contains("Duplicate foreign key")) {
                    log.warn(">>> 跳过已存在的结构：{}", msg);
                    continue;
                }
                throw new IllegalStateException("执行失败: " + msg + "\nSQL: " + s.substring(0, Math.min(200, s.length())), e);
            }
        }
    }

    /** 统计一张表的数据量，表不存在时返回 -1 */
    public long count(String table) {
        try {
            Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return -1;
        }
    }

    public boolean tableExists(String table) {
        try (Connection conn = rawDataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(conn.getCatalog(), null, table, new String[]{"TABLE"})) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    /** 应用退出时收掉探测线程 */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        prober.shutdownNow();
    }

    private String shortMsg(Exception e) {
        String m = e.getMessage();
        if (m == null) {
            return e.getClass().getSimpleName();
        }
        return m.length() > 200 ? m.substring(0, 200) + "..." : m;
    }

    /**
     * 包一层「快速失败」的数据源给仓储层用。
     * <p>
     * 数据库已被判定为不可用时直接抛 {@link SQLException}，
     * 让仓储层里的 {@code catch (Exception e) { return empty; }} 立即生效——
     * 这正是内存兜底模式想要的行为，而且耗时几乎为零。
     */
    private final class FailFastDataSource implements DataSource {

        private final DataSource delegate;

        FailFastDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return guard(() -> delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return guard(() -> delegate.getConnection(username, password));
        }

        private Connection guard(SqlSupplier supplier) throws SQLException {
            if (!dbAvailable) {
                throw new SQLException("MySQL 当前不可用，已启用内存模式兜底");
            }
            try {
                return supplier.get();
            } catch (SQLException e) {
                // 连不上说明数据库出问题了，立刻标记，避免后面每个请求都等满超时
                markDown(shortMsg(e));
                throw e;
            }
        }

        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getLogger("bili.db");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }

    /** 允许抛受检异常的 Supplier，省掉一堆样板代码 */
    @FunctionalInterface
    private interface SqlSupplier {
        Connection get() throws SQLException;
    }
}
