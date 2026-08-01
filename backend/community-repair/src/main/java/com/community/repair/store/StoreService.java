package com.community.repair.store;

import com.community.common.constant.RoleConstants;
import com.community.common.exception.BizException;
import com.community.common.tenant.TenantContext;
import com.community.repair.service.AlipaySandboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreService {

    public static final String ST_UNPAID = "UNPAID";
    public static final String ST_PENDING = "PENDING_ACCEPT";
    public static final String ST_PREPARING = "PREPARING";
    public static final String ST_DELIVERING = "DELIVERING";
    public static final String ST_COMPLETED = "COMPLETED";
    public static final String ST_CANCELLED = "CANCELLED";
    public static final String ST_REFUNDED = "REFUNDED";

    public static final String DELIVERY = "DELIVERY";
    public static final String PICKUP = "PICKUP";

    private static final int PAY_EXPIRE_MINUTES = 30;

    private final JdbcTemplate jdbc;
    private final StoreNotifyService storeNotifyService;
    private final AlipaySandboxService alipaySandboxService;
    private final ShopCouponService shopCouponService;

    /* ========== 店铺 ========== */
    public Map<String, Object> getStore() {
        Long tid = requireTenant();
        ensureStoreExtColumns();
        List<Map<String, Object>> list = jdbc.queryForList(
                "select * from store_info where tenant_id=? limit 1", tid);
        if (list.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("name", "社区超市");
            empty.put("minOrderAmount", 0);
            empty.put("deliveryFee", 0);
            empty.put("commissionRate", 5);
            empty.put("status", 0);
            return empty;
        }
        Map<String, Object> m = camel(list.get(0));
        if (m.get("commissionRate") == null) m.put("commissionRate", 5);
        return m;
    }

    /** 居民：营业中的商户列表（封面/头像/简介） */
    public List<Map<String, Object>> listOpenShops() {
        Long tid = requireTenant();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id as shopId, name, address, open_time as openTime, close_time as closeTime, "
                        + "avatar_url as avatarUrl, cover_url as coverUrl, category_name as categoryName, "
                        + "intro, contact_phone as contactPhone, status, "
                        + "(select count(1) from store_product p where p.shop_id=s.id and p.status=1) as productCount "
                        + "from store_shop s where tenant_id=? and deleted=0 and status=1 "
                        + "order by id asc",
                tid);
        for (Map<String, Object> r : rows) {
            if (!StringUtils.hasText(str(r.get("intro")))) {
                r.put("intro", "本社区优质商户，欢迎选购");
            }
            if (!StringUtils.hasText(str(r.get("categoryName")))) {
                r.put("categoryName", "社区商超");
            }
        }
        return rows;
    }

    /** 居民：店铺详情 */
    public Map<String, Object> getOpenShop(Long shopId) {
        Long tid = requireTenant();
        try {
            Map<String, Object> shop = jdbc.queryForMap(
                    "select * from store_shop where id=? and tenant_id=? and deleted=0", shopId, tid);
            if (toInt(shop.get("status"), 0) != 1) {
                throw new BizException(400, "店铺暂停营业");
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("shopId", shop.get("id"));
            m.put("name", shop.get("name"));
            m.put("address", shop.get("address"));
            m.put("openTime", shop.get("open_time"));
            m.put("closeTime", shop.get("close_time"));
            m.put("avatarUrl", shop.get("avatar_url"));
            m.put("coverUrl", shop.get("cover_url"));
            m.put("categoryName", shop.get("category_name"));
            m.put("intro", shop.get("intro"));
            m.put("contactPhone", shop.get("contact_phone"));
            m.put("minOrderAmount", 0);
            m.put("deliveryFee", 0);
            if (!StringUtils.hasText(str(m.get("intro")))) {
                m.put("intro", "本社区优质商户，欢迎选购");
            }
            if (!StringUtils.hasText(str(m.get("categoryName")))) {
                m.put("categoryName", "社区商超");
            }
            // 合并旧版 store_info 的配送规则（如有）
            try {
                Map<String, Object> info = getStore();
                if (info.get("minOrderAmount") != null) m.put("minOrderAmount", info.get("minOrderAmount"));
                if (info.get("deliveryFee") != null) m.put("deliveryFee", info.get("deliveryFee"));
            } catch (Exception ignored) {
            }
            return m;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(404, "店铺不存在");
        }
    }

    public List<Map<String, Object>> listShopCategories(Long shopId) {
        requireTenant();
        getOpenShop(shopId); // 校验营业
        return mapList(jdbc.queryForList(
                "select * from store_category where tenant_id=? and shop_id=? and status=1 "
                        + "order by sort_no asc, id asc",
                requireTenant(), shopId));
    }

    public List<Map<String, Object>> listShopProducts(Long shopId, Long categoryId, String keyword) {
        requireTenant();
        getOpenShop(shopId);
        StringBuilder sql = new StringBuilder(
                "select p.*, c.name as category_name from store_product p "
                        + "left join store_category c on c.id=p.category_id "
                        + "where p.tenant_id=? and p.shop_id=? and p.status=1 ");
        List<Object> args = new ArrayList<>();
        args.add(requireTenant());
        args.add(shopId);
        if (categoryId != null) {
            sql.append("and p.category_id=? ");
            args.add(categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and p.name like ? ");
            args.add("%" + keyword.trim() + "%");
        }
        sql.append("order by p.sort_no asc, p.id desc");
        List<Map<String, Object>> list = mapList(jdbc.queryForList(sql.toString(), args.toArray()));
        for (Map<String, Object> r : list) {
            if (r.get("originalPrice") == null && r.get("original_price") == null) {
                r.put("originalPrice", r.get("price"));
            }
        }
        return list;
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

    @Transactional
    public Map<String, Object> saveStore(Map<String, Object> body) {
        assertAdmin();
        Long tid = requireTenant();
        ensureStoreExtColumns();
        String name = str(body.get("name"));
        if (!StringUtils.hasText(name)) throw new BizException("请填写店铺名称");
        BigDecimal min = money(body.get("minOrderAmount"), body.get("min_order_amount"));
        BigDecimal fee = money(body.get("deliveryFee"), body.get("delivery_fee"));
        BigDecimal commission = money(body.get("commissionRate"), body.get("commission_rate"));
        if (commission.compareTo(BigDecimal.ZERO) < 0) commission = BigDecimal.ZERO;
        if (commission.compareTo(new BigDecimal("100")) > 0) commission = new BigDecimal("100");
        Integer status = body.get("status") == null ? 1 : Integer.parseInt(String.valueOf(body.get("status")));
        List<Map<String, Object>> exist = jdbc.queryForList("select id from store_info where tenant_id=?", tid);
        if (exist.isEmpty()) {
            jdbc.update("insert into store_info(tenant_id,name,logo_url,address,phone,open_time,close_time,"
                            + "min_order_amount,delivery_fee,commission_rate,status,remark) values(?,?,?,?,?,?,?,?,?,?,?,?)",
                    tid, name, body.get("logoUrl"), body.get("address"), body.get("phone"),
                    nz(body.get("openTime"), "08:00"), nz(body.get("closeTime"), "22:00"),
                    min, fee, commission, status, body.get("remark"));
        } else {
            jdbc.update("update store_info set name=?, logo_url=?, address=?, phone=?, open_time=?, close_time=?,"
                            + "min_order_amount=?, delivery_fee=?, commission_rate=?, status=?, remark=? where tenant_id=?",
                    name, body.get("logoUrl"), body.get("address"), body.get("phone"),
                    nz(body.get("openTime"), "08:00"), nz(body.get("closeTime"), "22:00"),
                    min, fee, commission, status, body.get("remark"), tid);
        }
        return getStore();
    }

    /* ========== 分类 ========== */
    public List<Map<String, Object>> listCategories(boolean all) {
        Long tid = requireTenant();
        String sql = "select * from store_category where tenant_id=? "
                + (all ? "" : "and status=1 ")
                + "order by sort_no asc, id asc";
        return mapList(jdbc.queryForList(sql, tid));
    }

    @Transactional
    public Map<String, Object> saveCategory(Map<String, Object> body) {
        assertAdmin();
        Long tid = requireTenant();
        String name = str(body.get("name"));
        if (!StringUtils.hasText(name)) throw new BizException("分类名不能为空");
        int sort = body.get("sortNo") == null ? 0 : Integer.parseInt(String.valueOf(body.get("sortNo")));
        int status = body.get("status") == null ? 1 : Integer.parseInt(String.valueOf(body.get("status")));
        Long id = longOrNull(body.get("id"));
        if (id == null) {
            KeyHolder kh = new GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "insert into store_category(tenant_id,name,sort_no,status) values(?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, tid);
                ps.setString(2, name);
                ps.setInt(3, sort);
                ps.setInt(4, status);
                return ps;
            }, kh);
            id = kh.getKey().longValue();
        } else {
            jdbc.update("update store_category set name=?, sort_no=?, status=? where id=? and tenant_id=?",
                    name, sort, status, id, tid);
        }
        return camel(jdbc.queryForMap("select * from store_category where id=?", id));
    }

    public void deleteCategory(Long id) {
        assertAdmin();
        Long tid = requireTenant();
        Integer cnt = jdbc.queryForObject(
                "select count(1) from store_product where tenant_id=? and category_id=?", Integer.class, tid, id);
        if (cnt != null && cnt > 0) throw new BizException("分类下仍有商品，无法删除");
        jdbc.update("delete from store_category where id=? and tenant_id=?", id, tid);
    }

    /* ========== 商品 ========== */
    public List<Map<String, Object>> listProducts(Long categoryId, String keyword, Boolean onSaleOnly) {
        Long tid = requireTenant();
        StringBuilder sql = new StringBuilder(
                "select p.*, c.name as category_name from store_product p "
                        + "left join store_category c on c.id=p.category_id "
                        + "where p.tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (Boolean.TRUE.equals(onSaleOnly) || isResident()) {
            sql.append("and p.status=1 ");
        }
        if (categoryId != null) {
            sql.append("and p.category_id=? ");
            args.add(categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and p.name like ? ");
            args.add("%" + keyword.trim() + "%");
        }
        sql.append("order by p.sort_no asc, p.id desc");
        return mapList(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    @Transactional
    public Map<String, Object> saveProduct(Map<String, Object> body) {
        assertAdmin();
        Long tid = requireTenant();
        String name = str(body.get("name"));
        if (!StringUtils.hasText(name)) throw new BizException("商品名不能为空");
        BigDecimal price = money(body.get("price"));
        if (price.compareTo(BigDecimal.ZERO) <= 0) throw new BizException("售价须大于0");
        int stock = body.get("stock") == null ? 0 : Integer.parseInt(String.valueOf(body.get("stock")));
        int status = body.get("status") == null ? 1 : Integer.parseInt(String.valueOf(body.get("status")));
        Long catId = longOrNull(body.get("categoryId"));
        Long id = longOrNull(body.get("id"));
        if (id == null) {
            KeyHolder kh = new GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "insert into store_product(tenant_id,category_id,name,cover_url,price,stock,status,sort_no,remark) "
                                + "values(?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, tid);
                if (catId == null) ps.setObject(2, null); else ps.setLong(2, catId);
                ps.setString(3, name);
                ps.setObject(4, body.get("coverUrl"));
                ps.setBigDecimal(5, price);
                ps.setInt(6, stock);
                ps.setInt(7, status);
                ps.setInt(8, body.get("sortNo") == null ? 0 : Integer.parseInt(String.valueOf(body.get("sortNo"))));
                ps.setObject(9, body.get("remark"));
                return ps;
            }, kh);
            id = kh.getKey().longValue();
        } else {
            jdbc.update("update store_product set category_id=?, name=?, cover_url=?, price=?, stock=?, status=?, "
                            + "sort_no=?, remark=? where id=? and tenant_id=?",
                    catId, name, body.get("coverUrl"), price, stock, status,
                    body.get("sortNo") == null ? 0 : Integer.parseInt(String.valueOf(body.get("sortNo"))),
                    body.get("remark"), id, tid);
        }
        return camel(jdbc.queryForMap("select * from store_product where id=?", id));
    }

    public void deleteProduct(Long id) {
        assertAdmin();
        jdbc.update("delete from store_product where id=? and tenant_id=?", id, requireTenant());
    }

    /* ========== 购物车 ========== */
    public List<Map<String, Object>> listCart() {
        assertResident();
        Long tid = requireTenant();
        Long uid = requireUser();
        return mapList(jdbc.queryForList(
                "select c.id, c.product_id, c.quantity, p.name, p.cover_url, p.price, p.stock, p.status, "
                        + "p.shop_id "
                        + "from store_cart c join store_product p on p.id=c.product_id "
                        + "where c.tenant_id=? and c.user_id=? order by c.id desc", tid, uid));
    }

    @Transactional
    public void addCart(Long productId, Integer qty) {
        assertResident();
        Long tid = requireTenant();
        Long uid = requireUser();
        int q = qty == null || qty < 1 ? 1 : qty;
        Map<String, Object> p = requireProduct(tid, productId);
        if (!Integer.valueOf(1).equals(toInt(p.get("status")))) throw new BizException("商品已下架");
        if (toInt(p.get("stock")) < q) throw new BizException("库存不足");
        List<Map<String, Object>> exist = jdbc.queryForList(
                "select id, quantity from store_cart where tenant_id=? and user_id=? and product_id=?",
                tid, uid, productId);
        if (exist.isEmpty()) {
            jdbc.update("insert into store_cart(tenant_id,user_id,product_id,quantity) values(?,?,?,?)",
                    tid, uid, productId, q);
        } else {
            int nq = toInt(exist.get(0).get("quantity")) + q;
            if (toInt(p.get("stock")) < nq) throw new BizException("库存不足");
            jdbc.update("update store_cart set quantity=?, update_time=now() where id=?",
                    nq, exist.get(0).get("id"));
        }
    }

    @Transactional
    public void updateCart(Long cartId, Integer qty) {
        assertResident();
        Long tid = requireTenant();
        Long uid = requireUser();
        if (qty == null || qty < 1) {
            jdbc.update("delete from store_cart where id=? and tenant_id=? and user_id=?", cartId, tid, uid);
            return;
        }
        jdbc.update("update store_cart set quantity=?, update_time=now() where id=? and tenant_id=? and user_id=?",
                qty, cartId, tid, uid);
    }

    public void clearCart() {
        assertResident();
        jdbc.update("delete from store_cart where tenant_id=? and user_id=?", requireTenant(), requireUser());
    }

    /* ========== 下单 / 支付 ========== */
    @Transactional
    public Map<String, Object> createOrder(Map<String, Object> body) {
        assertResident();
        Long tid = requireTenant();
        Long uid = requireUser();
        Map<String, Object> store = getStore();
        if (store.get("status") != null && toInt(store.get("status")) == 0) {
            throw new BizException("店铺暂停营业");
        }
        String deliveryType = str(body.get("deliveryType"));
        if (!DELIVERY.equals(deliveryType) && !PICKUP.equals(deliveryType)) {
            throw new BizException("请选择配送方式");
        }
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) {
            // 从购物车
            List<Map<String, Object>> cart = listCart();
            if (cart.isEmpty()) throw new BizException("购物车为空");
            items = new ArrayList<>();
            for (Map<String, Object> c : cart) {
                Map<String, Object> it = new HashMap<>();
                it.put("productId", c.get("productId"));
                it.put("quantity", c.get("quantity"));
                items.add(it);
            }
        }

        BigDecimal goods = BigDecimal.ZERO;
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Map<String, Object> it : items) {
            Long pid = longOrNull(it.get("productId"));
            int qty = toInt(it.get("quantity"));
            if (pid == null || qty < 1) continue;
            Map<String, Object> p = requireProduct(tid, pid);
            if (!Integer.valueOf(1).equals(toInt(p.get("status")))) {
                throw new BizException(p.get("name") + "已下架");
            }
            if (toInt(p.get("stock")) < qty) {
                throw new BizException(p.get("name") + "库存不足");
            }
            BigDecimal price = (BigDecimal) p.get("price");
            BigDecimal amt = price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            goods = goods.add(amt);
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("product", p);
            line.put("qty", qty);
            line.put("amount", amt);
            lines.add(line);
        }
        if (lines.isEmpty()) throw new BizException("请选择商品");

        Long orderShopId = null;
        for (Map<String, Object> line : lines) {
            Map<String, Object> p = (Map<String, Object>) line.get("product");
            Long psid = longOrNull(p.get("shop_id"));
            if (psid == null) psid = longOrNull(p.get("shopId"));
            if (psid == null) continue;
            if (orderShopId == null) orderShopId = psid;
            else if (!orderShopId.equals(psid)) {
                throw new BizException("一笔订单仅支持同一商户商品，请分单结算");
            }
        }
        if (orderShopId != null) {
            try {
                Integer st = jdbc.queryForObject(
                        "select status from store_shop where id=? and deleted=0", Integer.class, orderShopId);
                if (st == null || st == 0) throw new BizException("该商户已停业，暂不可下单");
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                // shop table may be empty for legacy single-store
            }
        }

        BigDecimal deliveryFee = BigDecimal.ZERO;
        Long houseId = longOrNull(body.get("houseId"));
        if (houseId == null) houseId = TenantContext.getHouseId();
        String receiverName = str(body.get("receiverName"));
        String receiverPhone = str(body.get("receiverPhone"));
        String receiverAddr = str(body.get("receiverAddr"));

        if (DELIVERY.equals(deliveryType)) {
            BigDecimal min = money(store.get("minOrderAmount"));
            if (goods.compareTo(min) < 0) {
                throw new BizException("未满起送金额 ¥" + min.stripTrailingZeros().toPlainString());
            }
            deliveryFee = money(store.get("deliveryFee"));
            if (houseId == null) throw new BizException("请先绑定房屋作为收货地址");
            Map<String, Object> house = loadHouse(tid, uid, houseId);
            if (!StringUtils.hasText(receiverName)) {
                receiverName = firstText(house, "owner_name", "ownerName", "nickname");
            }
            if (!StringUtils.hasText(receiverPhone)) {
                receiverPhone = firstText(house, "owner_phone", "ownerPhone", "phone");
            }
            if (!StringUtils.hasText(receiverAddr)) receiverAddr = str(house.get("address"));
            if (!StringUtils.hasText(receiverAddr)) {
                receiverAddr = str(house.get("building")) + "栋" + str(house.get("unit")) + "单元"
                        + firstText(house, "room_no", "room");
            }
        } else {
            receiverAddr = str(store.get("address"));
            if (!StringUtils.hasText(receiverName)) receiverName = "自提";
            if (!StringUtils.hasText(receiverPhone)) {
                // 尝试用户手机
                try {
                    receiverPhone = jdbc.queryForObject("select phone from sys_user where id=?", String.class, uid);
                } catch (Exception ignored) {
                }
            }
        }

        // 可选优惠券：claimId 为空或不传则不使用
        ensureOrderCouponColumns();
        Long claimId = longOrNull(body.get("claimId"));
        if (claimId == null) claimId = longOrNull(body.get("couponClaimId"));
        BigDecimal couponDiscount = BigDecimal.ZERO;
        Long couponId = null;
        if (claimId != null && claimId > 0) {
            if (orderShopId == null) throw new BizException("当前订单无法使用优惠券");
            Map<String, Object> used = shopCouponService.previewUseClaim(orderShopId, claimId, goods);
            couponDiscount = money(used.get("discountAmount"));
            couponId = longOrNull(used.get("couponId"));
        }

        BigDecimal payAmount = goods.add(deliveryFee).subtract(couponDiscount).setScale(2, RoundingMode.HALF_UP);
        if (payAmount.compareTo(BigDecimal.ZERO) < 0) payAmount = BigDecimal.ZERO;
        String orderNo = "ST" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", new Random().nextInt(10000));
        LocalDateTime expire = LocalDateTime.now().plusMinutes(PAY_EXPIRE_MINUTES);
        Long storeId = longOrNull(store.get("id"));
        Long finalShopId = orderShopId;

        // 扣库存
        for (Map<String, Object> line : lines) {
            Map<String, Object> p = (Map<String, Object>) line.get("product");
            int qty = (Integer) line.get("qty");
            int updated = jdbc.update(
                    "update store_product set stock=stock-? where id=? and tenant_id=? and stock>=?",
                    qty, p.get("id"), tid, qty);
            if (updated == 0) throw new BizException(p.get("name") + "库存不足");
        }

        KeyHolder kh = new GeneratedKeyHolder();
        Long finalHouseId = houseId;
        String finalName = receiverName;
        String finalPhone = receiverPhone;
        String finalAddr = receiverAddr;
        BigDecimal finalGoods = goods;
        BigDecimal finalFee = deliveryFee;
        BigDecimal finalPay = payAmount;
        BigDecimal finalCouponDiscount = couponDiscount;
        Long finalClaimId = claimId != null && claimId > 0 ? claimId : null;
        Long finalCouponId = couponId;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into store_order(tenant_id,order_no,user_id,house_id,store_id,shop_id,delivery_type,status,"
                            + "goods_amount,delivery_fee,coupon_discount,coupon_claim_id,coupon_id,pay_amount,"
                            + "receiver_name,receiver_phone,receiver_addr,remark,expire_time,refund_status) "
                            + "values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'NONE')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tid);
            ps.setString(2, orderNo);
            ps.setLong(3, uid);
            if (finalHouseId == null) ps.setObject(4, null); else ps.setLong(4, finalHouseId);
            if (storeId == null) ps.setObject(5, null); else ps.setLong(5, storeId);
            if (finalShopId == null) ps.setObject(6, null); else ps.setLong(6, finalShopId);
            ps.setString(7, deliveryType);
            ps.setString(8, ST_UNPAID);
            ps.setBigDecimal(9, finalGoods);
            ps.setBigDecimal(10, finalFee);
            ps.setBigDecimal(11, finalCouponDiscount);
            if (finalClaimId == null) ps.setObject(12, null); else ps.setLong(12, finalClaimId);
            if (finalCouponId == null) ps.setObject(13, null); else ps.setLong(13, finalCouponId);
            ps.setBigDecimal(14, finalPay);
            ps.setString(15, finalName);
            ps.setString(16, finalPhone);
            ps.setString(17, finalAddr);
            ps.setObject(18, body.get("remark"));
            ps.setTimestamp(19, Timestamp.valueOf(expire));
            return ps;
        }, kh);
        long orderId = kh.getKey().longValue();

        if (finalClaimId != null) {
            shopCouponService.markClaimUsed(finalClaimId, orderId);
        }

        for (Map<String, Object> line : lines) {
            Map<String, Object> p = (Map<String, Object>) line.get("product");
            int qty = (Integer) line.get("qty");
            BigDecimal amt = (BigDecimal) line.get("amount");
            jdbc.update("insert into store_order_item(tenant_id,order_id,product_id,product_name,cover_url,price,quantity,amount) "
                            + "values(?,?,?,?,?,?,?,?)",
                    tid, orderId, p.get("id"), p.get("name"), p.get("cover_url"), p.get("price"), qty, amt);
        }

        // 清空已下单购物车
        jdbc.update("delete from store_cart where tenant_id=? and user_id=?", tid, uid);

        return getOrder(orderId);
    }

    @Transactional
    public Map<String, Object> payOrder(String orderNo, String action, String channel) {
        assertResident();
        Long tid = requireTenant();
        Long uid = requireUser();
        Map<String, Object> order = loadOrderByNo(tid, orderNo);
        if (!uid.equals(longOrNull(order.get("user_id")))) throw new BizException(403, "无权支付");
        if (!ST_UNPAID.equals(str(order.get("status")))) throw new BizException("订单状态不可支付");

        String act = StringUtils.hasText(action) ? action.trim().toLowerCase() : "success";
        String ch = StringUtils.hasText(channel) ? channel.trim().toUpperCase() : "MOCK";
        if ("WECHAT".equals(ch) || "ALIPAY".equals(ch) || "MOCK".equals(ch)) {
            /* ok */
        } else {
            ch = "MOCK";
        }

        // 演示：模拟取消 / 失败 / 处理中，均不推进履约
        if ("cancel".equals(act)) {
            writePayLog(tid, order, orderNo, ch, "CANCELLED", "mock_cancel");
            return getOrder(longOrNull(order.get("id")));
        }
        if ("fail".equals(act)) {
            writePayLog(tid, order, orderNo, ch, "FAIL", "mock_fail");
            throw new BizException("模拟支付失败，请重试");
        }
        if ("pending".equals(act)) {
            writePayLog(tid, order, orderNo, ch, "PENDING", "mock_pending");
            Map<String, Object> pending = getOrder(longOrNull(order.get("id")));
            pending.put("payPending", true);
            pending.put("message", "支付处理中，请稍后在订单中继续支付");
            return pending;
        }

        // mock success
        markOrderPaid(order, "SP" + System.currentTimeMillis(), ch, "mock_ok");
        return getOrder(longOrNull(order.get("id")));
    }

    /**
     * 调起支付宝沙盒收银台（返回 payForm）
     */
    public Map<String, Object> createAlipayPay(String orderNo, String product) {
        assertResident();
        Long tid = requireTenant();
        Long uid = requireUser();
        Map<String, Object> order = loadOrderByNo(tid, orderNo);
        if (!uid.equals(longOrNull(order.get("user_id")))) {
            throw new BizException(403, "无权支付");
        }
        if (!ST_UNPAID.equals(str(order.get("status")))) {
            throw new BizException("订单状态不可支付");
        }
        BigDecimal amount = money(order.get("pay_amount"));
        String subject = "社区商城-" + orderNo;
        String form = alipaySandboxService.buildPayForm(orderNo, amount, subject, product);
        writePayLog(tid, order, orderNo, "ALIPAY", "PENDING", "alipay_form");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNo", orderNo);
        m.put("orderId", longOrNull(order.get("id")));
        m.put("amount", amount);
        m.put("payChannel", "ALIPAY");
        m.put("payForm", form);
        m.put("product", "wap".equalsIgnoreCase(product) ? "wap" : "page");
        return m;
    }

    /** 前端轮询 / 回跳后主动查单落账 */
    @Transactional
    public Map<String, Object> syncAlipayPaid(String orderNo) {
        Map<String, Object> order = loadOrderByNoGlobal(orderNo);
        Long orderId = longOrNull(order.get("id"));
        if (!ST_UNPAID.equals(str(order.get("status")))) {
            return viewOrder(orderId, longOrNull(order.get("tenant_id")));
        }
        if (!alipaySandboxService.ready()) {
            return viewOrder(orderId, longOrNull(order.get("tenant_id")));
        }
        var resp = alipaySandboxService.queryTrade(orderNo);
        if (alipaySandboxService.isTradeSuccess(resp)) {
            markPaidByAlipay(orderNo, resp.getTradeNo(), resp.getTotalAmount());
        }
        order = loadOrderByNoGlobal(orderNo);
        return viewOrder(longOrNull(order.get("id")), longOrNull(order.get("tenant_id")));
    }

    /** 异步通知：验签 + 落账 */
    @Transactional
    public String handleAlipayNotify(Map<String, String> params) {
        if (!alipaySandboxService.verifyNotify(params)) {
            return "failure";
        }
        String orderNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        String tradeNo = params.get("trade_no");
        if (!StringUtils.hasText(orderNo)) {
            return "failure";
        }
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            return "success";
        }
        try {
            markPaidByAlipay(orderNo, tradeNo, params.get("total_amount"));
        } catch (Exception e) {
            log.warn("store alipay notify mark fail: {}", e.getMessage());
            return "failure";
        }
        return "success";
    }

    /** 同步回跳：查单落账后跳转商城结果页 */
    @Transactional
    public String handleAlipayReturn(Map<String, String> params) {
        String orderNo = params.get("out_trade_no");
        if (!StringUtils.hasText(orderNo)) {
            return alipaySandboxService.frontendStoreResultRedirect("");
        }
        if (params.containsKey("sign")) {
            try {
                alipaySandboxService.verifyNotify(params);
            } catch (Exception ignored) {
            }
        }
        try {
            syncAlipayPaid(orderNo);
        } catch (Exception ignored) {
        }
        return alipaySandboxService.frontendStoreResultRedirect(orderNo);
    }

    private void markPaidByAlipay(String orderNo, String tradeNo, String totalAmount) {
        Map<String, Object> order = loadOrderByNoGlobal(orderNo);
        if (!ST_UNPAID.equals(str(order.get("status")))) {
            return;
        }
        BigDecimal expect = money(order.get("pay_amount")).setScale(2, RoundingMode.HALF_UP);
        if (StringUtils.hasText(totalAmount)) {
            BigDecimal paid = new BigDecimal(totalAmount).setScale(2, RoundingMode.HALF_UP);
            if (paid.compareTo(expect) != 0) {
                throw new BizException(400, "支付金额与订单不一致");
            }
        }
        String payNo = StringUtils.hasText(tradeNo) ? tradeNo : ("ALI" + System.currentTimeMillis());
        markOrderPaid(order, payNo, "ALIPAY", "alipay_ok");
    }

    private void markOrderPaid(Map<String, Object> order, String payNo, String channel, String raw) {
        Long tid = longOrNull(order.get("tenant_id"));
        Long orderId = longOrNull(order.get("id"));
        String orderNo = str(order.get("order_no"));
        // 防并发：仅 UNPAID 可推进
        int updated = jdbc.update(
                "update store_order set status=?, pay_time=now(), update_time=now() where id=? and tenant_id=? and status=?",
                ST_PENDING, orderId, tid, ST_UNPAID);
        if (updated == 0) {
            return;
        }
        try {
            jdbc.update("insert into store_payment_log(tenant_id,order_id,order_no,pay_no,channel,amount,status,raw_resp) "
                            + "values(?,?,?,?,?,?,?,?)",
                    tid, orderId, orderNo, payNo, channel, order.get("pay_amount"), "SUCCESS", raw);
        } catch (Exception ignored) {
        }
        List<Map<String, Object>> items = jdbc.queryForList(
                "select product_id, quantity from store_order_item where order_id=?", orderId);
        for (Map<String, Object> it : items) {
            jdbc.update("update store_product set sales=sales+? where id=?", it.get("quantity"), it.get("product_id"));
        }
        storeNotifyService.notifyNewOrder(tid, longOrNull(order.get("shop_id")), orderNo, money(order.get("pay_amount")));
    }

    private Map<String, Object> viewOrder(Long orderId, Long tid) {
        if (orderId == null) throw new BizException(404, "订单不存在");
        Map<String, Object> order = camel(loadOrder(tid, orderId));
        List<Map<String, Object>> items = mapList(jdbc.queryForList(
                "select * from store_order_item where order_id=?", orderId));
        order.put("items", items);
        order.put("statusLabel", statusLabel(str(order.get("status"))));
        order.put("deliveryTypeLabel", DELIVERY.equals(str(order.get("deliveryType"))) ? "送货上门" : "到店自提");
        return order;
    }

    private Map<String, Object> loadOrderByNoGlobal(String orderNo) {
        List<Map<String, Object>> list = jdbc.queryForList(
                "select * from store_order where order_no=? limit 1", orderNo);
        if (list.isEmpty()) {
            throw new BizException(404, "订单不存在");
        }
        return list.get(0);
    }

    private void writePayLog(Long tid, Map<String, Object> order, String orderNo,
                             String channel, String status, String raw) {
        String payNo = "SP" + System.currentTimeMillis();
        try {
            jdbc.update("insert into store_payment_log(tenant_id,order_id,order_no,pay_no,channel,amount,status,raw_resp) "
                            + "values(?,?,?,?,?,?,?,?)",
                    tid, order.get("id"), orderNo, payNo, channel, order.get("pay_amount"), status, raw);
        } catch (Exception ignored) {
            /* 演示环境表结构差异时忽略 */
        }
    }

    @Transactional
    public Map<String, Object> cancelOrder(Long orderId, String reason) {
        Long tid = requireTenant();
        Map<String, Object> order = loadOrder(tid, orderId);
        String st = str(order.get("status"));
        boolean resident = isResident();
        if (resident) {
            if (!requireUser().equals(longOrNull(order.get("user_id")))) {
                throw new BizException(403, "无权取消");
            }
            if (!ST_UNPAID.equals(st) && !ST_PENDING.equals(st)) {
                throw new BizException("当前状态不可取消");
            }
        } else {
            assertStaff();
            if (ST_COMPLETED.equals(st) || ST_REFUNDED.equals(st) || ST_CANCELLED.equals(st)) {
                throw new BizException("当前状态不可取消");
            }
        }
        releaseStock(tid, orderId);
        releaseOrderCoupon(order);
        jdbc.update("update store_order set status=?, cancel_time=now(), cancel_reason=?, update_time=now() where id=?",
                ST_CANCELLED, reason, orderId);
        storeNotifyService.notifyOrderCancel(
                tid, longOrNull(order.get("shop_id")), str(order.get("order_no")), reason);
        return getOrder(orderId);
    }

    /* ========== 订单查询 / 履约 ========== */
    public List<Map<String, Object>> listOrders(String status, String keyword) {
        Long tid = requireTenant();
        StringBuilder sql = new StringBuilder("select * from store_order where tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (isResident()) {
            sql.append("and user_id=? ");
            args.add(requireUser());
        } else {
            assertCanViewOrder();
        }
        if (StringUtils.hasText(status)) {
            sql.append("and status=? ");
            args.add(status.trim());
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (order_no like ? or receiver_name like ? or receiver_phone like ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k); args.add(k); args.add(k);
        }
        sql.append("order by id desc limit 200");
        List<Map<String, Object>> list = mapList(jdbc.queryForList(sql.toString(), args.toArray()));
        for (Map<String, Object> o : list) {
            o.put("statusLabel", statusLabel(str(o.get("status"))));
            o.put("deliveryTypeLabel", DELIVERY.equals(str(o.get("deliveryType"))) ? "送货上门" : "到店自提");
        }
        return list;
    }

    public Map<String, Object> getOrder(Long id) {
        Long tid = requireTenant();
        Map<String, Object> order = camel(loadOrder(tid, id));
        if (isResident() && !requireUser().equals(longOrNull(order.get("userId")))) {
            throw new BizException(403, "无权查看");
        }
        if (!isResident()) assertCanViewOrder();
        List<Map<String, Object>> items = mapList(jdbc.queryForList(
                "select * from store_order_item where order_id=?", id));
        order.put("items", items);
        order.put("statusLabel", statusLabel(str(order.get("status"))));
        order.put("deliveryTypeLabel", DELIVERY.equals(str(order.get("deliveryType"))) ? "送货上门" : "到店自提");
        return order;
    }

    @Transactional
    public Map<String, Object> staffAction(Long orderId, String action) {
        assertStaff();
        Long tid = requireTenant();
        Map<String, Object> order = loadOrder(tid, orderId);
        String st = str(order.get("status"));
        String next;
        String timeCol;
        switch (action == null ? "" : action) {
            case "accept" -> {
                if (!ST_PENDING.equals(st)) throw new BizException("仅待接单可接单");
                next = ST_PREPARING; timeCol = "accept_time";
            }
            case "prepare" -> {
                if (!ST_PREPARING.equals(st)) throw new BizException("仅备货中可标记备货完成");
                next = ST_DELIVERING; timeCol = "prepare_time";
            }
            case "deliver" -> {
                // 兼容：若仍在备货，也可直接开始配送
                if (!ST_PREPARING.equals(st) && !ST_DELIVERING.equals(st)) {
                    throw new BizException("当前不可开始配送");
                }
                next = ST_DELIVERING; timeCol = "deliver_time";
            }
            case "complete" -> {
                if (!ST_DELIVERING.equals(st) && !ST_PREPARING.equals(st)) {
                    throw new BizException("当前不可确认完成");
                }
                next = ST_COMPLETED; timeCol = "complete_time";
            }
            default -> throw new BizException("未知操作");
        }
        jdbc.update("update store_order set status=?, " + timeCol + "=now(), handler_id=?, update_time=now() where id=?",
                next, TenantContext.getUserId(), orderId);
        return getOrder(orderId);
    }

    /* ========== 退款 ========== */
    @Transactional
    public Map<String, Object> applyRefund(Long orderId, String reason) {
        assertResident();
        Long tid = requireTenant();
        Map<String, Object> order = loadOrder(tid, orderId);
        if (!requireUser().equals(longOrNull(order.get("user_id")))) throw new BizException(403, "无权操作");
        String st = str(order.get("status"));
        if (!(ST_PENDING.equals(st) || ST_PREPARING.equals(st) || ST_DELIVERING.equals(st) || ST_COMPLETED.equals(st))) {
            throw new BizException("当前状态不可申请退款");
        }
        if ("APPLYING".equals(str(order.get("refund_status")))) throw new BizException("退款审核中");
        String refundNo = "RF" + System.currentTimeMillis();
        jdbc.update("insert into store_refund_log(tenant_id,order_id,order_no,refund_no,amount,reason,status) "
                        + "values(?,?,?,?,?,?,?)",
                tid, orderId, order.get("order_no"), refundNo, order.get("pay_amount"), reason, "APPLYING");
        jdbc.update("update store_order set refund_status='APPLYING', refund_reason=?, refund_amount=?, update_time=now() where id=?",
                reason, order.get("pay_amount"), orderId);
        storeNotifyService.notifyRefund(tid, str(order.get("order_no")));
        return getOrder(orderId);
    }

    @Transactional
    public Map<String, Object> auditRefund(Long orderId, boolean approve, String remark) {
        assertAdmin();
        Long tid = requireTenant();
        Map<String, Object> order = loadOrder(tid, orderId);
        if (!"APPLYING".equals(str(order.get("refund_status")))) throw new BizException("无待审退款");
        if (approve) {
            releaseStock(tid, orderId);
            jdbc.update("update store_order set status=?, refund_status='APPROVED', update_time=now() where id=?",
                    ST_REFUNDED, orderId);
            jdbc.update("update store_refund_log set status='APPROVED', audit_by=?, audit_remark=?, audit_time=now() "
                            + "where order_id=? and status='APPLYING'",
                    TenantContext.getUserId(), remark, orderId);
            // 与系统退费逻辑互通：写入支付退款流水
            writeRefundPayLog(tid, order);
        } else {
            jdbc.update("update store_order set refund_status='REJECTED', update_time=now() where id=?", orderId);
            jdbc.update("update store_refund_log set status='REJECTED', audit_by=?, audit_remark=?, audit_time=now() "
                            + "where order_id=? and status='APPLYING'",
                    TenantContext.getUserId(), remark, orderId);
        }
        return getOrder(orderId);
    }

    /** 物业端直接退款（已支付订单），与居民申请退款共用退费记账 */
    @Transactional
    public Map<String, Object> staffRefund(Long orderId, String reason) {
        assertAdmin();
        Long tid = requireTenant();
        Map<String, Object> order = loadOrder(tid, orderId);
        String st = str(order.get("status"));
        if (ST_UNPAID.equals(st) || ST_CANCELLED.equals(st) || ST_REFUNDED.equals(st)) {
            throw new BizException("当前订单不可退款");
        }
        if ("APPLYING".equals(str(order.get("refund_status")))) {
            return auditRefund(orderId, true, reason);
        }
        String refundReason = StringUtils.hasText(reason) ? reason.trim() : "物业端退款";
        String refundNo = "RF" + System.currentTimeMillis();
        jdbc.update("insert into store_refund_log(tenant_id,order_id,order_no,refund_no,amount,reason,status,audit_by,audit_remark,audit_time) "
                        + "values(?,?,?,?,?,?,?,?,?,now())",
                tid, orderId, order.get("order_no"), refundNo, order.get("pay_amount"), refundReason,
                "APPROVED", TenantContext.getUserId(), "物业直接退款");
        releaseStock(tid, orderId);
        jdbc.update("update store_order set status=?, refund_status='APPROVED', refund_reason=?, refund_amount=?, update_time=now() where id=?",
                ST_REFUNDED, refundReason, order.get("pay_amount"), orderId);
        writeRefundPayLog(tid, order);
        return getOrder(orderId);
    }

    private void writeRefundPayLog(Long tid, Map<String, Object> order) {
        try {
            String payNo = "RF" + System.currentTimeMillis();
            jdbc.update("insert into store_payment_log(tenant_id,order_id,order_no,pay_no,channel,amount,status,raw_resp) "
                            + "values(?,?,?,?,?,?,?,?)",
                    tid, order.get("id"), order.get("order_no"), payNo, "REFUND",
                    order.get("pay_amount"), "REFUND", "store_refund_ok");
        } catch (Exception e) {
            log.warn("write refund pay log fail: {}", e.getMessage());
        }
    }

    /* ========== 结算 ========== */
    public Map<String, Object> settlementSummary(String startDate, String endDate) {
        assertStaff();
        Long tid = requireTenant();
        ensureStoreExtColumns();
        Map<String, Object> store = getStore();
        BigDecimal rate = money(store.get("commissionRate"));
        if (rate.compareTo(BigDecimal.ZERO) <= 0) rate = new BigDecimal("5");

        StringBuilder where = new StringBuilder(" where tenant_id=? and status in (?,?,?,?,?) ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        args.add(ST_PENDING);
        args.add(ST_PREPARING);
        args.add(ST_DELIVERING);
        args.add(ST_COMPLETED);
        args.add(ST_REFUNDED);
        if (StringUtils.hasText(startDate)) {
            where.append(" and ifnull(pay_time, create_time) >= ? ");
            args.add(Timestamp.valueOf(java.time.LocalDate.parse(startDate.substring(0, 10)).atStartOfDay()));
        }
        if (StringUtils.hasText(endDate)) {
            where.append(" and ifnull(pay_time, create_time) < ? ");
            args.add(Timestamp.valueOf(java.time.LocalDate.parse(endDate.substring(0, 10)).plusDays(1).atStartOfDay()));
        }

        BigDecimal gmv = jdbc.queryForObject(
                "select ifnull(sum(pay_amount),0) from store_order " + where
                        + " and status <> ?",
                BigDecimal.class, appendArgs(args, ST_REFUNDED));
        // GMV = 有效已付未退款订单；上面把 REFUNDED 排除
        Integer orderCount = jdbc.queryForObject(
                "select count(1) from store_order " + where + " and status <> ?",
                Integer.class, appendArgs(args, ST_REFUNDED));

        StringBuilder refundWhere = new StringBuilder(" where tenant_id=? and status=? ");
        List<Object> rArgs = new ArrayList<>();
        rArgs.add(tid);
        rArgs.add(ST_REFUNDED);
        if (StringUtils.hasText(startDate)) {
            refundWhere.append(" and update_time >= ? ");
            rArgs.add(Timestamp.valueOf(java.time.LocalDate.parse(startDate.substring(0, 10)).atStartOfDay()));
        }
        if (StringUtils.hasText(endDate)) {
            refundWhere.append(" and update_time < ? ");
            rArgs.add(Timestamp.valueOf(java.time.LocalDate.parse(endDate.substring(0, 10)).plusDays(1).atStartOfDay()));
        }
        BigDecimal refundAmount = jdbc.queryForObject(
                "select ifnull(sum(ifnull(refund_amount, pay_amount)),0) from store_order " + refundWhere,
                BigDecimal.class, rArgs.toArray());
        Integer refundCount = jdbc.queryForObject(
                "select count(1) from store_order " + refundWhere, Integer.class, rArgs.toArray());

        if (gmv == null) gmv = BigDecimal.ZERO;
        if (refundAmount == null) refundAmount = BigDecimal.ZERO;
        BigDecimal commission = gmv.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal settleAmount = gmv.subtract(commission).subtract(refundAmount);
        if (settleAmount.compareTo(BigDecimal.ZERO) < 0) settleAmount = BigDecimal.ZERO;

        List<Map<String, Object>> ledger = jdbc.queryForList(
                "select order_no as orderNo, status, pay_amount as payAmount, refund_amount as refundAmount, "
                        + "refund_status as refundStatus, receiver_name as receiverName, "
                        + "pay_time as payTime, complete_time as completeTime, update_time as updateTime "
                        + "from store_order " + where + " order by id desc limit 500",
                args.toArray());
        for (Map<String, Object> row : ledger) {
            row.put("statusLabel", statusLabel(str(row.get("status"))));
            BigDecimal pay = money(row.get("payAmount"));
            BigDecimal rowCommission = pay.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            row.put("commission", rowCommission);
            if (ST_REFUNDED.equals(str(row.get("status")))) {
                row.put("settleAmount", BigDecimal.ZERO);
            } else {
                row.put("settleAmount", pay.subtract(rowCommission));
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("storeName", store.get("name"));
        m.put("commissionRate", rate);
        m.put("orderCount", orderCount == null ? 0 : orderCount);
        m.put("gmv", gmv);
        m.put("commissionAmount", commission);
        m.put("refundCount", refundCount == null ? 0 : refundCount);
        m.put("refundAmount", refundAmount);
        m.put("settleAmount", settleAmount);
        m.put("netAmount", gmv.subtract(refundAmount));
        m.put("ledger", mapList(ledger));
        m.put("startDate", startDate);
        m.put("endDate", endDate);
        return m;
    }

    public List<Map<String, Object>> listRefunds() {
        assertStaff();
        Long tid = requireTenant();
        try {
            return mapList(jdbc.queryForList(
                    "select id, order_id as orderId, order_no as orderNo, refund_no as refundNo, amount, reason, "
                            + "status, audit_remark as auditRemark, audit_time as auditTime, create_time as createTime "
                            + "from store_refund_log where tenant_id=? order by id desc limit 200",
                    tid));
        } catch (Exception e) {
            return List.of();
        }
    }

    private Object[] appendArgs(List<Object> base, Object extra) {
        List<Object> a = new ArrayList<>(base);
        a.add(extra);
        return a.toArray();
    }

    private void ensureStoreExtColumns() {
        try {
            Integer cnt = jdbc.queryForObject(
                    "select count(1) from information_schema.columns where table_schema=database() "
                            + "and table_name='store_info' and column_name='commission_rate'",
                    Integer.class);
            if (cnt == null || cnt == 0) {
                jdbc.execute("alter table store_info add column commission_rate decimal(5,2) null default 5.00 "
                        + "comment '平台佣金比例%' after delivery_fee");
            }
        } catch (Exception ignored) {
        }
    }

    private void ensureOrderCouponColumns() {
        ensureCol("store_order", "coupon_discount",
                "alter table store_order add column coupon_discount decimal(10,2) not null default 0 after delivery_fee");
        ensureCol("store_order", "coupon_claim_id",
                "alter table store_order add column coupon_claim_id bigint null after coupon_discount");
        ensureCol("store_order", "coupon_id",
                "alter table store_order add column coupon_id bigint null after coupon_claim_id");
    }

    private void releaseOrderCoupon(Map<String, Object> order) {
        Long claimId = longOrNull(order.get("coupon_claim_id"));
        if (claimId == null) claimId = longOrNull(order.get("couponClaimId"));
        if (claimId == null) return;
        try {
            jdbc.update(
                    "update store_coupon_claim set claim_status='UNUSED', use_time=null, order_id=null "
                            + "where id=? and claim_status='USED'",
                    claimId);
        } catch (Exception ignored) {
        }
    }

    private void ensureCol(String table, String column, String alterSql) {
        try {
            Integer cnt = jdbc.queryForObject(
                    "select count(1) from information_schema.columns where table_schema=database() "
                            + "and table_name=? and column_name=?",
                    Integer.class, table, column);
            if (cnt != null && cnt > 0) return;
            jdbc.execute(alterSql);
        } catch (Exception ignored) {
        }
    }

    /** 未支付超时关闭 */
    @Transactional
    public int closeExpiredUnpaid() {
        List<Map<String, Object>> list = jdbc.queryForList(
                "select id, tenant_id from store_order where status=? and expire_time is not null and expire_time < now() limit 100",
                ST_UNPAID);
        int n = 0;
        for (Map<String, Object> o : list) {
            Long tid = longOrNull(o.get("tenant_id"));
            Long id = longOrNull(o.get("id"));
            releaseStock(tid, id);
            releaseOrderCoupon(o);
            jdbc.update("update store_order set status=?, cancel_time=now(), cancel_reason='超时未支付自动关闭', update_time=now() where id=?",
                    ST_CANCELLED, id);
            n++;
        }
        return n;
    }

    public Map<String, Object> staffTodo() {
        assertStaff();
        Long tid = requireTenant();
        Integer pending = jdbc.queryForObject(
                "select count(1) from store_order where tenant_id=? and status=?", Integer.class, tid, ST_PENDING);
        Integer preparing = jdbc.queryForObject(
                "select count(1) from store_order where tenant_id=? and status in (?,?)",
                Integer.class, tid, ST_PREPARING, ST_DELIVERING);
        Integer refund = jdbc.queryForObject(
                "select count(1) from store_order where tenant_id=? and refund_status='APPLYING'", Integer.class, tid);
        Map<String, Object> m = new HashMap<>();
        m.put("pendingAccept", pending == null ? 0 : pending);
        m.put("doing", preparing == null ? 0 : preparing);
        m.put("refundApplying", refund == null ? 0 : refund);
        return m;
    }

    /* ========== helpers ========== */
    private void releaseStock(Long tid, Long orderId) {
        List<Map<String, Object>> items = jdbc.queryForList(
                "select product_id, quantity from store_order_item where order_id=?", orderId);
        for (Map<String, Object> it : items) {
            jdbc.update("update store_product set stock=stock+? where id=? and tenant_id=?",
                    it.get("quantity"), it.get("product_id"), tid);
        }
    }

    private Map<String, Object> requireProduct(Long tid, Long id) {
        List<Map<String, Object>> list = jdbc.queryForList(
                "select * from store_product where id=? and tenant_id=?", id, tid);
        if (list.isEmpty()) throw new BizException("商品不存在");
        return list.get(0);
    }

    private Map<String, Object> loadOrder(Long tid, Long id) {
        List<Map<String, Object>> list = jdbc.queryForList(
                "select * from store_order where id=? and tenant_id=?", id, tid);
        if (list.isEmpty()) throw new BizException(404, "订单不存在");
        return list.get(0);
    }

    private Map<String, Object> loadOrderByNo(Long tid, String orderNo) {
        List<Map<String, Object>> list = jdbc.queryForList(
                "select * from store_order where order_no=? and tenant_id=?", orderNo, tid);
        if (list.isEmpty()) throw new BizException(404, "订单不存在");
        return list.get(0);
    }

    private Map<String, Object> loadHouse(Long tid, Long uid, Long houseId) {
        try {
            List<Map<String, Object>> list = jdbc.queryForList(
                    "select h.*, u.nickname as owner_name, u.phone as owner_phone, "
                            + "concat(ifnull(h.community_name,''),' ',ifnull(h.building,''),ifnull(h.unit,''),ifnull(h.room_no,'')) as address "
                            + "from community_house h left join sys_user u on u.id=? "
                            + "where h.id=? and h.tenant_id=?", uid, houseId, tid);
            if (!list.isEmpty()) return list.get(0);
        } catch (Exception e) {
            log.debug("house load: {}", e.getMessage());
        }
        throw new BizException("收货房屋无效，请先绑定房屋");
    }

    private String statusLabel(String s) {
        return switch (s == null ? "" : s) {
            case ST_UNPAID -> "待支付";
            case ST_PENDING -> "待接单";
            case ST_PREPARING -> "备货中";
            case ST_DELIVERING -> "配送中";
            case ST_COMPLETED -> "已完成";
            case ST_CANCELLED -> "已取消";
            case ST_REFUNDED -> "已退款";
            default -> s;
        };
    }

    private Long requireTenant() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) throw new BizException(40001, "缺少租户");
        return tid;
    }

    private Long requireUser() {
        Long uid = TenantContext.getUserId();
        if (uid == null) throw new BizException(401, "未登录");
        return uid;
    }

    private boolean isResident() {
        return RoleConstants.RESIDENT.equals(TenantContext.getRole());
    }

    private void assertResident() {
        if (!isResident()) throw new BizException(403, "仅居民可操作");
    }

    private void assertStaff() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isPropertyAdmin(role) && !RoleConstants.isGridWorker(role)
                && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业人员可操作");
        }
    }

    private void assertAdmin() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isPropertyAdmin(role) && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业管理员可维护商户/商品/退款审核");
        }
    }

    private void assertCanViewOrder() {
        assertStaff();
    }

    private List<Map<String, Object>> mapList(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) out.add(camel(r));
        return out;
    }

    private Map<String, Object> camel(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String k = e.getKey();
            if (k == null) continue;
            StringBuilder sb = new StringBuilder();
            boolean up = false;
            for (char c : k.toCharArray()) {
                if (c == '_') { up = true; continue; }
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
            Object v = e.getValue();
            if (v instanceof Timestamp ts) v = ts.toLocalDateTime().toString().replace('T', ' ');
            m.put(sb.toString(), v);
        }
        return m;
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }

    private String firstText(Map<String, Object> m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            String v = str(m.get(k));
            if (StringUtils.hasText(v) && !"null".equalsIgnoreCase(v)) return v;
        }
        return null;
    }

    private Object nz(Object o, Object d) { return o == null || String.valueOf(o).isBlank() ? d : o; }
    private Long longOrNull(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        return Long.parseLong(String.valueOf(o));
    }
    private int toInt(Object o) {
        if (o == null) return 0;
        return Integer.parseInt(String.valueOf(o));
    }
    private BigDecimal money(Object... vals) {
        for (Object v : vals) {
            if (v == null) continue;
            return new BigDecimal(String.valueOf(v)).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
