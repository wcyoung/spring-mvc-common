package wcyoung.spring.mvc.mybatis.plugin;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

@Intercepts({
    @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class MybatisSqlLogInterceptor implements Interceptor {

    private Logger log = LoggerFactory.getLogger(getClass());

    private final String LOG_FORMAT=
            "\n======================================= MapperId =========================================\n"
            + "{}\n"
            + "========================================== SQL ===========================================\n"
            + "    {}";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        try {
            if (log.isDebugEnabled()) {
                Object[] args = invocation.getArgs();
                MappedStatement mappedStatement = (MappedStatement) args[0];
                Object parameterObject = (Object) args[1];
                BoundSql boundSql = mappedStatement.getBoundSql(parameterObject);

                log.debug(LOG_FORMAT, mappedStatement.getId(), getParameterBindingSql(boundSql, parameterObject));
            }
        } catch (Exception e) {
            log.error("Exception: {}", ExceptionUtils.getStackTrace(e));
        }

        return invocation.proceed();
    }

    private String getParameterBindingSql(BoundSql boundSql, Object parameterObject) throws Exception {
        StringBuilder sqlStringBuilder = new StringBuilder(boundSql.getSql());

        BiConsumer<StringBuilder, Object> sqlObjectReplace = (sql, parameter) -> {
            int questionIndex = sql.indexOf("?");

            if (questionIndex == -1) {
                return;
            }

            sql.replace(questionIndex, questionIndex + 1, (parameter != null) ? "'" + parameter.toString() + "'" : "NULL");
        };

        if (parameterObject == null) {
            sqlObjectReplace.accept(sqlStringBuilder, parameterObject);
        } else {
            if (parameterObject instanceof Integer || parameterObject instanceof Long
                    || parameterObject instanceof Float || parameterObject instanceof Double
                    || parameterObject instanceof String) {
                sqlObjectReplace.accept(sqlStringBuilder, parameterObject);
            } else if (parameterObject instanceof Map) {
                Map<?, ?> parameterObjectMap = (Map<?, ?>) parameterObject;
                List<ParameterMapping> paramMappings = boundSql.getParameterMappings();

                for (ParameterMapping parameterMapping : paramMappings) {
                    String propertyKey = parameterMapping.getProperty();

                    Object paramValue = null;
                    if (boundSql.hasAdditionalParameter(propertyKey)) {
                        paramValue = boundSql.getAdditionalParameter(propertyKey);
                    } else {
                        paramValue = parameterObjectMap.get(propertyKey);
                    }

                    sqlObjectReplace.accept(sqlStringBuilder, paramValue);
                }
            } else {
                List<ParameterMapping> paramMappings = boundSql.getParameterMappings();
                Class<?> paramClass = parameterObject.getClass();

                for (ParameterMapping parameterMapping : paramMappings) {
                    String propertyKey = parameterMapping.getProperty();

                    Object paramValue = null;
                    if (boundSql.hasAdditionalParameter(propertyKey)) {
                        paramValue = boundSql.getAdditionalParameter(propertyKey);
                    } else {
                        Field field = ReflectionUtils.findField(paramClass, propertyKey);
                        field.setAccessible(true);
                        paramValue = field.get(parameterObject);
                    }

                    sqlObjectReplace.accept(sqlStringBuilder, paramValue);
                }
            }
        }

        return sqlStringBuilder.toString();
    }

}
