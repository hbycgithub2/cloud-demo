# 保险车险系统 SQL 优化经典案例

> **说明**：本文档整理了保险行业（特别是车险系统）常见的 SQL 优化案例，基于行业通用场景和最佳实践。

## 目录

1. [保单查询优化 - 多条件组合查询](#案例-1保单查询优化-多条件组合查询)
2. [理赔查询优化 - 历史数据归档](#案例-2理赔查询优化-历史数据归档)
3. [保费试算优化 - 高并发计算](#案例-3保费试算优化-高并发计算)
4. [续保提醒优化 - 大批量数据处理](#案例-4续保提醒优化-大批量数据处理)
5. [出险记录查询 - 多表关联优化](#案例-5出险记录查询-多表关联优化)
6. [保单统计报表 - 复杂聚合查询](#案例-6保单统计报表-复杂聚合查询)

---

## 案例 1：保单查询优化 - 多条件组合查询

### 背景

**场景**：客服/业务员查询保单信息

**典型问题**：
- 查询条件多（车牌号、身份证、手机号、保单号等）
- 查询慢（3-5 秒）
- 保单表数据量大（亿级）
- 用户体验差

**业务特点**：
- 保单数据量：1-2 亿条
- 查询频率：每秒 1000+ 次
- 查询条件：10+ 个可选条件
- 历史保单和有效保单混在一起

---

### 问题 SQL

```sql
-- 原始查询（慢）
SELECT * FROM policy 
WHERE 1=1
  AND (license_plate = '京A12345' OR license_plate IS NULL)
  AND (id_card = '110101199001011234' OR id_card IS NULL)
  AND (mobile = '13800138000' OR mobile IS NULL)
  AND (policy_no = 'PICC2024001' OR policy_no IS NULL)
  AND status IN ('valid', 'pending')
  AND create_time >= '2024-01-01'
ORDER BY create_time DESC
LIMIT 20;

-- 问题：
-- 1. OR 条件导致索引失效
-- 2. 多个可选条件，无法确定使用哪个索引
-- 3. 全表扫描
-- 4. 执行时间：3-5 秒
```

---

### 问题分析

```sql
-- 分析执行计划
EXPLAIN SELECT * FROM policy 
WHERE (license_plate = '京A12345' OR license_plate IS NULL)
  AND (id_card = '110101199001011234' OR id_card IS NULL)
  AND status IN ('valid', 'pending');

-- 结果：
type: ALL（全表扫描）
key: NULL（没有使用索引）
rows: 100000000（扫描 1 亿行）
Extra: Using where

-- 问题根源：
-- 1. OR 条件中包含 IS NULL，导致索引失效
-- 2. 多个 OR 条件，MySQL 无法选择合适的索引
-- 3. 需要扫描全表
```

---

### 解决方案

#### 方案 1：动态 SQL（推荐）

```java
// 根据实际传入的参数动态构建 SQL
public List<Policy> queryPolicy(PolicyQueryDTO query) {
    StringBuilder sql = new StringBuilder("SELECT * FROM policy WHERE 1=1");
    List<Object> params = new ArrayList<>();
    
    // 只添加有值的条件
    if (StringUtils.isNotBlank(query.getLicensePlate())) {
        sql.append(" AND license_plate = ?");
        params.add(query.getLicensePlate());
    }
    
    if (StringUtils.isNotBlank(query.getIdCard())) {
        sql.append(" AND id_card = ?");
        params.add(query.getIdCard());
    }
    
    if (StringUtils.isNotBlank(query.getMobile())) {
        sql.append(" AND mobile = ?");
        params.add(query.getMobile());
    }
    
    if (StringUtils.isNotBlank(query.getPolicyNo())) {
        sql.append(" AND policy_no = ?");
        params.add(query.getPolicyNo());
    }
    
    sql.append(" AND status IN ('valid', 'pending')");
    sql.append(" ORDER BY create_time DESC LIMIT 20");
    
    return jdbcTemplate.query(sql.toString(), params.toArray());
}

// 优点：
// ✅ 只使用有值的条件
// ✅ 可以使用索引
// ✅ 查询速度快
```

---

#### 方案 2：建立多个索引

```sql
-- 根据常用查询条件建立索引

-- 索引 1：车牌号 + 状态 + 创建时间
CREATE INDEX idx_license_status_time 
ON policy(license_plate, status, create_time);

-- 索引 2：身份证 + 状态 + 创建时间
CREATE INDEX idx_idcard_status_time 
ON policy(id_card, status, create_time);

-- 索引 3：手机号 + 状态 + 创建时间
CREATE INDEX idx_mobile_status_time 
ON policy(mobile, status, create_time);

-- 索引 4：保单号（唯一索引）
CREATE UNIQUE INDEX idx_policy_no 
ON policy(policy_no);

-- 优化后的查询（根据不同条件使用不同索引）
-- 按车牌号查询
SELECT * FROM policy 
WHERE license_plate = '京A12345'
  AND status IN ('valid', 'pending')
ORDER BY create_time DESC
LIMIT 20;

-- 按身份证查询
SELECT * FROM policy 
WHERE id_card = '110101199001011234'
  AND status IN ('valid', 'pending')
ORDER BY create_time DESC
LIMIT 20;
```

---

#### 方案 3：使用 Elasticsearch（推荐）

```java
// 将保单数据同步到 Elasticsearch
// MySQL → Binlog → Canal → Kafka → Elasticsearch

// 查询保单
public List<Policy> queryPolicy(PolicyQueryDTO query) {
    SearchRequest request = new SearchRequest("policy");
    SearchSourceBuilder builder = new SearchSourceBuilder();
    
    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
    
    // 添加查询条件
    if (StringUtils.isNotBlank(query.getLicensePlate())) {
        boolQuery.must(QueryBuilders.termQuery("licensePlate", query.getLicensePlate()));
    }
    
    if (StringUtils.isNotBlank(query.getIdCard())) {
        boolQuery.must(QueryBuilders.termQuery("idCard", query.getIdCard()));
    }
    
    if (StringUtils.isNotBlank(query.getMobile())) {
        boolQuery.must(QueryBuilders.termQuery("mobile", query.getMobile()));
    }
    
    if (StringUtils.isNotBlank(query.getPolicyNo())) {
        boolQuery.must(QueryBuilders.termQuery("policyNo", query.getPolicyNo()));
    }
    
    // 状态过滤
    boolQuery.filter(QueryBuilders.termsQuery("status", "valid", "pending"));
    
    builder.query(boolQuery);
    builder.sort("createTime", SortOrder.DESC);
    builder.size(20);
    
    request.source(builder);
    SearchResponse response = client.search(request);
    
    return parseResponse(response);
}

// 优点：
// ✅ 支持复杂查询
// ✅ 查询速度快（50-100ms）
// ✅ 支持模糊搜索
```

---

### 性能对比

```
┌─────────────────────────────────────────────────────────────┐
│              保单查询 - 性能对比                                │
└─────────────────────────────────────────────────────────────┘

场景：按车牌号查询保单

方案 1：原始 SQL（OR 条件）
  ↓
  执行时间：3-5 秒
  扫描行数：1 亿行
  问题：❌ 太慢

方案 2：动态 SQL + 索引
  ↓
  执行时间：50-100ms
  扫描行数：1000 行
  优点：✅ 快，简单

方案 3：Elasticsearch
  ↓
  执行时间：50-100ms
  优点：✅ 快，支持复杂查询

性能提升：30-50 倍
```

---

## 案例 2：理赔查询优化 - 历史数据归档

### 背景

**场景**：理赔人员查询理赔记录

**典型问题**：
- 理赔表数据量巨大（10 亿+）
- 查询慢（5-10 秒）
- 历史理赔和进行中理赔混在一起
- 数据库存储压力大

**业务特点**：
- 理赔数据：10 亿+ 条
- 90% 的查询只查最近 1 年的数据
- 历史数据很少查询
- 理赔状态：待审核、审核中、已结案

---

### 问题 SQL

```sql
-- 查询理赔记录（慢）
SELECT * FROM claim 
WHERE policy_no = 'PICC2024001'
  AND status IN ('pending', 'processing', 'closed')
ORDER BY create_time DESC
LIMIT 20;

-- 问题：
-- 1. claim 表有 10 亿条数据
-- 2. 历史理赔和进行中理赔混在一起
-- 3. 即使有索引，查询也慢
-- 4. 执行时间：5-10 秒
```

---

### 解决方案

#### 方案 1：冷热分离

```sql
-- 热数据表（最近 1 年，进行中的理赔）
CREATE TABLE claim_hot (
  id BIGINT PRIMARY KEY,
  policy_no VARCHAR(50),
  claim_no VARCHAR(50),
  status VARCHAR(20),
  amount DECIMAL(10,2),
  create_time DATETIME,
  update_time DATETIME,
  INDEX idx_policy_status_time (policy_no, status, create_time)
) ENGINE=InnoDB;

-- 冷数据表（1 年前，已结案的理赔，按年分表）
CREATE TABLE claim_cold_2023 (
  id BIGINT PRIMARY KEY,
  policy_no VARCHAR(50),
  claim_no VARCHAR(50),
  status VARCHAR(20),
  amount DECIMAL(10,2),
  create_time DATETIME,
  update_time DATETIME,
  INDEX idx_policy_time (policy_no, create_time)
) ENGINE=InnoDB;

CREATE TABLE claim_cold_2022 (...);
CREATE TABLE claim_cold_2021 (...);
```

---

#### 方案 2：查询逻辑优化

```java
// 查询理赔记录
public List<Claim> queryClaim(String policyNo) {
    // 1. 先查热数据（最近 1 年）
    List<Claim> claims = claimMapper.selectFromHot(policyNo);
    
    // 2. 如果热数据不够，再查冷数据
    if (claims.size() < 20) {
        // 按年份倒序查询冷数据
        for (int year = 2023; year >= 2020 && claims.size() < 20; year--) {
            List<Claim> coldClaims = claimMapper.selectFromCold(policyNo, year);
            claims.addAll(coldClaims);
        }
    }
    
    return claims.subList(0, Math.min(20, claims.size()));
}
```

---

#### 方案 3：定时归档任务

```java
// 定时任务：每天凌晨 2 点执行
@Scheduled(cron = "0 0 2 * * ?")
public void archiveClaim() {
    // 1. 查询 1 年前已结案的理赔
    LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
    List<Claim> oldClaims = claimMapper.selectOldClaims(oneYearAgo);
    
    // 2. 批量插入到冷数据表
    int year = oneYearAgo.getYear();
    claimMapper.batchInsertToCold(oldClaims, year);
    
    // 3. 从热数据表删除
    claimMapper.deleteFromHot(oldClaims);
    
    log.info("归档理赔数据：{} 条", oldClaims.size());
}
```

---

### 性能对比

```
┌─────────────────────────────────────────────────────────────┐
│              理赔查询 - 性能对比                                │
└─────────────────────────────────────────────────────────────┘

场景：查询保单的理赔记录

方案 1：原始方案（单表）
  ↓
  表数据量：10 亿条
  执行时间：5-10 秒
  问题：❌ 太慢

方案 2：冷热分离
  ↓
  热数据表：1000 万条（最近 1 年）
  冷数据表：9 亿条（按年分表）
  执行时间：50-100ms
  优点：✅ 快

性能提升：50-100 倍

存储优化：
  热数据表：SSD（快速存储）
  冷数据表：HDD（便宜存储）
  成本节省：50%
```

---

## 案例 3：保费试算优化 - 高并发计算

### 背景

**场景**：用户在线报价，试算保费

**典型问题**：
- 保费试算慢（2-3 秒）
- 需要查询大量费率表
- 高并发（每秒 5000+ 次）
- 数据库压力大

**业务特点**：
- 费率表：100+ 张
- 计算规则复杂（车型、地区、驾龄、出险次数等）
- 需要实时计算
- 高峰期并发高

---

### 问题 SQL

```sql
-- 查询基础费率
SELECT base_rate FROM rate_base 
WHERE car_type = 'sedan' 
  AND engine_capacity = '2.0'
  AND province = 'beijing';

-- 查询地区系数
SELECT coefficient FROM rate_region 
WHERE province = 'beijing' 
  AND city = 'beijing';

-- 查询驾龄系数
SELECT coefficient FROM rate_driving_age 
WHERE driving_years = 5;

-- 查询出险系数
SELECT coefficient FROM rate_claim_history 
WHERE claim_count = 2;

-- ... 还有 10+ 个查询

-- 问题：
-- 1. 需要查询 10+ 张费率表
-- 2. 每次试算都要查询数据库
-- 3. 高并发下数据库压力大
-- 4. 总耗时：2-3 秒
```

---

### 解决方案

#### 方案 1：费率表缓存到 Redis

```java
// 启动时加载所有费率表到 Redis
@PostConstruct
public void loadRateTables() {
    // 1. 加载基础费率表
    List<RateBase> baseRates = rateMapper.selectAllBaseRates();
    for (RateBase rate : baseRates) {
        String key = String.format("rate:base:%s:%s:%s", 
            rate.getCarType(), rate.getEngineCapacity(), rate.getProvince());
        redis.set(key, rate.getBaseRate());
    }
    
    // 2. 加载地区系数表
    List<RateRegion> regionRates = rateMapper.selectAllRegionRates();
    for (RateRegion rate : regionRates) {
        String key = String.format("rate:region:%s:%s", 
            rate.getProvince(), rate.getCity());
        redis.set(key, rate.getCoefficient());
    }
    
    // 3. 加载其他费率表...
    
    log.info("费率表加载完成");
}

// 保费试算
public BigDecimal calculatePremium(QuoteRequest request) {
    // 1. 从 Redis 查询基础费率
    String baseKey = String.format("rate:base:%s:%s:%s", 
        request.getCarType(), request.getEngineCapacity(), request.getProvince());
    BigDecimal baseRate = redis.get(baseKey);
    
    // 2. 从 Redis 查询地区系数
    String regionKey = String.format("rate:region:%s:%s", 
        request.getProvince(), request.getCity());
    BigDecimal regionCoef = redis.get(regionKey);
    
    // 3. 从 Redis 查询其他系数...
    
    // 4. 计算保费
    BigDecimal premium = baseRate
        .multiply(regionCoef)
        .multiply(drivingAgeCoef)
        .multiply(claimHistoryCoef);
    
    return premium;
}
```

---

#### 方案 2：本地缓存 + 定时刷新

```java
// 使用 Guava Cache 本地缓存
@Component
public class RateCacheService {
    
    // 本地缓存（1 小时过期）
    private LoadingCache<String, BigDecimal> rateCache = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build(new CacheLoader<String, BigDecimal>() {
            @Override
            public BigDecimal load(String key) {
                // 从数据库加载
                return loadFromDatabase(key);
            }
        });
    
    // 获取费率
    public BigDecimal getRate(String key) {
        try {
            return rateCache.get(key);
        } catch (ExecutionException e) {
            log.error("获取费率失败", e);
            return BigDecimal.ZERO;
        }
    }
    
    // 定时刷新缓存（每小时）
    @Scheduled(fixedRate = 3600000)
    public void refreshCache() {
        rateCache.invalidateAll();
        log.info("费率缓存已刷新");
    }
}
```

---

### 性能对比

```
┌─────────────────────────────────────────────────────────────┐
│              保费试算 - 性能对比                                │
└─────────────────────────────────────────────────────────────┘

场景：用户在线报价

方案 1：原始方案（每次查数据库）
  ↓
  查询次数：10+ 次
  执行时间：2-3 秒
  数据库 QPS：50000+（高峰期）
  问题：❌ 慢，数据库压力大

方案 2：Redis 缓存
  ↓
  查询次数：10+ 次（Redis）
  执行时间：50-100ms
  数据库 QPS：0
  优点：✅ 快，数据库无压力

方案 3：本地缓存
  ↓
  查询次数：10+ 次（本地内存）
  执行时间：10-20ms
  数据库 QPS：0
  优点：✅ 最快，无网络开销

性能提升：100-200 倍
```

---

## 案例 4：续保提醒优化 - 大批量数据处理

### 背景

**场景**：每天定时任务，查询即将到期的保单，发送续保提醒

**典型问题**：
- 需要扫描全表（1 亿+ 保单）
- 定时任务执行慢（1-2 小时）
- 数据库压力大
- 影响其他业务

**业务特点**：
- 保单总量：1 亿+
- 每天到期保单：10 万+
- 提前 30 天提醒
- 需要每天执行

---

### 问题 SQL

```sql
-- 查询即将到期的保单（慢）
SELECT * FROM policy 
WHERE end_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)
  AND status = 'valid'
  AND renewal_reminded = 0;

-- 问题：
-- 1. 需要扫描全表（1 亿+ 保单）
-- 2. 即使有索引，也要扫描大量数据
-- 3. 执行时间：1-2 小时
-- 4. 影响其他业务
```

---

### 解决方案

#### 方案 1：建立到期日期索引 + 分批处理

```sql
-- 建立索引
CREATE INDEX idx_enddate_status_reminded 
ON policy(end_date, status, renewal_reminded);

-- 优化后的查询
SELECT id, policy_no, mobile, end_date 
FROM policy 
WHERE end_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)
  AND status = 'valid'
  AND renewal_reminded = 0
LIMIT 1000;  -- 分批查询
```

```java
// 分批处理
@Scheduled(cron = "0 0 1 * * ?")  // 每天凌晨 1 点执行
public void sendRenewalReminder() {
    int pageSize = 1000;
    int offset = 0;
    int totalProcessed = 0;
    
    while (true) {
        // 分批查询
        List<Policy> policies = policyMapper.selectExpiringPolicies(offset, pageSize);
        
        if (policies.isEmpty()) {
            break;
        }
        
        // 批量发送提醒
        for (Policy policy : policies) {
            sendSMS(policy.getMobile(), "您的保单即将到期，请及时续保");
            
            // 更新提醒状态
            policyMapper.updateReminderStatus(policy.getId());
        }
        
        totalProcessed += policies.size();
        offset += pageSize;
        
        // 休息 1 秒，避免数据库压力过大
        Thread.sleep(1000);
    }
    
    log.info("续保提醒发送完成，共处理 {} 条", totalProcessed);
}
```

---

#### 方案 2：使用到期日期分区表

```sql
-- 按到期日期分区（按月）
CREATE TABLE policy (
  id BIGINT PRIMARY KEY,
  policy_no VARCHAR(50),
  end_date DATE,
  status VARCHAR(20),
  renewal_reminded TINYINT,
  ...
) PARTITION BY RANGE (TO_DAYS(end_date)) (
  PARTITION p202401 VALUES LESS THAN (TO_DAYS('2024-02-01')),
  PARTITION p202402 VALUES LESS THAN (TO_DAYS('2024-03-01')),
  PARTITION p202403 VALUES LESS THAN (TO_DAYS('2024-04-01')),
  ...
  PARTITION p202412 VALUES LESS THAN (TO_DAYS('2025-01-01')),
  PARTITION pmax VALUES LESS THAN MAXVALUE
);

-- 查询时只扫描相关分区
SELECT * FROM policy 
WHERE end_date BETWEEN '2024-03-01' AND '2024-03-31'
  AND status = 'valid'
  AND renewal_reminded = 0;

-- 优点：
-- ✅ 只扫描相关分区（1/12 的数据）
-- ✅ 查询速度快
```

---

#### 方案 3：使用 Redis 有序集合

```java
// 保单生效时，将到期日期存入 Redis
public void createPolicy(Policy policy) {
    // 1. 保存到 MySQL
    policyMapper.insert(policy);
    
    // 2. 将到期日期存入 Redis Sorted Set
    // score = 到期日期的时间戳
    long expireTimestamp = policy.getEndDate().getTime();
    redis.zadd("policy:expire", expireTimestamp, policy.getId().toString());
}

// 定时任务：查询即将到期的保单
@Scheduled(cron = "0 0 1 * * ?")
public void sendRenewalReminder() {
    // 1. 从 Redis 查询 30 天内到期的保单
    long now = System.currentTimeMillis();
    long thirtyDaysLater = now + 30 * 24 * 60 * 60 * 1000L;
    
    Set<String> policyIds = redis.zrangeByScore("policy:expire", now, thirtyDaysLater);
    
    // 2. 批量查询保单详情
    List<Policy> policies = policyMapper.selectByIds(policyIds);
    
    // 3. 发送提醒
    for (Policy policy : policies) {
        if (policy.getRenewalReminded() == 0) {
            sendSMS(policy.getMobile(), "您的保单即将到期");
            policyMapper.updateReminderStatus(policy.getId());
        }
    }
    
    log.info("续保提醒发送完成，共处理 {} 条", policies.size());
}
```

---

### 性能对比

```
┌─────────────────────────────────────────────────────────────┐
│              续保提醒 - 性能对比                                │
└─────────────────────────────────────────────────────────────┘

场景：每天查询即将到期的保单

方案 1：原始方案（全表扫描）
  ↓
  扫描行数：1 亿行
  执行时间：1-2 小时
  问题：❌ 太慢，影响其他业务

方案 2：索引 + 分批处理
  ↓
  扫描行数：10 万行
  执行时间：5-10 分钟
  优点：✅ 快很多

方案 3：分区表
  ↓
  扫描行数：800 万行（1/12）
  执行时间：2-5 分钟
  优点：✅ 快

方案 4：Redis Sorted Set
  ↓
  查询次数：1 次（Redis）
  执行时间：1-2 分钟
  优点：✅ 最快

性能提升：30-60 倍
```

---

## 案例 5：出险记录查询 - 多表关联优化

### 背景

**场景**：查询车辆的出险记录（包含保单、理赔、车辆信息）

**典型问题**：
- 需要关联 5+ 张表
- 查询慢（5-10 秒）
- 数据量大
- 复杂的 JOIN

**业务特点**：
- 需要关联：保单表、理赔表、车辆表、车主表、理赔明细表
- 每张表都是亿级数据
- 查询频率高

---

### 问题 SQL

```sql
-- 查询出险记录（慢）
SELECT 
  p.policy_no,
  p.start_date,
  p.end_date,
  c.claim_no,
  c.claim_date,
  c.amount,
  v.license_plate,
  v.car_model,
  o.owner_name,
  o.mobile,
  cd.damage_type,
  cd.damage_amount
FROM policy p
INNER JOIN claim c ON p.policy_no = c.policy_no
INNER JOIN vehicle v ON p.vehicle_id = v.id
INNER JOIN owner o ON p.owner_id = o.id
INNER JOIN claim_detail cd ON c.claim_no = cd.claim_no
WHERE v.license_plate = '京A12345'
ORDER BY c.claim_date DESC
LIMIT 20;

-- 问题：
-- 1. 关联 5 张表
-- 2. 每张表都是亿级数据
-- 3. 执行时间：5-10 秒
```

---

### 解决方案

#### 方案 1：宽表设计（推荐）

```sql
-- 创建宽表（包含常用字段）
CREATE TABLE claim_wide (
  id BIGINT PRIMARY KEY,
  policy_no VARCHAR(50),
  claim_no VARCHAR(50),
  license_plate VARCHAR(20),
  car_model VARCHAR(50),
  owner_name VARCHAR(50),
  mobile VARCHAR(20),
  claim_date DATE,
  amount DECIMAL(10,2),
  status VARCHAR(20),
  create_time DATETIME,
  INDEX idx_license_date (license_plate, claim_date)
) ENGINE=InnoDB;

-- 数据同步：通过 Binlog + Canal 实时同步
// 当理赔数据变化时，自动更新宽表

-- 优化后的查询
SELECT * FROM claim_wide 
WHERE license_plate = '京A12345'
ORDER BY claim_date DESC
LIMIT 20;

-- 优点：
-- ✅ 只查一张表
-- ✅ 查询速度快（50-100ms）
```

---

#### 方案 2：分步查询 + 缓存

```java
// 分步查询，避免大表 JOIN
public List<ClaimRecord> queryClaimRecords(String licensePlate) {
    // 1. 查询车辆信息（缓存）
    Vehicle vehicle = vehicleCache.get(licensePlate);
    if (vehicle == null) {
        vehicle = vehicleMapper.selectByLicensePlate(licensePlate);
        vehicleCache.put(licensePlate, vehicle);
    }
    
    // 2. 查询保单列表
    List<Policy> policies = policyMapper.selectByVehicleId(vehicle.getId());
    
    // 3. 查询理赔记录
    List<String> policyNos = policies.stream()
        .map(Policy::getPolicyNo)
        .collect(Collectors.toList());
    
    List<Claim> claims = claimMapper.selectByPolicyNos(policyNos);
    
    // 4. 查询车主信息（批量查询 + 缓存）
    Set<Long> ownerIds = policies.stream()
        .map(Policy::getOwnerId)
        .collect(Collectors.toSet());
    
    Map<Long, Owner> owners = ownerMapper.selectByIds(ownerIds).stream()
        .collect(Collectors.toMap(Owner::getId, o -> o));
    
    // 5. 组装数据
    List<ClaimRecord> records = new ArrayList<>();
    for (Claim claim : claims) {
        Policy policy = policies.stream()
            .filter(p -> p.getPolicyNo().equals(claim.getPolicyNo()))
            .findFirst().orElse(null);
        
        if (policy != null) {
            Owner owner = owners.get(policy.getOwnerId());
            records.add(new ClaimRecord(policy, claim, vehicle, owner));
        }
    }
    
    return records;
}
```

---

### 性能对比

```
┌─────────────────────────────────────────────────────────────┐
│              出险记录查询 - 性能对比                            │
└─────────────────────────────────────────────────────────────┘

场景：查询车辆的出险记录

方案 1：原始方案（5 表 JOIN）
  ↓
  执行时间：5-10 秒
  扫描行数：数百万行
  问题：❌ 太慢

方案 2：宽表设计
  ↓
  执行时间：50-100ms
  扫描行数：100 行
  优点：✅ 快

方案 3：分步查询 + 缓存
  ↓
  执行时间：100-200ms
  查询次数：3-4 次
  优点：✅ 灵活，可缓存

性能提升：25-100 倍
```

---

## 案例 6：保单统计报表 - 复杂聚合查询

### 背景

**场景**：管理后台查询保单统计报表

**典型问题**：
- 复杂的聚合查询（GROUP BY、SUM、COUNT）
- 查询慢（30 秒 - 1 分钟）
- 数据量大（1 亿+ 保单）
- 影响在线业务

**业务特点**：
- 统计维度多（地区、险种、渠道、时间）
- 需要实时统计
- 查询频率高

---

### 问题 SQL

```sql
-- 统计报表（慢）
SELECT 
  province,
  product_type,
  channel,
  COUNT(*) as policy_count,
  SUM(premium) as total_premium,
  AVG(premium) as avg_premium
FROM policy 
WHERE create_time BETWEEN '2024-01-01' AND '2024-12-31'
  AND status = 'valid'
GROUP BY province, product_type, channel
ORDER BY total_premium DESC;

-- 问题：
-- 1. 需要扫描全年数据（1 亿+ 保单）
-- 2. 复杂的 GROUP BY
-- 3. 执行时间：30 秒 - 1 分钟
-- 4. 影响在线业务
```

---

### 解决方案

#### 方案 1：预聚合表（推荐）

```sql
-- 创建统计表（按天预聚合）
CREATE TABLE policy_stats_daily (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  stat_date DATE,
  province VARCHAR(50),
  product_type VARCHAR(50),
  channel VARCHAR(50),
  policy_count INT,
  total_premium DECIMAL(15,2),
  avg_premium DECIMAL(10,2),
  create_time DATETIME,
  UNIQUE KEY uk_date_province_product_channel (stat_date, province, product_type, channel)
) ENGINE=InnoDB;

-- 定时任务：每天凌晨统计前一天的数据
@Scheduled(cron = "0 0 2 * * ?")
public void generateDailyStats() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    
    // 统计前一天的数据
    List<PolicyStats> stats = policyMapper.aggregateByDay(yesterday);
    
    // 插入统计表
    policyStatsMapper.batchInsert(stats);
}

-- 查询报表（快）
SELECT 
  province,
  product_type,
  channel,
  SUM(policy_count) as policy_count,
  SUM(total_premium) as total_premium,
  AVG(avg_premium) as avg_premium
FROM policy_stats_daily 
WHERE stat_date BETWEEN '2024-01-01' AND '2024-12-31'
GROUP BY province, product_type, channel
ORDER BY total_premium DESC;

-- 优点：
-- ✅ 只查统计表（365 * 1000 = 36.5 万行）
-- ✅ 查询速度快（1-2 秒）
```

---

#### 方案 2：使用 ClickHouse（OLAP 数据库）

```sql
-- 将保单数据同步到 ClickHouse
-- MySQL → Binlog → Canal → Kafka → ClickHouse

-- ClickHouse 表结构
CREATE TABLE policy (
  policy_no String,
  province String,
  product_type String,
  channel String,
  premium Decimal(10,2),
  create_time DateTime,
  status String
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(create_time)
ORDER BY (province, product_type, channel, create_time);

-- 查询报表（快）
SELECT 
  province,
  product_type,
  channel,
  COUNT(*) as policy_count,
  SUM(premium) as total_premium,
  AVG(premium) as avg_premium
FROM policy 
WHERE create_time BETWEEN '2024-01-01' AND '2024-12-31'
  AND status = 'valid'
GROUP BY province, product_type, channel
ORDER BY total_premium DESC;

-- 优点：
-- ✅ ClickHouse 专为 OLAP 设计
-- ✅ 查询速度快（1-5 秒）
-- ✅ 支持复杂聚合
```

---

### 性能对比

```
┌─────────────────────────────────────────────────────────────┐
│              保单统计报表 - 性能对比                            │
└─────────────────────────────────────────────────────────────┘

场景：查询全年保单统计报表

方案 1：原始方案（MySQL 实时聚合）
  ↓
  扫描行数：1 亿行
  执行时间：30 秒 - 1 分钟
  问题：❌ 太慢，影响在线业务

方案 2：预聚合表
  ↓
  扫描行数：36.5 万行
  执行时间：1-2 秒
  优点：✅ 快

方案 3：ClickHouse
  ↓
  扫描行数：1 亿行（列式存储，快）
  执行时间：1-5 秒
  优点：✅ 快，支持复杂查询

性能提升：15-60 倍
```

---

## 保险车险系统 SQL 优化总结

### 1. 核心优化策略

```
策略 1：冷热分离
  ✅ 热数据：最近 1 年（MySQL SSD）
  ✅ 冷数据：历史数据（MySQL HDD / HBase）
  ✅ 适用：保单、理赔、出险记录

策略 2：缓存优化
  ✅ Redis：费率表、车辆信息、车主信息
  ✅ 本地缓存：费率表（Guava Cache）
  ✅ 适用：高频查询、不常变化的数据

策略 3：宽表设计
  ✅ 避免多表 JOIN
  ✅ 提升查询速度
  ✅ 适用：出险记录、理赔查询

策略 4：预聚合
  ✅ 定时统计，生成报表
  ✅ 查询统计表，不查原始数据
  ✅ 适用：统计报表、数据分析

策略 5：分库分表
  ✅ 按地区分库（华北、华东、华南等）
  ✅ 按时间分表（按年、按月）
  ✅ 适用：超大数据量（10 亿+）
```

---

### 2. 保险行业特点

```
数据特点：
  ✅ 数据量大（亿级）
  ✅ 历史数据多（90% 是历史数据）
  ✅ 查询频繁（客服、业务员、用户）
  ✅ 计算复杂（保费试算、理赔计算）

业务特点：
  ✅ 强监管（数据不能丢）
  ✅ 高可用（7x24 小时）
  ✅ 高并发（大促、续保高峰）
  ✅ 复杂查询（多条件、多表关联）
```

---

### 3. 推荐架构

```
┌─────────────────────────────────────────────────────────────┐
│              保险车险系统推荐架构                              │
└─────────────────────────────────────────────────────────────┘

在线业务（OLTP）：
  ✅ MySQL（主从复制、读写分离）
  ✅ Redis（缓存热点数据）
  ✅ Elasticsearch（复杂查询）

离线分析（OLAP）：
  ✅ ClickHouse（统计报表）
  ✅ HBase（历史数据归档）
  ✅ Hive（数据仓库）

数据同步：
  ✅ Binlog + Canal + Kafka
  ✅ 实时同步到 Redis、ES、ClickHouse
```

---

## 一句话总结

**保险车险系统 SQL 优化的核心：冷热分离（历史数据归档）+ 缓存优化（费率表缓存）+ 宽表设计（避免多表 JOIN）+ 预聚合（统计报表）+ 分库分表（超大数据量）。90% 的性能问题都可以通过这 5 个策略解决！**

