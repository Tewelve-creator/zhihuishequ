package com.community.repair.store;

import cn.hutool.crypto.digest.DigestUtil;
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

import java.sql.Timestamp;
import java.util.*;

/**
 * 多商户商超：物业管店 + 商户端按 shop_id 强制隔离
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopMallService {

    private final JdbcTemplate jdbc;
    private final StoreNotifyService storeNotifyService;

    @PostConstruct
    public void ensure() {
        try {
            jdbc.execute("""
                    create table if not exists store_shop (
                      id bigint primary key auto_increment,
                      tenant_id bigint not null,
                      name varchar(128) not null,
                      contact_name varchar(64) null,
                      contact_phone varchar(32) null,
                      address varchar(255) null,
                      open_time varchar(16) default '08:00',
                      close_time varchar(16) default '22:00',
                      status tinyint not null default 1 comment '1启用 0停用',
                      owner_user_id bigint null,
                      login_phone varchar(32) null,
                      remark varchar(255) null,
                      deleted tinyint not null default 0,
                      create_time datetime null,
                      update_time datetime null,
                      key idx_ss_tenant (tenant_id, status),
                      key idx_ss_owner (owner_user_id)
                    ) engine=InnoDB default charset=utf8mb4
                    """);
            ensureColumn("store_product", "shop_id", "bigint null");
            ensureColumn("store_product", "original_price", "decimal(10,2) null");
            ensureColumn("store_product", "limit_buy", "int null default 0");
            ensureColumn("store_product", "stock_warn", "int null default 10");
            ensureColumn("store_category", "shop_id", "bigint null");
            ensureColumn("store_order", "shop_id", "bigint null");
            ensureColumn("store_shop", "avatar_url", "varchar(512) null");
            ensureColumn("store_shop", "cover_url", "varchar(512) null");
            ensureColumn("store_shop", "category_name", "varchar(64) null");
            ensureColumn("store_shop", "intro", "varchar(1000) null");
            ensureColumn("store_shop", "notify_new_order", "tinyint not null default 1");
            ensureColumn("store_shop", "notify_order_cancel", "tinyint not null default 1");
            ensureColumn("store_shop", "notify_platform_notice", "tinyint not null default 1");
            ensureColumn("store_shop", "notify_stock_low", "tinyint not null default 1");
            migrateLegacyShop();
        } catch (Exception e) {
            log.warn("store_shop schema skip: {}", e.getMessage());
        }
    }

    private void ensureColumn(String table, String col, String def) {
        try {
            Integer n = jdbc.queryForObject(
                    "select count(1) from information_schema.columns where table_schema=database() "
                            + "and table_name=? and column_name=?",
                    Integer.class, table, col);
            if (n == null || n == 0) {
                jdbc.execute("alter table " + table + " add column " + col + " " + def);
            }
        } catch (Exception e) {
            log.debug("ensure {}.{} skip: {}", table, col, e.getMessage());
        }
    }

    private void migrateLegacyShop() {
        try {
            List<Map<String, Object>> infos = jdbc.queryForList("select * from store_info");
            for (Map<String, Object> info : infos) {
                Long tid = toLong(info.get("tenant_id"));
                Integer n = jdbc.queryForObject(
                        "select count(1) from store_shop where tenant_id=? and deleted=0", Integer.class, tid);
                if (n != null && n > 0) continue;
                jdbc.update(
                        "insert into store_shop(tenant_id,name,contact_phone,address,open_time,close_time,status,"
                                + "remark,deleted,create_time,update_time) values(?,?,?,?,?,?,1,'系统迁移',0,now(),now())",
                        tid, info.get("name"), info.get("phone"), info.get("address"),
                        info.get("open_time"), info.get("close_time"));
                Long shopId = jdbc.queryForObject("select last_insert_id()", Long.class);
                jdbc.update("update store_product set shop_id=? where tenant_id=? and shop_id is null", shopId, tid);
                jdbc.update("update store_category set shop_id=? where tenant_id=? and shop_id is null", shopId, tid);
                jdbc.update("update store_order set shop_id=? where tenant_id=? and shop_id is null", shopId, tid);
            }
        } catch (Exception e) {
            log.debug("migrate legacy shop skip: {}", e.getMessage());
        }
    }

    /* ========== 物业：商户列表 ========== */

    public List<Map<String, Object>> listShops(String keyword, Integer status) {
        assertPropertyStaff();
        Long tid = requireTenant();
        // 把已入驻的 store_merchant 同步进 store_shop，避免「总览有、商户列表没有」
        syncApprovedMerchantsToShops(tid);
        StringBuilder sql = new StringBuilder(
                "select s.id as shopId, s.tenant_id as tenantId, s.name, s.contact_name as contactName, "
                        + "s.contact_phone as contactPhone, s.address, s.open_time as openTime, s.close_time as closeTime, "
                        + "s.status, s.owner_user_id as ownerUserId, s.login_phone as loginPhone, "
                        + "s.create_time as createTime, "
                        + "(select count(1) from store_product p where p.shop_id=s.id) as productCount, "
                        + "(select count(1) from store_order o where o.shop_id=s.id) as orderCount "
                        + "from store_shop s where s.tenant_id=? and s.deleted=0 ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (status != null) {
            sql.append("and s.status=? ");
            args.add(status);
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (s.name like ? or ifnull(s.login_phone,'') like ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append("order by s.id desc");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> r : rows) {
            normalizeTime(r, "createTime");
            r.put("statusLabel", toInt(r.get("status"), 1) == 1 ? "启用" : "停用");
        }
        return rows;
    }

    /**
     * 历史入驻商户补齐到商超店铺表（按小区+名称去重）
     */
    private void syncApprovedMerchantsToShops(Long tid) {
        if (tid == null) return;
        try {
            List<Map<String, Object>> merchants = jdbc.queryForList(
                    "select m.id, m.name, m.contact_name as contactName, m.contact_phone as contactPhone, "
                            + "(select a.address from store_merchant_apply a where a.merchant_id=m.id "
                            + "order by a.id desc limit 1) as address "
                            + "from store_merchant m where m.tenant_id=? and m.status=1 "
                            + "and ifnull(m.onboard_status,'APPROVED')='APPROVED'",
                    tid);
            for (Map<String, Object> m : merchants) {
                String name = str(m.get("name"), null);
                if (!StringUtils.hasText(name)) continue;
                Integer exist = jdbc.queryForObject(
                        "select count(1) from store_shop where tenant_id=? and name=? and deleted=0",
                        Integer.class, tid, name.trim());
                if (exist != null && exist > 0) continue;
                jdbc.update(
                        "insert into store_shop(tenant_id,name,contact_name,contact_phone,address,open_time,close_time,"
                                + "status,remark,deleted,create_time,update_time) "
                                + "values(?,?,?,?,?,'08:00','22:00',1,?,0,now(),now())",
                        tid, name.trim(),
                        str(m.get("contactName"), null),
                        str(m.get("contactPhone"), null),
                        str(m.get("address"), null),
                        "入驻同步#" + m.get("id"));
            }
        } catch (Exception e) {
            log.warn("syncApprovedMerchantsToShops fail: {}", e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> createShop(Map<String, Object> body) {
        assertPropertyAdmin();
        Long tid = requireTenant();
        String name = str(body.get("name"), null);
        String loginPhone = str(body.get("loginPhone"), str(body.get("username"), null));
        String password = str(body.get("password"), null);
        if (!StringUtils.hasText(name)) throw new BizException(400, "商户名称不能为空");
        if (!StringUtils.hasText(loginPhone)) throw new BizException(400, "登录账号不能为空");
        if (!StringUtils.hasText(password) || password.length() < 6) {
            throw new BizException(400, "登录密码至少 6 位");
        }
        Integer existUser = jdbc.queryForObject(
                "select count(1) from sys_user where phone=? and ifnull(deleted,0)=0", Integer.class, loginPhone);
        if (existUser != null && existUser > 0) {
            throw new BizException(400, "该登录账号已存在");
        }
        String pwd = DigestUtil.md5Hex(password);
        jdbc.update(
                "insert into sys_user(tenant_id,phone,password,nickname,role,status,create_time,deleted) "
                        + "values(?,?,?,?,?,1,now(),0)",
                tid, loginPhone, pwd, name + "店长", RoleConstants.STORE_MERCHANT);
        Long userId = jdbc.queryForObject("select last_insert_id()", Long.class);
        bindMerchantRole(userId);
        jdbc.update(
                "insert into store_shop(tenant_id,name,contact_name,contact_phone,address,open_time,close_time,"
                        + "status,owner_user_id,login_phone,remark,deleted,create_time,update_time) "
                        + "values(?,?,?,?,?,?,?,1,?,?,?,0,now(),now())",
                tid, name, str(body.get("contactName"), null),
                str(body.get("contactPhone"), loginPhone), str(body.get("address"), null),
                str(body.get("openTime"), "08:00"), str(body.get("closeTime"), "22:00"),
                userId, loginPhone, str(body.get("remark"), null));
        Long shopId = jdbc.queryForObject("select last_insert_id()", Long.class);
        return getShop(shopId);
    }

    @Transactional
    public Map<String, Object> updateShopStatus(Long shopId, int status) {
        assertPropertyAdmin();
        Map<String, Object> shop = requireShopInTenant(shopId);
        jdbc.update("update store_shop set status=?, update_time=now() where id=?", status, shopId);
        Long ownerId = toLong(shop.get("owner_user_id"));
        if (ownerId != null) {
            // 停用商户账号，启用时同步恢复
            jdbc.update("update sys_user set status=? where id=?", status == 1 ? 1 : 0, ownerId);
        }
        return getShop(shopId);
    }

    @Transactional
    public Map<String, Object> resetShopPassword(Long shopId, String password) {
        assertPropertyAdmin();
        Map<String, Object> shop = requireShopInTenant(shopId);
        if (!StringUtils.hasText(password) || password.length() < 6) {
            throw new BizException(400, "密码至少 6 位");
        }
        Long ownerId = toLong(shop.get("owner_user_id"));
        if (ownerId == null) throw new BizException(400, "商户未绑定登录账号，请先设置账号");
        jdbc.update("update sys_user set password=? where id=?", DigestUtil.md5Hex(password), ownerId);
        Map<String, Object> m = getShop(shopId);
        m.put("passwordReset", true);
        return m;
    }

    /**
     * 为历史迁移等无账号店铺绑定登录账号；已有账号时可改绑手机号并可选改密。
     */
    @Transactional
    public Map<String, Object> bindShopAccount(Long shopId, Map<String, Object> body) {
        assertPropertyAdmin();
        Map<String, Object> shop = requireShopInTenant(shopId);
        Long tid = requireTenant();
        String loginPhone = str(body.get("loginPhone"), str(body.get("username"), null));
        String password = str(body.get("password"), null);
        if (!StringUtils.hasText(loginPhone)) throw new BizException(400, "登录账号不能为空");
        Long ownerId = toLong(shop.get("owner_user_id"));
        if (ownerId == null) {
            if (!StringUtils.hasText(password) || password.length() < 6) {
                throw new BizException(400, "首次设置账号时密码至少 6 位");
            }
            Integer existUser = jdbc.queryForObject(
                    "select count(1) from sys_user where phone=? and ifnull(deleted,0)=0", Integer.class, loginPhone);
            if (existUser != null && existUser > 0) {
                throw new BizException(400, "该登录账号已存在");
            }
            String shopName = str(shop.get("name"), "商户");
            jdbc.update(
                    "insert into sys_user(tenant_id,phone,password,nickname,role,status,create_time,deleted) "
                            + "values(?,?,?,?,?,1,now(),0)",
                    tid, loginPhone, DigestUtil.md5Hex(password), shopName + "店长", RoleConstants.STORE_MERCHANT);
            ownerId = jdbc.queryForObject("select last_insert_id()", Long.class);
            bindMerchantRole(ownerId);
            jdbc.update(
                    "update store_shop set owner_user_id=?, login_phone=?, update_time=now() where id=?",
                    ownerId, loginPhone, shopId);
        } else {
            Integer dup = jdbc.queryForObject(
                    "select count(1) from sys_user where phone=? and id<>? and ifnull(deleted,0)=0",
                    Integer.class, loginPhone, ownerId);
            if (dup != null && dup > 0) {
                throw new BizException(400, "该登录账号已存在");
            }
            if (StringUtils.hasText(password)) {
                if (password.length() < 6) throw new BizException(400, "密码至少 6 位");
                jdbc.update("update sys_user set phone=?, password=? where id=?",
                        loginPhone, DigestUtil.md5Hex(password), ownerId);
            } else {
                jdbc.update("update sys_user set phone=? where id=?", loginPhone, ownerId);
            }
            jdbc.update("update store_shop set login_phone=?, update_time=now() where id=?", loginPhone, shopId);
        }
        return getShop(shopId);
    }

    private void bindMerchantRole(Long userId) {
        try {
            Integer roleCnt = jdbc.queryForObject(
                    "select count(1) from sys_role where role_code=?", Integer.class, RoleConstants.STORE_MERCHANT);
            if (roleCnt == null || roleCnt == 0) {
                jdbc.update(
                        "insert into sys_role(role_name,role_code,remark) values('商超商户',?,'社区商超独立商户后台')",
                        RoleConstants.STORE_MERCHANT);
            }
            Long roleId = jdbc.queryForObject(
                    "select id from sys_role where role_code=? limit 1", Long.class, RoleConstants.STORE_MERCHANT);
            if (roleId != null) {
                Integer bound = jdbc.queryForObject(
                        "select count(1) from sys_user_role where user_id=? and role_id=?",
                        Integer.class, userId, roleId);
                if (bound == null || bound == 0) {
                    jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)", userId, roleId);
                }
            }
        } catch (Exception e) {
            log.debug("bind store merchant role skip: {}", e.getMessage());
        }
    }

    public Map<String, Object> getShop(Long shopId) {
        assertPropertyOrMerchant();
        Map<String, Object> shop = requireShopAccess(shopId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shopId", shop.get("id"));
        m.put("tenantId", shop.get("tenant_id"));
        m.put("name", shop.get("name"));
        m.put("contactName", shop.get("contact_name"));
        m.put("contactPhone", shop.get("contact_phone"));
        m.put("address", shop.get("address"));
        m.put("openTime", shop.get("open_time"));
        m.put("closeTime", shop.get("close_time"));
        m.put("status", shop.get("status"));
        m.put("statusLabel", toInt(shop.get("status"), 1) == 1 ? "启用" : "停用");
        m.put("loginPhone", shop.get("login_phone"));
        m.put("ownerUserId", shop.get("owner_user_id"));
        m.put("avatarUrl", shop.get("avatar_url"));
        m.put("coverUrl", shop.get("cover_url"));
        m.put("categoryName", shop.get("category_name"));
        m.put("intro", shop.get("intro"));
        m.put("remark", shop.get("remark"));
        m.put("notifyNewOrder", toInt(shop.get("notify_new_order"), 1) == 1);
        m.put("notifyOrderCancel", toInt(shop.get("notify_order_cancel"), 1) == 1);
        m.put("notifyPlatformNotice", toInt(shop.get("notify_platform_notice"), 1) == 1);
        m.put("notifyStockLow", toInt(shop.get("notify_stock_low"), 1) == 1);
        return m;
    }

    /* ========== 商户端：本店信息 ========== */

    public Map<String, Object> myShop() {
        assertMerchant();
        return getShop(requireShopId());
    }

    @Transactional
    public Map<String, Object> updateMyNotifySettings(Map<String, Object> body) {
        assertMerchant();
        Long shopId = requireShopId();
        Map<String, Object> old = requireShopAccess(shopId);
        int newOrder = body.containsKey("notifyNewOrder")
                ? bool01(body.get("notifyNewOrder"), true)
                : toInt(old.get("notify_new_order"), 1);
        int orderCancel = body.containsKey("notifyOrderCancel")
                ? bool01(body.get("notifyOrderCancel"), true)
                : toInt(old.get("notify_order_cancel"), 1);
        int platformNotice = body.containsKey("notifyPlatformNotice")
                ? bool01(body.get("notifyPlatformNotice"), true)
                : toInt(old.get("notify_platform_notice"), 1);
        int stockLow = body.containsKey("notifyStockLow")
                ? bool01(body.get("notifyStockLow"), true)
                : toInt(old.get("notify_stock_low"), 1);
        jdbc.update(
                "update store_shop set notify_new_order=?, notify_order_cancel=?, notify_platform_notice=?, "
                        + "notify_stock_low=?, update_time=now() where id=?",
                newOrder, orderCancel, platformNotice, stockLow, shopId);
        return myShop();
    }

    @Transactional
    public Map<String, Object> updateMyShop(Map<String, Object> body) {
        assertMerchant();
        Long shopId = requireShopId();
        Map<String, Object> old = requireShopAccess(shopId);
        String avatar = str(body.get("avatarUrl"), null);
        String cover = str(body.get("coverUrl"), null);
        if (!StringUtils.hasText(avatar)) {
            avatar = str(old.get("avatar_url"), null);
        }
        if (!StringUtils.hasText(cover)) {
            cover = str(old.get("cover_url"), null);
        }
        String category = str(body.get("categoryName"), null);
        if (!StringUtils.hasText(category)) {
            category = str(old.get("category_name"), null);
        }
        String intro = str(body.get("intro"), null);
        if (!StringUtils.hasText(intro)) {
            intro = str(old.get("intro"), null);
        }
        jdbc.update(
                "update store_shop set name=?, contact_name=?, contact_phone=?, address=?, open_time=?, close_time=?, "
                        + "avatar_url=?, cover_url=?, category_name=?, intro=?, update_time=now() where id=?",
                str(body.get("name"), null), str(body.get("contactName"), null),
                str(body.get("contactPhone"), null), str(body.get("address"), null),
                str(body.get("openTime"), "08:00"), str(body.get("closeTime"), "22:00"),
                avatar, cover, category, intro, shopId);
        return myShop();
    }

    /** 商户端收入对账：强制本店 shop_id */
    public Map<String, Object> myRevenue(String startDate, String endDate) {
        assertMerchant();
        Long shopId = requireShopId();
        requireShopAccess(shopId);
        Long tid = requireTenant();

        StringBuilder where = new StringBuilder(" where tenant_id=? and shop_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        args.add(shopId);
        if (StringUtils.hasText(startDate)) {
            where.append(" and ifnull(pay_time, create_time) >= ? ");
            args.add(java.sql.Timestamp.valueOf(
                    java.time.LocalDate.parse(startDate.trim().substring(0, Math.min(10, startDate.trim().length())))
                            .atStartOfDay()));
        }
        if (StringUtils.hasText(endDate)) {
            where.append(" and ifnull(pay_time, create_time) < ? ");
            args.add(java.sql.Timestamp.valueOf(
                    java.time.LocalDate.parse(endDate.trim().substring(0, Math.min(10, endDate.trim().length())))
                            .plusDays(1).atStartOfDay()));
        }

        // 销售额：已付款且未取消的订单（含进行中与已完成）
        String salesWhere = where + " and status in ('PENDING_ACCEPT','PREPARING','DELIVERING','COMPLETED') ";
        Integer orderCount = jdbc.queryForObject("select count(1) from store_order" + salesWhere, Integer.class, args.toArray());
        java.math.BigDecimal salesAmount = jdbc.queryForObject(
                "select ifnull(sum(pay_amount),0) from store_order" + salesWhere, java.math.BigDecimal.class, args.toArray());
        Integer completedCount = jdbc.queryForObject(
                "select count(1) from store_order" + where + " and status='COMPLETED'", Integer.class, args.toArray());
        java.math.BigDecimal completedAmount = jdbc.queryForObject(
                "select ifnull(sum(pay_amount),0) from store_order" + where + " and status='COMPLETED'",
                java.math.BigDecimal.class, args.toArray());
        Integer refundCount = jdbc.queryForObject(
                "select count(1) from store_order" + where + " and status='REFUNDED'", Integer.class, args.toArray());
        java.math.BigDecimal refundAmount = jdbc.queryForObject(
                "select ifnull(sum(ifnull(refund_amount, pay_amount)),0) from store_order" + where + " and status='REFUNDED'",
                java.math.BigDecimal.class, args.toArray());

        List<Map<String, Object>> orders = jdbc.queryForList(
                "select id, order_no as orderNo, status, pay_amount as payAmount, goods_amount as goodsAmount, "
                        + "receiver_name as receiverName, receiver_phone as receiverPhone, "
                        + "create_time as createTime, pay_time as payTime "
                        + "from store_order" + where
                        + " and status in ('PENDING_ACCEPT','PREPARING','DELIVERING','COMPLETED','REFUNDED') "
                        + "order by id desc limit 500",
                args.toArray());
        for (Map<String, Object> r : orders) {
            normalizeTime(r, "createTime");
            normalizeTime(r, "payTime");
            r.put("statusLabel", orderStatusLabel(String.valueOf(r.get("status"))));
        }

        Map<String, Object> shop = requireShopInTenant(shopId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shopId", shopId);
        m.put("shopName", shop.get("name"));
        m.put("startDate", startDate);
        m.put("endDate", endDate);
        m.put("orderCount", orderCount == null ? 0 : orderCount);
        m.put("salesAmount", salesAmount == null ? java.math.BigDecimal.ZERO : salesAmount);
        m.put("completedCount", completedCount == null ? 0 : completedCount);
        m.put("completedAmount", completedAmount == null ? java.math.BigDecimal.ZERO : completedAmount);
        m.put("refundCount", refundCount == null ? 0 : refundCount);
        m.put("refundAmount", refundAmount == null ? java.math.BigDecimal.ZERO : refundAmount);
        m.put("orders", orders);
        return m;
    }

    /* ========== 商品（按 shop_id 隔离） ========== */

    public List<Map<String, Object>> listProducts(Long shopId, String keyword, Boolean onSaleOnly,
                                                  Long categoryId, Integer status) {
        assertPropertyOrMerchant();
        Long sid = resolveShopFilter(shopId);
        Long tid = requireTenant();
        StringBuilder sql = new StringBuilder(
                "select p.id, p.shop_id as shopId, p.tenant_id as tenantId, p.category_id as categoryId, "
                        + "p.name, p.cover_url as coverUrl, p.price, p.original_price as originalPrice, "
                        + "p.stock, p.limit_buy as limitBuy, ifnull(p.stock_warn,10) as stockWarn, "
                        + "p.sales, p.status, p.sort_no as sortNo, p.remark, "
                        + "p.create_time as createTime, p.update_time as updateTime, "
                        + "c.name as categoryName, s.name as shopName "
                        + "from store_product p "
                        + "left join store_category c on c.id=p.category_id "
                        + "left join store_shop s on s.id=p.shop_id "
                        + "where p.tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (sid != null) {
            sql.append("and p.shop_id=? ");
            args.add(sid);
        }
        if (categoryId != null && categoryId > 0) {
            sql.append("and p.category_id=? ");
            args.add(categoryId);
        }
        if (status != null) {
            sql.append("and p.status=? ");
            args.add(status);
        } else if (Boolean.TRUE.equals(onSaleOnly)) {
            sql.append("and p.status=1 ");
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and p.name like ? ");
            args.add("%" + keyword.trim() + "%");
        }
        sql.append("order by p.sort_no asc, p.id desc limit 1000");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> r : rows) {
            int st = toInt(r.get("status"), 1);
            r.put("statusLabel", st == 1 ? "上架" : "下架");
            int stock = toInt(r.get("stock"), 0);
            int warn = toInt(r.get("stockWarn"), 10);
            r.put("stockLow", stock < warn);
            normalizeTime(r, "createTime");
            normalizeTime(r, "updateTime");
            if (r.get("originalPrice") == null) {
                r.put("originalPrice", r.get("price"));
            }
            if (r.get("limitBuy") == null) {
                r.put("limitBuy", 0);
            }
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> saveProduct(Map<String, Object> body) {
        assertPropertyOrMerchantWrite();
        Long tid = requireTenant();
        Long shopId = resolveShopFilter(toLong(body.get("shopId")));
        if (shopId == null) throw new BizException(400, "缺少 shopId");
        requireShopAccess(shopId);
        String name = str(body.get("name"), null);
        if (!StringUtils.hasText(name)) throw new BizException(400, "商品名称不能为空");
        Long id = toLong(body.get("id"));
        Long categoryId = toLong(body.get("categoryId"));
        String cover = str(body.get("coverUrl"), null);
        java.math.BigDecimal price = toDec(body.get("price"));
        java.math.BigDecimal originalPrice = body.get("originalPrice") == null ? price : toDec(body.get("originalPrice"));
        int stock = toInt(body.get("stock"), 0);
        int limitBuy = toInt(body.get("limitBuy"), 0);
        int stockWarn = toInt(body.get("stockWarn"), 10);
        int status = toInt(body.get("status"), 1);
        int sortNo = toInt(body.get("sortNo"), 0);
        String remark = str(body.get("remark"), null);
        if (id != null && id > 0) {
            assertProductShop(id, shopId);
            jdbc.update(
                    "update store_product set category_id=?, name=?, cover_url=?, price=?, original_price=?, "
                            + "stock=?, limit_buy=?, stock_warn=?, status=?, sort_no=?, remark=?, update_time=now() "
                            + "where id=? and shop_id=?",
                    categoryId, name, cover, price, originalPrice, stock, limitBuy, stockWarn,
                    status, sortNo, remark, id, shopId);
        } else {
            jdbc.update(
                    "insert into store_product(tenant_id,shop_id,category_id,name,cover_url,price,original_price,"
                            + "stock,limit_buy,stock_warn,sales,status,sort_no,remark,create_time,update_time) "
                            + "values(?,?,?,?,?,?,?,?,?,?,0,?,?,?,now(),now())",
                    tid, shopId, categoryId, name, cover, price, originalPrice, stock, limitBuy, stockWarn,
                    status, sortNo, remark);
            id = jdbc.queryForObject("select last_insert_id()", Long.class);
        }
        List<Map<String, Object>> list = listProducts(shopId, null, null, null, null);
        Long finalId = id;
        return list.stream().filter(p -> Objects.equals(toLong(p.get("id")), finalId)).findFirst()
                .orElseThrow(() -> new BizException(500, "保存失败"));
    }

    /** 批量下架 / 上架 */
    @Transactional
    public int batchUpdateStatus(List<Long> ids, int status) {
        assertPropertyOrMerchantWrite();
        if (ids == null || ids.isEmpty()) {
            throw new BizException(400, "请选择商品");
        }
        Long shopId = requireShopIdIfMerchant();
        int n = 0;
        for (Long id : ids) {
            if (id == null || id <= 0) continue;
            if (shopId != null) {
                assertProductShop(id, shopId);
                n += jdbc.update("update store_product set status=?, update_time=now() where id=? and shop_id=?",
                        status, id, shopId);
            } else {
                n += jdbc.update("update store_product set status=?, update_time=now() where id=? and tenant_id=?",
                        status, id, requireTenant());
            }
        }
        return n;
    }

    /** 批量修改库存（设为指定值） */
    @Transactional
    public int batchUpdateStock(List<Long> ids, int stock) {
        assertPropertyOrMerchantWrite();
        if (ids == null || ids.isEmpty()) {
            throw new BizException(400, "请选择商品");
        }
        if (stock < 0) {
            throw new BizException(400, "库存不能为负数");
        }
        Long shopId = requireShopIdIfMerchant();
        int n = 0;
        for (Long id : ids) {
            if (id == null || id <= 0) continue;
            if (shopId != null) {
                assertProductShop(id, shopId);
                n += jdbc.update("update store_product set stock=?, update_time=now() where id=? and shop_id=?",
                        stock, id, shopId);
            } else {
                n += jdbc.update("update store_product set stock=?, update_time=now() where id=? and tenant_id=?",
                        stock, id, requireTenant());
            }
        }
        return n;
    }

    public void deleteProduct(Long productId) {
        assertPropertyOrMerchantWrite();
        Long shopId = requireShopIdIfMerchant();
        if (shopId != null) {
            assertProductShop(productId, shopId);
            jdbc.update("delete from store_product where id=? and shop_id=?", productId, shopId);
        } else {
            Long tid = requireTenant();
            jdbc.update("delete from store_product where id=? and tenant_id=?", productId, tid);
        }
    }

    public List<Map<String, Object>> listCategories(Long shopId) {
        assertPropertyOrMerchant();
        Long sid = resolveShopFilter(shopId);
        Long tid = requireTenant();
        if (sid != null) {
            return jdbc.queryForList(
                    "select id, shop_id as shopId, name, sort_no as sortNo, status from store_category "
                            + "where tenant_id=? and shop_id=? order by sort_no, id",
                    tid, sid);
        }
        return jdbc.queryForList(
                "select id, shop_id as shopId, name, sort_no as sortNo, status from store_category "
                        + "where tenant_id=? order by sort_no, id",
                tid);
    }

    @Transactional
    public Map<String, Object> saveCategory(Map<String, Object> body) {
        assertPropertyOrMerchantWrite();
        Long tid = requireTenant();
        Long shopId = resolveShopFilter(toLong(body.get("shopId")));
        if (shopId == null) throw new BizException(400, "缺少 shopId");
        requireShopAccess(shopId);
        String name = str(body.get("name"), null);
        if (!StringUtils.hasText(name)) throw new BizException(400, "分类名称不能为空");
        Long id = toLong(body.get("id"));
        int sortNo = toInt(body.get("sortNo"), 0);
        if (id != null && id > 0) {
            jdbc.update("update store_category set name=?, sort_no=? where id=? and shop_id=?",
                    name, sortNo, id, shopId);
        } else {
            jdbc.update(
                    "insert into store_category(tenant_id,shop_id,name,sort_no,status,create_time) values(?,?,?,?,1,now())",
                    tid, shopId, name, sortNo);
            id = jdbc.queryForObject("select last_insert_id()", Long.class);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("shopId", shopId);
        m.put("name", name);
        m.put("sortNo", sortNo);
        return m;
    }

    /* ========== 订单 ========== */

    public List<Map<String, Object>> listOrders(Long shopId, String status, String keyword) {
        assertPropertyOrMerchant();
        Long tid = requireTenant();
        Long sid = resolveShopFilter(shopId);
        StringBuilder sql = new StringBuilder(
                "select o.id, o.shop_id as shopId, o.tenant_id as tenantId, o.order_no as orderNo, o.user_id as userId, "
                        + "o.status, o.pay_amount as payAmount, o.goods_amount as goodsAmount, "
                        + "o.receiver_name as receiverName, o.receiver_phone as receiverPhone, "
                        + "o.create_time as createTime, o.pay_time as payTime, s.name as shopName "
                        + "from store_order o left join store_shop s on s.id=o.shop_id "
                        + "where o.tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (sid != null) {
            sql.append("and o.shop_id=? ");
            args.add(sid);
        }
        if (StringUtils.hasText(status)) {
            sql.append("and o.status=? ");
            args.add(status.trim());
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (o.order_no like ? or ifnull(o.receiver_name,'') like ? or ifnull(s.name,'') like ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append("order by o.id desc limit 500");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> r : rows) {
            normalizeTime(r, "createTime");
            normalizeTime(r, "payTime");
            r.put("statusLabel", orderStatusLabel(String.valueOf(r.get("status"))));
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> updateOrderStatus(Long orderId, String action) {
        assertPropertyOrMerchantWrite();
        Map<String, Object> order;
        try {
            order = jdbc.queryForMap("select * from store_order where id=?", orderId);
        } catch (Exception e) {
            throw new BizException(404, "订单不存在");
        }
        Long shopId = toLong(order.get("shop_id"));
        if (RoleConstants.isStoreMerchant(TenantContext.getRole())) {
            if (!Objects.equals(shopId, requireShopId())) {
                throw new BizException(403, "无权操作其他店铺订单");
            }
        } else {
            assertTenantAccess(toLong(order.get("tenant_id")));
        }
        String next = mapAction(String.valueOf(order.get("status")), action);
        jdbc.update("update store_order set status=?, update_time=now() where id=?", next, orderId);
        if ("CANCELLED".equals(next)) {
            storeNotifyService.notifyOrderCancel(
                    toLong(order.get("tenant_id")),
                    shopId,
                    str(order.get("order_no"), null),
                    "商家取消");
        }
        List<Map<String, Object>> list = listOrders(shopId, null, null);
        return list.stream().filter(o -> Objects.equals(toLong(o.get("id")), orderId)).findFirst()
                .orElseThrow(() -> new BizException(500, "更新失败"));
    }

    private String mapAction(String cur, String action) {
        if (!StringUtils.hasText(action)) throw new BizException(400, "action 不能为空");
        String a = action.trim().toUpperCase(Locale.ROOT);
        return switch (a) {
            case "ACCEPT" -> "PREPARING";
            case "DELIVER" -> "DELIVERING";
            case "COMPLETE" -> "COMPLETED";
            case "CANCEL" -> "CANCELLED";
            default -> {
                if (List.of("PENDING_ACCEPT", "PREPARING", "DELIVERING", "COMPLETED", "CANCELLED").contains(a)) {
                    yield a;
                }
                throw new BizException(400, "不支持的操作: " + action);
            }
        };
    }

    /* ========== helpers ========== */

    private Long resolveShopFilter(Long requestedShopId) {
        if (RoleConstants.isStoreMerchant(TenantContext.getRole())) {
            return requireShopId();
        }
        return requestedShopId;
    }

    private Long requireShopIdIfMerchant() {
        if (RoleConstants.isStoreMerchant(TenantContext.getRole())) {
            return requireShopId();
        }
        return null;
    }

    private Long requireShopId() {
        Long sid = TenantContext.getShopId();
        if (sid == null) throw new BizException(403, "缺少店铺上下文 shopId");
        return sid;
    }

    private Map<String, Object> requireShopInTenant(Long shopId) {
        Map<String, Object> shop;
        try {
            shop = jdbc.queryForMap("select * from store_shop where id=? and deleted=0", shopId);
        } catch (Exception e) {
            throw new BizException(404, "商户不存在");
        }
        assertTenantAccess(toLong(shop.get("tenant_id")));
        return shop;
    }

    private Map<String, Object> requireShopAccess(Long shopId) {
        Map<String, Object> shop = requireShopInTenant(shopId);
        if (RoleConstants.isStoreMerchant(TenantContext.getRole())) {
            if (!Objects.equals(toLong(shop.get("id")), requireShopId())) {
                throw new BizException(403, "无权访问其他店铺");
            }
        }
        return shop;
    }

    private void assertProductShop(Long productId, Long shopId) {
        Integer n = jdbc.queryForObject(
                "select count(1) from store_product where id=? and shop_id=?", Integer.class, productId, shopId);
        if (n == null || n == 0) throw new BizException(403, "商品不属于本店");
    }

    private void assertTenantAccess(Long tenantId) {
        if (RoleConstants.isPlatformAdmin(TenantContext.getRole())) return;
        Long tid = TenantContext.getTenantId();
        if (tid == null || !Objects.equals(tid, tenantId)) {
            throw new BizException(403, "无权访问其他小区数据");
        }
    }

    private Long requireTenant() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) throw new BizException(400, "缺少社区租户");
        return tid;
    }

    private void assertPropertyStaff() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isTenantStaff(role) && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业人员可查看商户列表");
        }
    }

    private void assertPropertyAdmin() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isPropertyAdmin(role) && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业管理员可操作");
        }
    }

    private void assertMerchant() {
        if (!RoleConstants.isStoreMerchant(TenantContext.getRole())) {
            throw new BizException(403, "仅商户可操作");
        }
    }

    private void assertPropertyOrMerchant() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isTenantStaff(role) && !RoleConstants.isPlatformAdmin(role)
                && !RoleConstants.isStoreMerchant(role)) {
            throw new BizException(403, "无权限");
        }
    }

    private void assertPropertyOrMerchantWrite() {
        String role = TenantContext.getRole();
        if (RoleConstants.isStoreMerchant(role)) return;
        if (RoleConstants.isPropertyAdmin(role) || RoleConstants.isPlatformAdmin(role)) return;
        throw new BizException(403, "无写权限");
    }

    private static String orderStatusLabel(String st) {
        if (st == null) return "-";
        return switch (st) {
            case "UNPAID" -> "待支付";
            case "PENDING_ACCEPT" -> "待接单";
            case "PREPARING" -> "备货中";
            case "DELIVERING" -> "配送中";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            case "REFUNDED" -> "已退款";
            default -> st;
        };
    }

    private static void normalizeTime(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Timestamp ts) {
            m.put(key, ts.toLocalDateTime().toString().replace('T', ' '));
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

    private static int bool01(Object v, boolean def) {
        if (v == null) return def ? 1 : 0;
        if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
        String s = String.valueOf(v).trim().toLowerCase();
        if ("1".equals(s) || "true".equals(s) || "yes".equals(s) || "on".equals(s)) return 1;
        if ("0".equals(s) || "false".equals(s) || "no".equals(s) || "off".equals(s)) return 0;
        return def ? 1 : 0;
    }

    private static java.math.BigDecimal toDec(Object v) {
        if (v == null) return java.math.BigDecimal.ZERO;
        if (v instanceof java.math.BigDecimal b) return b;
        try {
            return new java.math.BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return java.math.BigDecimal.ZERO;
        }
    }
}
