package com.agentforge;

import com.agentforge.entity.handler.PgJsonbTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("PgJsonbTypeHandler 单元测试")
public class PgJsonbTypeHandlerTest {

    private final PgJsonbTypeHandler handler = new PgJsonbTypeHandler();

    @Test
    @DisplayName("测试将有效 JSON 字符串封装为 PGobject('jsonb') 并存入 PreparedStatement")
    void testSetNonNullParameterValidJson() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        String json = "{\"source\":\"doc.pdf\",\"page\":12}";

        handler.setNonNullParameter(ps, 1, json, JdbcType.OTHER);

        ArgumentCaptor<PGobject> captor = ArgumentCaptor.forClass(PGobject.class);
        verify(ps).setObject(eq(1), captor.capture());

        PGobject captured = captor.getValue();
        assertNotNull(captured);
        assertEquals("jsonb", captured.getType());
        assertEquals(json, captured.getValue());
    }

    @Test
    @DisplayName("测试将空字符串或空白字符串安全降级为 '{}' 存入 PGobject('jsonb')")
    void testSetNonNullParameterBlankJson() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);

        handler.setNonNullParameter(ps, 2, "   ", JdbcType.OTHER);

        ArgumentCaptor<PGobject> captor = ArgumentCaptor.forClass(PGobject.class);
        verify(ps).setObject(eq(2), captor.capture());

        PGobject captured = captor.getValue();
        assertNotNull(captured);
        assertEquals("jsonb", captured.getType());
        assertEquals("{}", captured.getValue());
    }

    @Test
    @DisplayName("测试从 ResultSet 和 CallableStatement 取出 JSONB 字符串")
    void testGetNullableResult() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("metadata")).thenReturn("{\"status\":\"ok\"}");
        when(rs.getString(1)).thenReturn("{\"status\":\"ok\"}");

        assertEquals("{\"status\":\"ok\"}", handler.getNullableResult(rs, "metadata"));
        assertEquals("{\"status\":\"ok\"}", handler.getNullableResult(rs, 1));

        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getString(1)).thenReturn("{\"status\":\"ok\"}");
        assertEquals("{\"status\":\"ok\"}", handler.getNullableResult(cs, 1));
    }
}
