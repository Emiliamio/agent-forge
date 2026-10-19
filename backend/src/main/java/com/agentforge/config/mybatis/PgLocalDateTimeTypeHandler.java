package com.agentforge.config.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * MyBatis TypeHandler for PostgreSQL TIMESTAMPTZ / TIMESTAMP to java.time.LocalDateTime.
 * Safely handles OffsetDateTime returned by pgjdbc for TIMESTAMPTZ columns, converting to LocalDateTime.
 */
@MappedTypes(LocalDateTime.class)
public class PgLocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType) throws SQLException {
        ps.setTimestamp(i, Timestamp.valueOf(parameter));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return convert(rs.getObject(columnName));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return convert(rs.getObject(columnIndex));
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return convert(cs.getObject(columnIndex));
    }

    private LocalDateTime convert(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (obj instanceof OffsetDateTime odt) {
            return odt.toLocalDateTime();
        }
        if (obj instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }
}
