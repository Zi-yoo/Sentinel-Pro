package com.alibaba.csp.sentinel.dashboard.util;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BoundParameterQuery;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class InfluxDBUtils {
    private static Logger logger = LoggerFactory.getLogger(InfluxDBUtils.class);
    private static InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
    private static String url;
    private static String username;
    private static String password;
    @Value("${influx.url}")
    public void setUrl(String urli) {
        url = urli;
    }
    @Value("${influx.user}")
    public void setUser(String userr) {
        username = userr;
    }
    @Value("${influx.password}")
    public void setPwd(String pwdd) {
        password = pwdd;
    }
    public static <T> T process(String database, InfluxDBCallback callback) {
        InfluxDB influxDB = null;
        T t = null;
        try {
            influxDB = InfluxDBFactory.connect(url,username,password);
            influxDB.setDatabase(database);
            t = callback.doCallBack(database, influxDB);
        } catch (Exception e) {
            logger.error("[process exception]", e);
        } finally {
            if (influxDB != null) {
                try {
                    influxDB.close();
                } catch (Exception e) {
                    logger.error("[influxDB.close exception]", e);
                }
            }
        }
        return t;
    }
    public static void insert(String database, InfluxDBInsertCallback influxDBInsertCallback) {
        process(database, new InfluxDBCallback() {
            @Override
            public <T> T doCallBack(String database, InfluxDB influxDB) {
                influxDBInsertCallback.doCallBack(database, influxDB);
                return null;
            }
        });
    }
    public static QueryResult query(String database, InfluxDBQueryCallback influxDBQueryCallback) {
        return process(database, new InfluxDBCallback() {
            @Override
            public <T> T doCallBack(String database, InfluxDB influxDB) {
                QueryResult queryResult = influxDBQueryCallback.doCallBack(database, influxDB);
                return (T) queryResult;
            }
        });
    }
    public static <T> List<T> queryList(String database, String sql, Map<String, Object> paramMap, Class<T> clasz) {
        QueryResult queryResult = query(database, new InfluxDBQueryCallback() {
            @Override
            public QueryResult doCallBack(String database, InfluxDB influxDB) {
                BoundParameterQuery.QueryBuilder queryBuilder = BoundParameterQuery.QueryBuilder.newQuery(sql);
                queryBuilder.forDatabase(database);
                if (paramMap != null && paramMap.size() > 0) {
                    Set<Map.Entry<String, Object>> entries = paramMap.entrySet();
                    for (Map.Entry<String, Object> entry : entries) {
                        queryBuilder.bind(entry.getKey(), entry.getValue());
                    }
                }
                return influxDB.query(queryBuilder.create());
            }
        });
        return resultMapper.toPOJO(queryResult, clasz);
    }
    public interface InfluxDBCallback {
        <T> T doCallBack(String database, InfluxDB influxDB);
    }
    public interface InfluxDBInsertCallback {
        void doCallBack(String database, InfluxDB influxDB);
    }
    public interface InfluxDBQueryCallback {
        QueryResult doCallBack(String database, InfluxDB influxDB);
    }
}
