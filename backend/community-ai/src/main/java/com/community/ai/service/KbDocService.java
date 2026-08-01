package com.community.ai.service;

import com.community.ai.rag.LocalTextEmbedder;
import com.community.ai.util.DocumentTextExtractor;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 知识库文档：上传（Markdown/TXT/PDF/Word）→ 文本管理 → 切分向量入库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbDocService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";

    private static final int DEFAULT_WINDOW = 400;
    private static final int DEFAULT_OVERLAP = 80;
    private static final int MIN_WINDOW = 80;
    private static final int MAX_WINDOW = 4000;
    private static final int MAX_OVERLAP = 2000;

    private final JdbcTemplate jdbc;
    private final KbCategoryService categoryService;

    @PostConstruct
    public void ensureSchema() {
        ensureCol("knowledge_documents", "doc_status",
                "alter table knowledge_documents add column doc_status varchar(16) not null default 'ACTIVE' "
                        + "comment 'DRAFT草稿/ACTIVE生效中/REVOKED已废止' after index_status");
        ensureCol("knowledge_index_log", "duration_ms",
                "alter table knowledge_index_log add column duration_ms int null after success");
        ensureCol("knowledge_index_log", "chunk_no",
                "alter table knowledge_index_log add column chunk_no int null after duration_ms");
        ensureCol("knowledge_index_log", "chunk_preview",
                "alter table knowledge_index_log add column chunk_preview varchar(1000) null after chunk_no");
        ensureCol("knowledge_index_log", "error_detail",
                "alter table knowledge_index_log add column error_detail varchar(2000) null after chunk_preview");
        try {
            jdbc.update("update knowledge_documents set doc_status='ACTIVE' "
                    + "where doc_status is null or doc_status=''");
        } catch (Exception ignored) {
        }
    }

    public List<Map<String, Object>> listDocs(String category, String keyword, String docStatus) {
        assertStaff();
        ensureSchema();
        Long tid = requireTenant();
        categoryService.ensureTable();
        StringBuilder sql = new StringBuilder(
                "select d.id,d.tenant_id,d.category,d.title,d.file_name,d.char_count,d.index_status,d.doc_status,"
                        + "d.chunk_count,d.create_time,d.update_time,c.name as category_name "
                        + "from knowledge_documents d "
                        + "left join knowledge_category c on c.tenant_id=d.tenant_id and c.code=d.category "
                        + "where d.tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tid);
        if (StringUtils.hasText(category)) {
            sql.append("and d.category=? ");
            args.add(category.trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(docStatus)) {
            sql.append("and d.doc_status=? ");
            args.add(normalizeDocStatus(docStatus));
        }
        if (StringUtils.hasText(keyword)) {
            sql.append("and (d.title like ? or d.file_name like ?) ");
            String kw = "%" + keyword.trim() + "%";
            args.add(kw);
            args.add(kw);
        }
        sql.append("order by d.id desc");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = camel(r);
            enrichDoc(m);
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> getDoc(Long id) {
        assertStaff();
        ensureSchema();
        Map<String, Object> r = jdbc.queryForMap(
                "select * from knowledge_documents where id=? and tenant_id=?", id, requireTenant());
        Map<String, Object> m = camel(r);
        try {
            List<Map<String, Object>> cats = jdbc.queryForList(
                    "select name from knowledge_category where tenant_id=? and code=?",
                    requireTenant(), m.get("category"));
            if (!cats.isEmpty()) m.put("categoryName", cats.get(0).get("name"));
        } catch (Exception ignored) {
        }
        enrichDoc(m);
        return m;
    }

    @Transactional
    public Map<String, Object> upload(String title, String fileName, String content, String category) {
        assertAdmin();
        ensureSchema();
        if (!StringUtils.hasText(content)) throw new BizException("文档内容为空");
        String cat = normalizeCategory(category);
        if (!StringUtils.hasText(title)) {
            title = StringUtils.hasText(fileName) ? DocumentTextExtractor.guessTitle(fileName) : "未命名文档";
        }
        if (!StringUtils.hasText(fileName)) fileName = title + ".md";
        Long tid = requireTenant();
        jdbc.update(
                "insert into knowledge_documents(tenant_id,category,title,file_name,content,char_count,"
                        + "index_status,doc_status,chunk_count,uploader_id) "
                        + "values(?,?,?,?,?,?,'NONE','DRAFT',0,?)",
                tid, cat, title.trim(), fileName.trim(), content, content.length(), TenantContext.getUserId());
        Long id = jdbc.queryForObject("select last_insert_id()", Long.class);
        return getDoc(id);
    }

    /** 二进制上传：自动解析 PDF / Word / 文本 */
    @Transactional
    public Map<String, Object> uploadFile(MultipartFile file, String title, String category) {
        assertAdmin();
        if (file == null || file.isEmpty()) throw new BizException("请选择文件");
        String fileName = file.getOriginalFilename();
        if (!StringUtils.hasText(fileName)) fileName = "upload.bin";
        try {
            String content = DocumentTextExtractor.extract(fileName, file.getBytes());
            String t = StringUtils.hasText(title) ? title : DocumentTextExtractor.guessTitle(fileName);
            return upload(t, fileName, content, category);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("上传失败：" + e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> updateContent(Long id, Map<String, Object> body) {
        assertAdmin();
        ensureSchema();
        Long tid = requireTenant();
        Map<String, Object> doc = jdbc.queryForMap(
                "select * from knowledge_documents where id=? and tenant_id=?", id, tid);
        String content = body.get("content") != null ? String.valueOf(body.get("content")) : String.valueOf(doc.get("content"));
        if (!StringUtils.hasText(content)) throw new BizException("文档内容为空");
        String title = body.get("title") != null ? String.valueOf(body.get("title")).trim() : String.valueOf(doc.get("title"));
        String cat = body.get("category") != null ? normalizeCategory(String.valueOf(body.get("category")))
                : String.valueOf(doc.get("category"));
        String docStatus = body.get("docStatus") != null
                ? normalizeDocStatus(String.valueOf(body.get("docStatus")))
                : null;
        if (docStatus != null) {
            jdbc.update(
                    "update knowledge_documents set title=?, category=?, content=?, char_count=?, "
                            + "doc_status=?, index_status='NONE', chunk_count=0 where id=? and tenant_id=?",
                    title, cat, content, content.length(), docStatus, id, tid);
        } else {
            jdbc.update(
                    "update knowledge_documents set title=?, category=?, content=?, char_count=?, "
                            + "index_status='NONE', chunk_count=0 where id=? and tenant_id=?",
                    title, cat, content, content.length(), id, tid);
        }
        jdbc.update("delete from knowledge_chunks where doc_id=? and tenant_id=?", id, tid);
        log(tid, id, "UPDATE", "文本已更新，旧向量已清理，请重新解析入库", true, null, null, null, null);
        return getDoc(id);
    }

    @Transactional
    public Map<String, Object> updateDocStatus(Long id, String docStatus) {
        assertAdmin();
        ensureSchema();
        String st = normalizeDocStatus(docStatus);
        int n = jdbc.update(
                "update knowledge_documents set doc_status=? where id=? and tenant_id=?",
                st, id, requireTenant());
        if (n == 0) throw new BizException("文档不存在");
        log(requireTenant(), id, "STATUS", "文档状态变更为 " + st, true, null, null, null, null);
        return getDoc(id);
    }

    @Transactional
    public void delete(Long id) {
        assertAdmin();
        Long tid = requireTenant();
        jdbc.update("delete from knowledge_chunks where doc_id=? and tenant_id=?", id, tid);
        jdbc.update("delete from knowledge_index_log where doc_id=? and tenant_id=?", id, tid);
        int n = jdbc.update("delete from knowledge_documents where id=? and tenant_id=?", id, tid);
        if (n == 0) throw new BizException("文档不存在");
    }

    @Transactional
    public Map<String, Object> batchDelete(List<Long> ids) {
        assertAdmin();
        if (ids == null || ids.isEmpty()) throw new BizException("请选择文档");
        int n = 0;
        for (Long id : ids) {
            if (id == null) continue;
            try {
                delete(id);
                n++;
            } catch (Exception e) {
                log.warn("batch delete {} skip: {}", id, e.getMessage());
            }
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("deleted", n);
        resp.put("message", "已删除 " + n + " 份文档");
        return resp;
    }

    /** 解析入库 / 重建索引：清理旧切片后按参数重新切分+向量化 */
    @Transactional
    public Map<String, Object> buildIndex(Long id, Map<String, Object> body) {
        assertAdmin();
        ensureSchema();
        Long tid = requireTenant();
        int window = clampInt(body == null ? null : body.get("chunkSize"), DEFAULT_WINDOW, MIN_WINDOW, MAX_WINDOW);
        int overlap = clampInt(body == null ? null : body.get("overlap"), DEFAULT_OVERLAP, 0, Math.min(MAX_OVERLAP, window - 1));
        boolean rebuild = body != null && Boolean.TRUE.equals(body.get("rebuild"));

        Map<String, Object> doc;
        try {
            doc = jdbc.queryForMap(
                    "select * from knowledge_documents where id=? and tenant_id=?", id, tid);
        } catch (Exception e) {
            throw new BizException("文档不存在");
        }
        String content = String.valueOf(doc.get("content"));
        long t0 = System.currentTimeMillis();
        try {
            log(tid, id, rebuild ? "REBUILD" : "LOAD",
                    (rebuild ? "重建索引：" : "加载文档：") + "字符数=" + content.length()
                            + "，切片=" + window + "，重叠=" + overlap,
                    true, null, null, null, null);
            jdbc.update("update knowledge_documents set index_status='BUILDING' where id=?", id);

            long tDel = System.currentTimeMillis();
            int deleted = jdbc.update("delete from knowledge_chunks where doc_id=? and tenant_id=?", id, tid);
            log(tid, id, "CLEAN", "清理旧切片 " + deleted + " 条", true,
                    (int) (System.currentTimeMillis() - tDel), null, null, null);

            long tSplit = System.currentTimeMillis();
            List<String> chunks = split(content, window, overlap);
            log(tid, id, "SPLIT",
                    "滑动窗口切分 window=" + window + " overlap=" + overlap + " → " + chunks.size() + " 片",
                    true, (int) (System.currentTimeMillis() - tSplit), null, null, null);

            int i = 0;
            for (String c : chunks) {
                long tChunk = System.currentTimeMillis();
                try {
                    float[] vec = LocalTextEmbedder.embed(c);
                    byte[] blob = LocalTextEmbedder.toBlob(vec);
                    jdbc.update(
                            "insert into knowledge_chunks(tenant_id,doc_id,chunk_no,content,token_est,embedding_blob,embedding_dim) "
                                    + "values(?,?,?,?,?,?,?)",
                            tid, id, i, c, Math.max(1, c.length() / 2), blob, LocalTextEmbedder.DIM);
                    log(tid, id, "VECTOR",
                            "切片#" + i + " 向量化成功 dim=" + LocalTextEmbedder.DIM + " len=" + c.length(),
                            true, (int) (System.currentTimeMillis() - tChunk), i, preview(c), null);
                } catch (Exception ex) {
                    log(tid, id, "VECTOR", "切片#" + i + " 向量化失败",
                            false, (int) (System.currentTimeMillis() - tChunk), i, preview(c), ex.getMessage());
                    throw ex;
                }
                i++;
            }
            jdbc.update("update knowledge_documents set index_status='READY', chunk_count=? where id=?",
                    chunks.size(), id);
            // 首次入库后若仍为草稿，自动生效（可再手动废止）
            try {
                jdbc.update(
                        "update knowledge_documents set doc_status=? where id=? and tenant_id=? and doc_status=?",
                        STATUS_ACTIVE, id, tid, STATUS_DRAFT);
            } catch (Exception ignored) {
            }
            log(tid, id, "DONE", "索引构建完成，共 " + chunks.size() + " 片，总耗时 "
                            + (System.currentTimeMillis() - t0) + "ms",
                    true, (int) (System.currentTimeMillis() - t0), null, null, null);
            return getDoc(id);
        } catch (Exception e) {
            jdbc.update("update knowledge_documents set index_status='FAILED' where id=?", id);
            log(tid, id, "FAILED", "索引构建失败", false,
                    (int) (System.currentTimeMillis() - t0), null, null, e.getMessage());
            throw new BizException("索引构建失败：" + e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> batchBuildIndex(List<Long> ids, Map<String, Object> body) {
        assertAdmin();
        if (ids == null || ids.isEmpty()) throw new BizException("请选择文档");
        int ok = 0;
        int fail = 0;
        List<String> errors = new ArrayList<>();
        Map<String, Object> opts = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        opts.put("rebuild", true);
        for (Long id : ids) {
            if (id == null) continue;
            try {
                buildIndex(id, opts);
                ok++;
            } catch (Exception e) {
                fail++;
                errors.add("#" + id + ": " + e.getMessage());
            }
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", ok);
        resp.put("failed", fail);
        resp.put("errors", errors);
        resp.put("message", "成功 " + ok + "，失败 " + fail);
        return resp;
    }

    public List<Map<String, Object>> listLogs(Long docId) {
        assertStaff();
        ensureSchema();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from knowledge_index_log where tenant_id=? and doc_id=? order by id desc limit 500",
                requireTenant(), docId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = camel(r);
            Object ok = m.get("success");
            boolean success = ok instanceof Number n ? n.intValue() == 1
                    : Boolean.TRUE.equals(ok) || "1".equals(String.valueOf(ok));
            m.put("success", success);
            m.put("resultLabel", success ? "成功" : "失败");
            out.add(m);
        }
        return out;
    }

    /** 切片明细（检索调试用） */
    public List<Map<String, Object>> listChunks(Long docId) {
        assertStaff();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, doc_id, chunk_no, content, token_est, embedding_dim, "
                        + "(embedding_blob is not null) as has_vector, create_time "
                        + "from knowledge_chunks where tenant_id=? and doc_id=? order by chunk_no asc",
                requireTenant(), docId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = camel(r);
            Object hv = m.get("hasVector");
            boolean has = hv instanceof Number n ? n.intValue() == 1
                    : Boolean.TRUE.equals(hv) || "1".equals(String.valueOf(hv)) || "true".equalsIgnoreCase(String.valueOf(hv));
            m.put("hasVector", has);
            m.put("vectorResult", has ? "已向量化" : "无向量");
            out.add(m);
        }
        return out;
    }

    private void enrichDoc(Map<String, Object> m) {
        String st = String.valueOf(m.getOrDefault("docStatus", STATUS_ACTIVE));
        m.put("docStatus", st);
        m.put("docStatusLabel", switch (st) {
            case STATUS_DRAFT -> "草稿";
            case STATUS_REVOKED -> "已废止";
            default -> "生效中";
        });
        m.put("docStatusTag", switch (st) {
            case STATUS_DRAFT -> "info";
            case STATUS_REVOKED -> "danger";
            default -> "success";
        });
    }

    private String normalizeDocStatus(String s) {
        if (!StringUtils.hasText(s)) return STATUS_ACTIVE;
        String u = s.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "DRAFT", "草稿" -> STATUS_DRAFT;
            case "REVOKED", "已废止", "废止" -> STATUS_REVOKED;
            case "ACTIVE", "生效中", "生效" -> STATUS_ACTIVE;
            default -> throw new BizException("无效文档状态");
        };
    }

    private String normalizeCategory(String category) {
        if (!StringUtils.hasText(category)) return "policy";
        String c = category.trim().toLowerCase(Locale.ROOT);
        if (categoryService.isValidCode(c)) return c;
        if ("policy".equals(c) || "community".equals(c)) return c;
        throw new BizException("分类不存在或已停用，请先在「知识库分类」中维护");
    }

    private List<String> split(String content, int window, int overlap) {
        String text = content.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        if (text.isEmpty()) return chunks;
        int stepOverlap = Math.max(0, Math.min(overlap, window - 1));
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + window);
            chunks.add(text.substring(i, end));
            if (end >= text.length()) break;
            i = Math.max(i + 1, end - stepOverlap);
        }
        return chunks;
    }

    private void log(Long tid, Long docId, String step, String msg, boolean ok,
                     Integer durationMs, Integer chunkNo, String chunkPreview, String errorDetail) {
        try {
            jdbc.update(
                    "insert into knowledge_index_log(tenant_id,doc_id,step,message,success,duration_ms,chunk_no,chunk_preview,error_detail) "
                            + "values(?,?,?,?,?,?,?,?,?)",
                    tid, docId, step, truncate(msg, 500), ok ? 1 : 0,
                    durationMs, chunkNo, truncate(chunkPreview, 1000), truncate(errorDetail, 2000));
        } catch (Exception e) {
            // 兼容旧表结构
            try {
                jdbc.update(
                        "insert into knowledge_index_log(tenant_id,doc_id,step,message,success) values(?,?,?,?,?)",
                        tid, docId, step, truncate(msg, 500), ok ? 1 : 0);
            } catch (Exception ignored) {
            }
        }
    }

    private static String preview(String c) {
        if (c == null) return null;
        String s = c.replace('\n', ' ').trim();
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static int clampInt(Object v, int def, int min, int max) {
        if (v == null) return def;
        try {
            int n = v instanceof Number num ? num.intValue() : Integer.parseInt(String.valueOf(v).trim());
            return Math.max(min, Math.min(max, n));
        } catch (Exception e) {
            return def;
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
        } catch (Exception e) {
            log.debug("ensure col {}.{} skip: {}", table, column, e.getMessage());
        }
    }

    private void assertStaff() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isPropertyAdmin(role) && !RoleConstants.isGridWorker(role)
                && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "仅物业人员可查看");
        }
    }

    private void assertAdmin() {
        String role = TenantContext.getRole();
        if (!RoleConstants.isPropertyAdmin(role) && !RoleConstants.isPlatformAdmin(role)) {
            throw new BizException(403, "网格员不可上传或重建索引");
        }
    }

    private Long requireTenant() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) throw new BizException(40001, "缺少租户");
        return tid;
    }

    private Map<String, Object> camel(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String k = e.getKey();
            if (k == null) continue;
            StringBuilder sb = new StringBuilder();
            boolean up = false;
            for (char c : k.toCharArray()) {
                if (c == '_') {
                    up = true;
                    continue;
                }
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
            Object v = e.getValue();
            if (v instanceof java.sql.Timestamp ts) v = ts.toLocalDateTime().toString().replace('T', ' ');
            if ("content".equals(sb.toString()) && v instanceof byte[]) {
                continue;
            }
            m.put(sb.toString(), v);
        }
        return m;
    }
}
