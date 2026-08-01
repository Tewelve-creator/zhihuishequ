package com.community.repair.store;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 商户优惠券：按 shop_id 隔离，仅限本店商品使用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopCouponService {

    public static final String TYPE_FULL_REDUCE = "FULL_REDUCE";
    public static final String TYPE_NO_THRESHOLD = "NO_THRESHOLD";
    public static final String TYPE_DISCOUNT = "DISCOUNT";

    private final JdbcTemplate jdbc;

    @PostConstruct
    public void ensure() {
        try {
            jdbc.execute("""
                    create table if not exists store_coupon (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      shop_id bigint not null,
                      name varchar(128) not null,
                      cover_url varchar(512) null,
                      coupon_type varchar(32) not null comment 'FULL_REDUCE/NO_THRESHOLD/DISCOUNT',
                      threshold_amount decimal(10,2) not null default 0,
                      discount_amount decimal(10,2) not null default 0,
                      discount_rate decimal(5,2) null comment '折扣，如8.5表示8.5折',
                      total_count int not null default 0,
                      claimed_count int not null default 0,
                      limit_per_user int not null default 1,
                      start_time datetime null,
                      end_time datetime null,
                      remark varchar(500) null,
                      status tinyint not null default 1 comment '1上架 0下架',
                      create_time datetime null,
                      update_time datetime null,
                      key idx_sc_shop (shop_id, status),
                      key idx_sc_tenant (tenant_id)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            jdbc.execute("""
                    create table if not exists store_coupon_claim (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      shop_id bigint not null,
                      coupon_id bigint not null,
                      user_id bigint null,
                      user_name varchar(64) null,
                      user_phone varchar(32) null,
                      claim_status varchar(16) not null default 'UNUSED' comment 'UNUSED/USED/EXPIRED',
                      claim_time datetime null,
                      use_time datetime null,
                      order_id bigint null,
                      key idx_scc_coupon (coupon_id),
                      key idx_scc_shop (shop_id)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
        } catch (Exception e) {
            log.warn("store_coupon schema skip: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> listCoupons(String keyword, String statusFilter) {
        assertMerchant();
        Long shopId = requireShopId();
        Long tid = requireTenant();
        StringBuilder sql = new StringBuilder(
                "select id, tenant_id as tenantId, shop_id as shopId, name, cover_url as coverUrl, "
                        + "coupon_type as couponType, threshold_amount as thresholdAmount, "
                        + "discount_amount as discountAmount, discount_rate as discountRate, "
                        + "total_count as totalCount, claimed_count as claimedCount, "
                        + "limit_per_user as limitPerUser, start_time as startTime, end_time as endTime, "
                        + "remark, status, create_time as createTime, update_time as updateTime "
                        + "from store_coupon where tenant_id=? and shop_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        args.add(shopId);
        if (StringUtils.hasText(keyword)) {
            sql.append("and name like ? ");
            args.add("%" + keyword.trim() + "%");
        }
        sql.append("order by id desc limit 1000");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            enrichCoupon(r);
            if (matchStatusFilter(r, statusFilter)) {
                out.add(r);
            }
        }
        return out;
    }

    @Transactional
    public Map<String, Object> saveCoupon(Map<String, Object> body) {
        assertMerchant();
        Long shopId = requireShopId();
        Long tid = requireTenant();
        String name = str(body.get("name"), null);
        if (!StringUtils.hasText(name)) {
            throw new BizException(400, "优惠券名称不能为空");
        }
        Long id = toLong(body.get("id"));
        String type = normalizeType(str(body.get("couponType"), TYPE_FULL_REDUCE));
        BigDecimal threshold = money(body.get("thresholdAmount"));
        BigDecimal discountAmount = money(body.get("discountAmount"));
        BigDecimal discountRate = body.get("discountRate") == null ? null : money(body.get("discountRate"));
        int totalCount = toInt(body.get("totalCount"), 0);
        int limitPerUser = Math.max(1, toInt(body.get("limitPerUser"), 1));
        String cover = str(body.get("coverUrl"), null);
        String remark = str(body.get("remark"), null);
        LocalDateTime start = parseTime(body.get("startTime"));
        LocalDateTime end = parseTime(body.get("endTime"));
        if (start != null && end != null && end.isBefore(start)) {
            throw new BizException(400, "结束时间不能早于开始时间");
        }
        validateBenefit(type, threshold, discountAmount, discountRate);

        if (id != null && id > 0) {
            Map<String, Object> old = requireCoupon(id, shopId);
            enrichCoupon(old);
            boolean ongoing = "ONGOING".equals(String.valueOf(old.get("bizStatus")));
            if (ongoing) {
                // 进行中：禁止改优惠金额/类型/门槛，仅允许改展示信息、总量(不可低于已领)、限领、备注、图片
                type = str(old.get("couponType"), type);
                threshold = money(old.get("thresholdAmount"));
                discountAmount = money(old.get("discountAmount"));
                discountRate = old.get("discountRate") == null ? null : money(old.get("discountRate"));
            }
            int claimed = toInt(old.get("claimedCount"), 0);
            if (totalCount < claimed) {
                throw new BizException(400, "发放总量不能小于已领取数量");
            }
            jdbc.update(
                    "update store_coupon set name=?, cover_url=?, coupon_type=?, threshold_amount=?, "
                            + "discount_amount=?, discount_rate=?, total_count=?, limit_per_user=?, "
                            + "start_time=?, end_time=?, remark=?, update_time=now() where id=? and shop_id=?",
                    name, cover, type, threshold, discountAmount, discountRate, totalCount, limitPerUser,
                    start == null ? null : Timestamp.valueOf(start),
                    end == null ? null : Timestamp.valueOf(end),
                    remark, id, shopId);
            if (ongoing && (start != null || end != null)) {
                // 进行中允许微调时间与展示，但优惠参数已锁
            }
        } else {
            if (totalCount < 0) {
                throw new BizException(400, "发放总量不能为负数");
            }
            jdbc.update(
                    "insert into store_coupon(tenant_id,shop_id,name,cover_url,coupon_type,threshold_amount,"
                            + "discount_amount,discount_rate,total_count,claimed_count,limit_per_user,"
                            + "start_time,end_time,remark,status,create_time,update_time) "
                            + "values(?,?,?,?,?,?,?,?,?,0,?,?,?,?,1,now(),now())",
                    tid, shopId, name, cover, type, threshold, discountAmount, discountRate,
                    totalCount, limitPerUser,
                    start == null ? null : Timestamp.valueOf(start),
                    end == null ? null : Timestamp.valueOf(end),
                    remark);
            id = jdbc.queryForObject("select last_insert_id()", Long.class);
        }
        return getCoupon(id);
    }

    @Transactional
    public Map<String, Object> offShelf(Long couponId) {
        assertMerchant();
        Long shopId = requireShopId();
        requireCoupon(couponId, shopId);
        jdbc.update("update store_coupon set status=0, update_time=now() where id=? and shop_id=?",
                couponId, shopId);
        return getCoupon(couponId);
    }

    public Map<String, Object> getCoupon(Long couponId) {
        assertMerchant();
        Map<String, Object> row = requireCoupon(couponId, requireShopId());
        enrichCoupon(row);
        return row;
    }

    public List<Map<String, Object>> listClaims(Long couponId) {
        assertMerchant();
        Long shopId = requireShopId();
        requireCoupon(couponId, shopId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, coupon_id as couponId, user_id as userId, user_name as userName, "
                        + "user_phone as userPhone, claim_status as claimStatus, "
                        + "claim_time as claimTime, use_time as useTime, order_id as orderId "
                        + "from store_coupon_claim where coupon_id=? and shop_id=? order by id desc limit 500",
                couponId, shopId);
        for (Map<String, Object> r : rows) {
            normalizeTime(r, "claimTime");
            normalizeTime(r, "useTime");
            String st = str(r.get("claimStatus"), "UNUSED");
            r.put("claimStatusLabel", switch (st) {
                case "USED" -> "已使用";
                case "EXPIRED" -> "已过期";
                default -> "未使用";
            });
        }
        return rows;
    }

    /** 居民：店铺可领取的进行中优惠券 */
    public List<Map<String, Object>> listOpenShopCoupons(Long shopId) {
        if (shopId == null) throw new BizException(400, "缺少店铺");
        ensure();
        Long tid = requireTenant();
        Long uid = TenantContext.getUserId();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, tenant_id as tenantId, shop_id as shopId, name, cover_url as coverUrl, "
                        + "coupon_type as couponType, threshold_amount as thresholdAmount, "
                        + "discount_amount as discountAmount, discount_rate as discountRate, "
                        + "total_count as totalCount, claimed_count as claimedCount, "
                        + "limit_per_user as limitPerUser, start_time as startTime, end_time as endTime, "
                        + "remark, status, create_time as createTime, update_time as updateTime "
                        + "from store_coupon where tenant_id=? and shop_id=? and status=1 order by id desc",
                tid, shopId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (isFreshCoupon(str(r.get("name"), null))) continue;
            enrichCoupon(r);
            if (!"ONGOING".equals(String.valueOf(r.get("bizStatus")))) continue;
            int myClaims = countUserClaims(toLong(r.get("id")), uid);
            int limit = Math.max(1, toInt(r.get("limitPerUser"), 1));
            int total = toInt(r.get("totalCount"), 0);
            int claimedAll = toInt(r.get("claimedCount"), 0);
            boolean soldOut = total > 0 && claimedAll >= total;
            boolean claimedByMe = myClaims >= limit;
            r.put("claimedByMe", claimedByMe);
            r.put("soldOut", soldOut);
            r.put("canClaim", !claimedByMe && !soldOut);
            out.add(r);
        }
        return out;
    }

    /** 居民：我的可用券（已领取未使用），可用于结算选择 */
    public List<Map<String, Object>> listMyUsableCoupons(Long shopId, BigDecimal goodsAmount) {
        if (shopId == null) throw new BizException(400, "缺少店铺");
        ensure();
        Long tid = requireTenant();
        Long uid = TenantContext.getUserId();
        if (uid == null) throw new BizException(401, "请先登录");
        BigDecimal goods = goodsAmount == null ? BigDecimal.ZERO : goodsAmount;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select c.id as claimId, c.coupon_id as couponId, c.claim_status as claimStatus, "
                        + "k.name, k.coupon_type as couponType, k.threshold_amount as thresholdAmount, "
                        + "k.discount_amount as discountAmount, k.discount_rate as discountRate, "
                        + "k.start_time as startTime, k.end_time as endTime, k.status "
                        + "from store_coupon_claim c "
                        + "join store_coupon k on k.id=c.coupon_id "
                        + "where c.tenant_id=? and c.shop_id=? and c.user_id=? and c.claim_status='UNUSED' "
                        + "and k.status=1 order by c.id desc",
                tid, shopId, uid);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (isFreshCoupon(str(r.get("name"), null))) continue;
            enrichCoupon(r);
            if (!"ONGOING".equals(String.valueOf(r.get("bizStatus")))) continue;
            BigDecimal discount = calcDiscount(r, goods);
            boolean meet = meetsThreshold(r, goods);
            r.put("discountPreview", discount);
            r.put("usable", meet && discount.compareTo(BigDecimal.ZERO) > 0);
            r.put("unusableReason", meet ? null : "未满使用门槛");
            out.add(r);
        }
        return out;
    }

    /**
     * 结算使用优惠券：校验 claim 归属与门槛，返回折扣金额；不落库（由下单写 USED）
     */
    public Map<String, Object> previewUseClaim(Long shopId, Long claimId, BigDecimal goodsAmount) {
        Map<String, Object> claim = loadUsableClaim(shopId, claimId);
        BigDecimal goods = goodsAmount == null ? BigDecimal.ZERO : goodsAmount;
        if (!meetsThreshold(claim, goods)) {
            throw new BizException(400, "未满优惠券使用门槛");
        }
        BigDecimal discount = calcDiscount(claim, goods);
        if (discount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "该优惠券当前不可用");
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("claimId", claimId);
        resp.put("couponId", claim.get("couponId"));
        resp.put("name", claim.get("name"));
        resp.put("ruleText", claim.get("ruleText"));
        resp.put("discountAmount", discount);
        return resp;
    }

    public void markClaimUsed(Long claimId, Long orderId) {
        if (claimId == null) return;
        int n = jdbc.update(
                "update store_coupon_claim set claim_status='USED', use_time=now(), order_id=? "
                        + "where id=? and claim_status='UNUSED'",
                orderId, claimId);
        if (n == 0) throw new BizException(400, "优惠券不可用或已使用");
    }

    public void releaseClaimIfUnused(Long claimId) {
        if (claimId == null) return;
        try {
            jdbc.update(
                    "update store_coupon_claim set claim_status='UNUSED', use_time=null, order_id=null "
                            + "where id=? and claim_status='USED' and order_id is not null",
                    claimId);
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> loadUsableClaim(Long shopId, Long claimId) {
        Long uid = TenantContext.getUserId();
        if (uid == null) throw new BizException(401, "请先登录");
        if (shopId == null || claimId == null) throw new BizException(400, "请选择优惠券");
        ensure();
        try {
            Map<String, Object> r = jdbc.queryForMap(
                    "select c.id as claimId, c.coupon_id as couponId, c.user_id as userId, "
                            + "c.claim_status as claimStatus, c.shop_id as shopId, "
                            + "k.name, k.coupon_type as couponType, k.threshold_amount as thresholdAmount, "
                            + "k.discount_amount as discountAmount, k.discount_rate as discountRate, "
                            + "k.start_time as startTime, k.end_time as endTime, k.status "
                            + "from store_coupon_claim c "
                            + "join store_coupon k on k.id=c.coupon_id "
                            + "where c.id=? and c.shop_id=? and c.user_id=?",
                    claimId, shopId, uid);
            if (!"UNUSED".equals(str(r.get("claimStatus"), ""))) {
                throw new BizException(400, "优惠券已使用或不可用");
            }
            if (isFreshCoupon(str(r.get("name"), null))) {
                throw new BizException(400, "该优惠券不可用");
            }
            enrichCoupon(r);
            if (!"ONGOING".equals(String.valueOf(r.get("bizStatus"))) || toInt(r.get("status"), 0) != 1) {
                throw new BizException(400, "优惠券不在有效期");
            }
            return r;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(404, "优惠券不存在");
        }
    }

    private static boolean isFreshCoupon(String name) {
        return StringUtils.hasText(name) && name.contains("生鲜");
    }

    private static boolean meetsThreshold(Map<String, Object> coupon, BigDecimal goods) {
        String type = str(coupon.get("couponType"), TYPE_FULL_REDUCE);
        BigDecimal threshold = money(coupon.get("thresholdAmount"));
        if (TYPE_NO_THRESHOLD.equals(type)) return true;
        return goods.compareTo(threshold) >= 0;
    }

    /** 按商品金额计算优惠（不超过商品金额） */
    public static BigDecimal calcDiscount(Map<String, Object> coupon, BigDecimal goodsAmount) {
        BigDecimal goods = goodsAmount == null ? BigDecimal.ZERO : goodsAmount;
        if (goods.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        String type = str(coupon.get("couponType"), TYPE_FULL_REDUCE);
        BigDecimal threshold = money(coupon.get("thresholdAmount"));
        BigDecimal amount = money(coupon.get("discountAmount"));
        BigDecimal rate = coupon.get("discountRate") == null ? null : money(coupon.get("discountRate"));
        BigDecimal discount = BigDecimal.ZERO;
        switch (type) {
            case TYPE_FULL_REDUCE -> {
                if (goods.compareTo(threshold) >= 0) discount = amount;
            }
            case TYPE_NO_THRESHOLD -> discount = amount;
            case TYPE_DISCOUNT -> {
                if (rate != null && (threshold.compareTo(BigDecimal.ZERO) <= 0 || goods.compareTo(threshold) >= 0)) {
                    // 8.5折 → 优惠 = goods * (1 - 0.85)
                    BigDecimal ratio = rate.divide(new BigDecimal("10"), 4, RoundingMode.HALF_UP);
                    discount = goods.multiply(BigDecimal.ONE.subtract(ratio)).setScale(2, RoundingMode.HALF_UP);
                }
            }
            default -> {
            }
        }
        if (discount.compareTo(goods) > 0) discount = goods;
        if (discount.compareTo(BigDecimal.ZERO) < 0) discount = BigDecimal.ZERO;
        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    /** 居民：领取优惠券 */
    @Transactional
    public Map<String, Object> claimForUser(Long shopId, Long couponId) {
        if (shopId == null || couponId == null) throw new BizException(400, "参数错误");
        ensure();
        Long tid = requireTenant();
        Long uid = TenantContext.getUserId();
        if (uid == null) throw new BizException(401, "请先登录");
        Map<String, Object> coupon = requireCoupon(couponId, shopId);
        assertTenantAccess(toLong(coupon.get("tenantId")));
        enrichCoupon(coupon);
        if (isFreshCoupon(str(coupon.get("name"), null))) {
            throw new BizException(400, "该优惠券不可领取");
        }
        if (toInt(coupon.get("status"), 0) != 1) {
            throw new BizException(400, "优惠券已下架");
        }
        if (!"ONGOING".equals(String.valueOf(coupon.get("bizStatus")))) {
            throw new BizException(400, "优惠券不在领取期");
        }
        int limit = Math.max(1, toInt(coupon.get("limitPerUser"), 1));
        if (countUserClaims(couponId, uid) >= limit) {
            throw new BizException(400, "您已领取过该券");
        }
        int updated = jdbc.update(
                "update store_coupon set claimed_count=ifnull(claimed_count,0)+1, update_time=now() "
                        + "where id=? and shop_id=? and status=1 "
                        + "and (total_count<=0 or ifnull(claimed_count,0)<total_count)",
                couponId, shopId);
        if (updated == 0) {
            throw new BizException(400, "优惠券已领完");
        }
        String userName = null;
        String userPhone = null;
        try {
            Map<String, Object> u = jdbc.queryForMap(
                    "select nickname, phone from sys_user where id=?", uid);
            userName = str(u.get("nickname"), null);
            userPhone = str(u.get("phone"), null);
        } catch (Exception ignored) {
        }
        jdbc.update(
                "insert into store_coupon_claim(tenant_id,shop_id,coupon_id,user_id,user_name,user_phone,"
                        + "claim_status,claim_time) values(?,?,?,?,?,?,'UNUSED',now())",
                tid, shopId, couponId, uid, userName, userPhone);
        Map<String, Object> resp = new LinkedHashMap<>(coupon);
        resp.put("claimedByMe", true);
        resp.put("canClaim", false);
        resp.put("message", "领取成功");
        return resp;
    }

    private int countUserClaims(Long couponId, Long userId) {
        if (couponId == null || userId == null) return 0;
        try {
            Integer n = jdbc.queryForObject(
                    "select count(1) from store_coupon_claim where coupon_id=? and user_id=?",
                    Integer.class, couponId, userId);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return 0;
        }
    }

    private void validateBenefit(String type, BigDecimal threshold, BigDecimal discountAmount, BigDecimal discountRate) {
        switch (type) {
            case TYPE_FULL_REDUCE -> {
                if (threshold.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BizException(400, "满减券请填写使用门槛");
                }
                if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BizException(400, "请填写减免金额");
                }
                if (discountAmount.compareTo(threshold) > 0) {
                    throw new BizException(400, "减免金额不能大于使用门槛");
                }
            }
            case TYPE_NO_THRESHOLD -> {
                if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BizException(400, "请填写立减金额");
                }
            }
            case TYPE_DISCOUNT -> {
                if (discountRate == null
                        || discountRate.compareTo(BigDecimal.ZERO) <= 0
                        || discountRate.compareTo(new BigDecimal("9.9")) > 0) {
                    throw new BizException(400, "折扣请填写 0.1~9.9 之间的数值，如 8.5 表示 8.5 折");
                }
            }
            default -> throw new BizException(400, "不支持的优惠券类型");
        }
    }

    private void enrichCoupon(Map<String, Object> r) {
        String type = str(r.get("couponType"), TYPE_FULL_REDUCE);
        r.put("couponTypeLabel", typeLabel(type));
        BigDecimal threshold = money(r.get("thresholdAmount"));
        BigDecimal amount = money(r.get("discountAmount"));
        BigDecimal rate = r.get("discountRate") == null ? null : money(r.get("discountRate"));
        r.put("ruleText", ruleText(type, threshold, amount, rate));
        r.put("thresholdText", TYPE_NO_THRESHOLD.equals(type)
                ? "无门槛"
                : (threshold.compareTo(BigDecimal.ZERO) <= 0 ? "无门槛" : "满¥" + threshold.stripTrailingZeros().toPlainString()));
        normalizeTime(r, "startTime");
        normalizeTime(r, "endTime");
        normalizeTime(r, "createTime");
        normalizeTime(r, "updateTime");
        String period = formatPeriod(r.get("startTime"), r.get("endTime"));
        r.put("validPeriod", period);

        int shelf = toInt(r.get("status"), 1);
        String biz = calcBizStatus(shelf, r.get("startTime"), r.get("endTime"));
        r.put("bizStatus", biz);
        r.put("statusLabel", bizLabel(biz));
        r.put("statusTagType", bizTag(biz));
        r.put("amountLocked", "ONGOING".equals(biz));
        r.put("canEdit", !"OFF".equals(biz) || shelf == 0); // 已下架也可查看编辑非金额字段
        r.put("canOffShelf", shelf == 1);
    }

    private boolean matchStatusFilter(Map<String, Object> r, String statusFilter) {
        if (!StringUtils.hasText(statusFilter) || "ALL".equalsIgnoreCase(statusFilter)) {
            return true;
        }
        return statusFilter.trim().equalsIgnoreCase(String.valueOf(r.get("bizStatus")));
    }

    private static String calcBizStatus(int shelf, Object startObj, Object endObj) {
        if (shelf != 1) return "OFF";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = parseTime(startObj);
        LocalDateTime end = parseTime(endObj);
        if (start != null && now.isBefore(start)) return "NOT_STARTED";
        if (end != null && now.isAfter(end)) return "ENDED";
        return "ONGOING";
    }

    private static String bizLabel(String biz) {
        return switch (biz) {
            case "ONGOING" -> "进行中";
            case "NOT_STARTED" -> "未开始";
            case "ENDED" -> "已结束";
            case "OFF" -> "已下架";
            default -> biz;
        };
    }

    private static String bizTag(String biz) {
        return switch (biz) {
            case "ONGOING" -> "success";
            case "NOT_STARTED" -> "warning";
            case "ENDED" -> "info";
            case "OFF" -> "danger";
            default -> "info";
        };
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case TYPE_FULL_REDUCE -> "满减券";
            case TYPE_NO_THRESHOLD -> "无门槛券";
            case TYPE_DISCOUNT -> "折扣券";
            default -> type;
        };
    }

    private static String ruleText(String type, BigDecimal threshold, BigDecimal amount, BigDecimal rate) {
        return switch (type) {
            case TYPE_FULL_REDUCE -> "满" + plain(threshold) + "减" + plain(amount);
            case TYPE_NO_THRESHOLD -> "立减" + plain(amount) + "元";
            case TYPE_DISCOUNT -> {
                String base = plain(rate) + "折";
                if (threshold != null && threshold.compareTo(BigDecimal.ZERO) > 0) {
                    yield "满" + plain(threshold) + "享" + base;
                }
                yield base;
            }
            default -> "-";
        };
    }

    private static String formatPeriod(Object start, Object end) {
        String s = start == null ? "" : String.valueOf(start);
        String e = end == null ? "" : String.valueOf(end);
        if (!StringUtils.hasText(s) && !StringUtils.hasText(e)) return "不限";
        if (!StringUtils.hasText(s)) return "至 " + shortTime(e);
        if (!StringUtils.hasText(e)) return shortTime(s) + " 起";
        return shortTime(s) + " ~ " + shortTime(e);
    }

    private static String shortTime(String t) {
        if (t == null) return "";
        String s = t.length() >= 16 ? t.substring(0, 16) : t;
        return s.replace('T', ' ');
    }

    private static String plain(BigDecimal v) {
        if (v == null) return "0";
        return v.stripTrailingZeros().toPlainString();
    }

    private Map<String, Object> requireCoupon(Long couponId, Long shopId) {
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "select id, tenant_id as tenantId, shop_id as shopId, name, cover_url as coverUrl, "
                            + "coupon_type as couponType, threshold_amount as thresholdAmount, "
                            + "discount_amount as discountAmount, discount_rate as discountRate, "
                            + "total_count as totalCount, claimed_count as claimedCount, "
                            + "limit_per_user as limitPerUser, start_time as startTime, end_time as endTime, "
                            + "remark, status, create_time as createTime, update_time as updateTime "
                            + "from store_coupon where id=? and shop_id=?",
                    couponId, shopId);
            assertTenantAccess(toLong(row.get("tenantId")));
            return row;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(404, "优惠券不存在");
        }
    }

    private static String normalizeType(String type) {
        if (!StringUtils.hasText(type)) return TYPE_FULL_REDUCE;
        String t = type.trim().toUpperCase(Locale.ROOT);
        if (List.of(TYPE_FULL_REDUCE, TYPE_NO_THRESHOLD, TYPE_DISCOUNT).contains(t)) return t;
        // 中文兼容
        return switch (type.trim()) {
            case "满减券" -> TYPE_FULL_REDUCE;
            case "无门槛券" -> TYPE_NO_THRESHOLD;
            case "折扣券" -> TYPE_DISCOUNT;
            default -> throw new BizException(400, "不支持的优惠券类型");
        };
    }

    private void assertMerchant() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isStoreMerchant(role)
                && !RoleConstants.isPropertyAdmin(role)
                && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "无权限");
        }
        if (RoleConstants.isStoreMerchant(role) && TenantContext.getShopId() == null) {
            throw new BizException(403, "缺少店铺上下文");
        }
    }

    private Long requireShopId() {
        if (RoleConstants.isStoreMerchant(TenantContext.getRole())) {
            Long sid = TenantContext.getShopId();
            if (sid == null) throw new BizException(403, "缺少店铺上下文 shopId");
            return sid;
        }
        throw new BizException(403, "仅商户可管理优惠券");
    }

    private Long requireTenant() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) throw new BizException(401, "未登录");
        return tid;
    }

    private void assertTenantAccess(Long tenantId) {
        Long cur = TenantContext.getTenantId();
        if (cur != null && tenantId != null && !Objects.equals(cur, tenantId)
                && !RoleConstants.isPlatformAdmin(TenantContext.getRole())) {
            throw new BizException(403, "租户不匹配");
        }
    }

    private static void normalizeTime(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Timestamp ts) {
            m.put(key, ts.toLocalDateTime().toString().replace('T', ' '));
        } else if (v instanceof LocalDateTime ldt) {
            m.put(key, ldt.toString().replace('T', ' '));
        }
    }

    private static LocalDateTime parseTime(Object v) {
        if (v == null) return null;
        if (v instanceof Timestamp ts) return ts.toLocalDateTime();
        if (v instanceof LocalDateTime ldt) return ldt;
        String s = String.valueOf(v).trim().replace('T', ' ');
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            if (s.length() == 16) s = s + ":00";
            if (s.length() > 19) s = s.substring(0, 19);
            return LocalDateTime.parse(s.replace(' ', 'T'));
        } catch (Exception e) {
            throw new BizException(400, "时间格式错误: " + v);
        }
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? def : s;
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private static int toInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static BigDecimal money(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b.setScale(2, RoundingMode.HALF_UP);
        try {
            return new BigDecimal(String.valueOf(v)).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
