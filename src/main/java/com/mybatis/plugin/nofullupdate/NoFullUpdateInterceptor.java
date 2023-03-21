package com.mybatis.plugin.nofullupdate;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;


@Intercepts(
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
)
public class NoFullUpdateInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MetaObject metaObject = SystemMetaObject.forObject(invocation.getTarget());
        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        BoundSql boundSql = mappedStatement.getBoundSql(metaObject.getValue("delegate.parameterHandler.parameterObject"));

        //Get the real executed SQL
        String sql = getRealSql(boundSql, mappedStatement);

        Statements statements = CCJSqlParserUtil.parseStatements(sql);
        for (Statement statement : statements.getStatements()) {

            // the statement is an update statement
            if (statement instanceof Update) {
                Update update = (Update) statement;

                //Whether to update the full table
                if (isFullUpdate(update.getWhere())) {
                    throw new NoFullUpdatePluginException("Updating the entire table data is not allowed in update operations.");
                }
            }
        }

        return invocation.proceed();
    }

    /**
     * Get the real executed SQL
     *
     * @param boundSql
     * @param mappedStatement
     * @return
     */
    private String getRealSql(BoundSql boundSql, MappedStatement mappedStatement) {
        String sql = boundSql.getSql();

        if (StringUtils.isBlank(sql)) {
            throw new NoFullUpdatePluginException("The SQL statement cannot be empty.");
        }

        //Whether the SQL includes placeholders or not
        if (!StringUtils.contains(sql, "?")) {
            return sql;
        }

        sql = sql.replaceAll("[\\s\n ]+", " ");

        Configuration configuration = mappedStatement.getConfiguration();
        Object parameterObject = boundSql.getParameterObject();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();

        List<String> parameters = new ArrayList<>();

        //Gets the parameters of the placeholder
        if (parameterMappings != null) {
            MetaObject metaObject = parameterObject == null ? null : configuration.newMetaObject(parameterObject);
            for (ParameterMapping parameterMapping : parameterMappings) {
                if (parameterMapping.getMode() != ParameterMode.OUT) {
                    Object value;
                    String propertyName = parameterMapping.getProperty();
                    if (boundSql.hasAdditionalParameter(propertyName)) {
                        value = boundSql.getAdditionalParameter(propertyName);
                    } else if (parameterObject == null) {
                        value = null;
                    } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                        value = parameterObject;
                    } else {
                        value = metaObject == null ? null : metaObject.getValue(propertyName);
                    }
                    if (value instanceof Number) {
                        parameters.add(String.valueOf(value));
                    } else {
                        StringBuilder builder = new StringBuilder();
                        builder.append("'");
                        if (value instanceof Date) {
                            builder.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((Date) value));
                        } else if (value instanceof String) {
                            builder.append(value);
                        }
                        builder.append("'");
                        parameters.add(builder.toString());
                    }
                }
            }
        }

        //replace the placeholder
        for (String value : parameters) {
            sql = sql.replaceFirst("\\?", value);
        }

        return sql;
    }

    /**
     * Whether to update the full table
     *
     * @param where
     * @return
     */
    private boolean isFullUpdate(Expression where) {
        if (where == null) {
            return true;
        }

        if (where instanceof EqualsTo) {
            // example: 1=1
            EqualsTo equalsTo = (EqualsTo) where;
            return StringUtils.equals(equalsTo.getLeftExpression().toString(), equalsTo.getRightExpression().toString());
        } else if (where instanceof NotEqualsTo) {
            // example: 1 != 2
            NotEqualsTo notEqualsTo = (NotEqualsTo) where;
            return !StringUtils.equals(notEqualsTo.getLeftExpression().toString(), notEqualsTo.getRightExpression().toString());
        } else if (where instanceof OrExpression) {
            // example: 1 = 1 or 1 !=2
            OrExpression orExpression = (OrExpression) where;
            return isFullUpdate(orExpression.getLeftExpression()) || isFullUpdate(orExpression.getRightExpression());
        } else if (where instanceof AndExpression) {
            // example: 1 = 1 and 1 !=2
            AndExpression andExpression = (AndExpression) where;
            return isFullUpdate(andExpression.getLeftExpression()) && isFullUpdate(andExpression.getRightExpression());
        } else if (where instanceof Parenthesis) {
            // example: (1 = 1)
            Parenthesis parenthesis = (Parenthesis) where;
            return isFullUpdate(parenthesis.getExpression());
        }

        return false;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // do nothing
    }

}