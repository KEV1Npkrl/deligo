package pe.deligo.shared;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.*;

@Repository
public class Store {
    public final JdbcTemplate jdbc;
    public Store(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Map<String,Object> one(String sql, Object... args) { return jdbc.queryForMap(sql, args); }
    public List<Map<String,Object>> list(String sql, Object... args) { return jdbc.queryForList(sql, args); }
    public int update(String sql, Object... args) { return jdbc.update(sql, args); }
    public static String id() { return UUID.randomUUID().toString(); }
    public static String str(Map<String,Object> row, String key) { return Objects.toString(row.get(key), ""); }
    public static BigDecimal money(Map<String,Object> row, String key) { return new BigDecimal(row.get(key).toString()); }
    public static boolean yes(Map<String,Object> row, String key) { return Boolean.TRUE.equals(row.get(key)) || "1".equals(str(row,key)); }
}
