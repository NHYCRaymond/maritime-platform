package com.maritime.iam.sdk.dataperm;

import com.maritime.iam.sdk.context.IamContext;
import com.maritime.iam.sdk.context.SystemContext;
import com.maritime.iam.sdk.model.PagePolicy;
import com.maritime.iam.sdk.model.PageSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MyBatis interceptor that injects data permission and five-line
 * role WHERE conditions from the page snapshot.
 *
 * <p>Two condition sources are merged (AND):
 * <ol>
 *   <li>Data permissions — from {@code pageSnapshot.dataPermissions}
 *       via {@link ExpressionParser}</li>
 *   <li>Five-line policies — from {@code pageSnapshot.pagePolicies}
 *       + {@code IamContext.lineRoles()} via
 *       {@link LineRoleFilter}</li>
 * </ol>
 *
 * <p>All values are parameterized via PreparedStatement
 * parameters. Field names are whitelist-validated by
 * {@link ExpressionParser}. No string concatenation of values.
 */
@Intercepts({@Signature(
        type = Executor.class,
        method = "query",
        args = {MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class})})
public class DataPermissionInjector implements Interceptor {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    DataPermissionInjector.class);

    private final ScopeColumns scopeColumns;

    /** Backward-compatible ctor — uses {@link ScopeColumns#DEFAULT}. */
    public DataPermissionInjector() {
        this(ScopeColumns.DEFAULT);
    }

    /**
     * @param scopeColumns column names for ORG / SELF / line_type
     *                     injection; auto-config wires this from
     *                     {@code iam.sdk.scope.*} properties.
     */
    public DataPermissionInjector(ScopeColumns scopeColumns) {
        this.scopeColumns = scopeColumns != null
                ? scopeColumns : ScopeColumns.DEFAULT;
    }

    @Override
    public Object intercept(Invocation invocation)
            throws Throwable {
        // Scheduled jobs / MQ consumers / outbox pollers mark
        // themselves via SystemContext — they have no IamContext
        // and would otherwise get 1=0 filtered to nothing.
        if (SystemContext.isActive()) {
            return invocation.proceed();
        }
        ExpressionParser.SqlFragment combined =
                buildCombinedFragment();
        if (combined.condition().isEmpty()) {
            return invocation.proceed();
        }
        modifySql(invocation.getArgs(), combined);
        return invocation.proceed();
    }

    /**
     * Merge data permission fragment and line role fragment.
     * Both present → AND. Either alone → just that one.
     */
    private ExpressionParser.SqlFragment buildCombinedFragment() {
        ExpressionParser.SqlFragment dpFrag =
                resolveDataPermFragment();
        ExpressionParser.SqlFragment lineFrag =
                resolveLineRoleFragment();

        boolean hasDp = !dpFrag.condition().isEmpty();
        boolean hasLine = !lineFrag.condition().isEmpty();

        if (!hasDp && !hasLine) {
            return new ExpressionParser.SqlFragment(
                    "", List.of());
        }
        if (hasDp && !hasLine) {
            return dpFrag;
        }
        if (!hasDp) {
            return lineFrag;
        }

        // Both present — AND them together
        String condition = dpFrag.condition()
                + " AND " + lineFrag.condition();
        List<Object> values = new ArrayList<>(dpFrag.values());
        values.addAll(lineFrag.values());
        return new ExpressionParser.SqlFragment(condition, values);
    }

    private ExpressionParser.SqlFragment resolveDataPermFragment() {
        List<DataPermissionExpression> expressions =
                resolveExpressions();
        if (expressions.isEmpty()) {
            return new ExpressionParser.SqlFragment(
                    "", List.of());
        }
        return ExpressionParser.toSqlFragment(expressions);
    }

    private ExpressionParser.SqlFragment resolveLineRoleFragment() {
        IamContext.ContextData ctx = IamContext.get();
        if (ctx == null || ctx.pageSnapshot() == null) {
            return new ExpressionParser.SqlFragment(
                    "", List.of());
        }
        List<PagePolicy> policies =
                ctx.pageSnapshot().pagePolicies();
        List<String> lineRoles = ctx.lineRoles();
        return LineRoleFilter.generateLineCondition(
                policies, lineRoles, scopeColumns);
    }

    private List<DataPermissionExpression> resolveExpressions() {
        IamContext.ContextData ctx = IamContext.get();
        if (ctx == null || ctx.pageSnapshot() == null) {
            return List.of();
        }
        PageSnapshot snapshot = ctx.pageSnapshot();
        return ExpressionParser.validate(
                snapshot.dataPermissions());
    }

    private void modifySql(
            Object[] args,
            ExpressionParser.SqlFragment fragment) {
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];
        BoundSql original = ms.getBoundSql(parameter);
        String wrapped = wrapSql(
                original.getSql(), fragment.condition());
        BoundSql modified = createModifiedBoundSql(
                ms, original, wrapped, parameter, fragment);
        args[0] = copyMappedStatement(ms, modified);
    }

    private String wrapSql(String sql, String condition) {
        return "SELECT * FROM (" + sql
                + ") __dp_t WHERE " + condition;
    }

    private BoundSql createModifiedBoundSql(
            MappedStatement ms,
            BoundSql original,
            String newSql,
            Object parameter,
            ExpressionParser.SqlFragment fragment) {
        List<ParameterMapping> mappings =
                buildMappings(ms, original, fragment);
        BoundSql modified = new BoundSql(
                ms.getConfiguration(), newSql,
                mappings, parameter);
        copyAdditionalParams(original, modified);
        setFragmentParams(modified, fragment);
        return modified;
    }

    private List<ParameterMapping> buildMappings(
            MappedStatement ms,
            BoundSql original,
            ExpressionParser.SqlFragment fragment) {
        List<ParameterMapping> mappings =
                new ArrayList<>(
                        original.getParameterMappings());
        for (int i = 0; i < fragment.values().size(); i++) {
            mappings.add(new ParameterMapping.Builder(
                    ms.getConfiguration(),
                    "__dp_" + i,
                    Object.class).build());
        }
        return mappings;
    }

    private void copyAdditionalParams(
            BoundSql source, BoundSql target) {
        for (ParameterMapping pm
                : source.getParameterMappings()) {
            String prop = pm.getProperty();
            if (source.hasAdditionalParameter(prop)) {
                target.setAdditionalParameter(
                        prop,
                        source.getAdditionalParameter(prop));
            }
        }
    }

    private void setFragmentParams(
            BoundSql boundSql,
            ExpressionParser.SqlFragment fragment) {
        List<Object> values = fragment.values();
        for (int i = 0; i < values.size(); i++) {
            boundSql.setAdditionalParameter(
                    "__dp_" + i, values.get(i));
        }
    }

    private MappedStatement copyMappedStatement(
            MappedStatement ms, BoundSql boundSql) {
        SqlSource sqlSource = p -> boundSql;
        return new MappedStatement.Builder(
                ms.getConfiguration(),
                ms.getId(),
                sqlSource,
                ms.getSqlCommandType())
                .resource(ms.getResource())
                .parameterMap(ms.getParameterMap())
                .resultMaps(ms.getResultMaps())
                .build();
    }
}
