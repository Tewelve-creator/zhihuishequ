package com.community.ai.rag;

import com.community.ai.entity.AiKnowledge;
import com.community.ai.service.KbSettingsService;
import com.community.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * RAG 检索：问答对（Jaccard）+ 知识文档分块（向量余弦）；低于阈值不强制套用。
 */
@Service
@RequiredArgsConstructor
public class RagKnowledgeService {

    private final JdbcTemplate jdbcTemplate;
    private final KbSettingsService settingsService;

    @Value("${ai.rag.min-score:0.32}")
    private double defaultMinScore;

    private double minScore() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) return defaultMinScore;
        try {
            return settingsService.minScore(tid);
        } catch (Exception e) {
            return defaultMinScore;
        }
    }

    public Optional<RagHit> retrieve(String question) {
        Long tenantId = TenantContext.getTenantId();
        double minScore = minScore();
        boolean preferDoc = true;
        try {
            preferDoc = settingsService.preferDocument(tenantId);
        } catch (Exception ignored) {
        }
        RagHit qa = bestQaHit(tenantId, question);
        RagHit doc = bestDocHit(tenantId, question);
        RagHit best;
        if (qa == null && doc == null) {
            RagHit empty = new RagHit();
            empty.setScore(0);
            empty.setAccepted(false);
            empty.setHitType("none");
            best = empty;
        } else if (qa == null) {
            best = doc;
        } else if (doc == null) {
            best = qa;
        } else if (doc.getScore() > qa.getScore() + 1e-9) {
            best = doc;
        } else if (qa.getScore() > doc.getScore() + 1e-9) {
            best = qa;
        } else {
            // 同分：可配置优先文档或问答对
            if (preferDoc) {
                best = doc.getScore() >= minScore ? doc : qa;
            } else {
                best = qa.getScore() >= minScore ? qa : doc;
            }
        }
        return Optional.of(best);
    }

    public List<RagHit> retrieveTopAccepted(String question) {
        int k = 3;
        try {
            k = settingsService.topK(TenantContext.getTenantId());
        } catch (Exception ignored) {
        }
        return retrieveTopAccepted(question, k);
    }

    public void bumpHit(AiKnowledge kb) {
        if (kb == null || kb.getId() == null) return;
        jdbcTemplate.update(
                "UPDATE ai_knowledge SET hit_count=IFNULL(hit_count,0)+1 WHERE id=?",
                kb.getId());
    }

    public List<RagHit> retrieveTopAccepted(String question, int limit) {
        Long tenantId = TenantContext.getTenantId();
        List<RagHit> all = new ArrayList<>();
        all.addAll(listQaHits(tenantId, question));
        all.addAll(listDocHits(tenantId, question));
        all.sort(Comparator.comparingDouble(RagHit::getScore).reversed());
        List<RagHit> out = new ArrayList<>();
        for (RagHit h : all) {
            if (!h.isAccepted()) continue;
            out.add(h);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private RagHit bestQaHit(Long tenantId, String question) {
        List<RagHit> list = listQaHits(tenantId, question);
        return list.isEmpty() ? null : list.get(0);
    }

    private List<RagHit> listQaHits(Long tenantId, String question) {
        List<AiKnowledge> rows = jdbcTemplate.query(
                "SELECT id,tenant_id,category,question,answer,keywords,hit_count,status,create_time,update_time " +
                        "FROM ai_knowledge WHERE tenant_id=? AND status=1",
                (rs, i) -> {
                    AiKnowledge k = new AiKnowledge();
                    k.setId(rs.getLong("id"));
                    k.setTenantId(rs.getLong("tenant_id"));
                    k.setCategory(rs.getString("category"));
                    k.setQuestion(rs.getString("question"));
                    k.setAnswer(rs.getString("answer"));
                    k.setKeywords(rs.getString("keywords"));
                    k.setHitCount(rs.getInt("hit_count"));
                    k.setStatus(rs.getInt("status"));
                    return k;
                },
                tenantId
        );
        Set<String> qTokens = tokenize(question);
        List<RagHit> hits = new ArrayList<>();
        for (AiKnowledge k : rows) {
            Set<String> cand = tokenize(k.getQuestion() + " " + nullToEmpty(k.getKeywords()));
            double score = jaccard(qTokens, cand);
            if (containsBoost(question, k)) score = Math.min(1.0, score + 0.25);
            RagHit hit = new RagHit();
            hit.setKnowledge(k);
            hit.setScore(score);
            hit.setAccepted(score >= minScore());
            hit.setHitType("qa");
            hits.add(hit);
        }
        hits.sort(Comparator.comparingDouble(RagHit::getScore).reversed());
        return hits;
    }

    private RagHit bestDocHit(Long tenantId, String question) {
        List<RagHit> list = listDocHits(tenantId, question);
        return list.isEmpty() ? null : list.get(0);
    }

    private List<RagHit> listDocHits(Long tenantId, String question) {
        float[] qv = LocalTextEmbedder.embed(question);
        double threshold = minScore();
        List<RagHit> hits = new ArrayList<>();
        try {
            List<MapRow> rows = jdbcTemplate.query(
                    "SELECT c.doc_id,c.content,c.embedding_blob,d.title,d.file_name,d.category " +
                            "FROM knowledge_chunks c JOIN knowledge_documents d ON d.id=c.doc_id " +
                            "WHERE c.tenant_id=? AND d.index_status='READY' AND c.embedding_blob IS NOT NULL " +
                            "AND (d.doc_status IS NULL OR d.doc_status='ACTIVE')",
                    (rs, i) -> new MapRow(
                            rs.getLong("doc_id"),
                            rs.getString("content"),
                            rs.getBytes("embedding_blob"),
                            rs.getString("title"),
                            rs.getString("file_name"),
                            rs.getString("category")
                    ),
                    tenantId
            );
            for (MapRow r : rows) {
                float[] ev = LocalTextEmbedder.fromBlob(r.blob);
                double score = LocalTextEmbedder.cosine(qv, ev);
                if (containsAnyKeyword(question, r.content)) {
                    score = Math.min(1.0, score + 0.08);
                }
                RagHit hit = new RagHit();
                hit.setHitType("document");
                hit.setDocId(r.docId);
                hit.setDocTitle(r.title);
                hit.setDocFileName(r.fileName);
                hit.setDocCategory(r.category);
                hit.setChunkContent(r.content);
                hit.setScore(score);
                hit.setAccepted(score >= threshold);
                hits.add(hit);
            }
        } catch (Exception ignored) {
            // 表未就绪时降级为空
        }
        hits.sort(Comparator.comparingDouble(RagHit::getScore).reversed());
        return hits;
    }

    private record MapRow(Long docId, String content, byte[] blob, String title, String fileName, String category) {}

    private boolean containsBoost(String q, AiKnowledge k) {
        String ql = q == null ? "" : q.toLowerCase(Locale.ROOT);
        if (k.getKeywords() != null) {
            for (String kw : k.getKeywords().split("[,，\\s]+")) {
                if (!kw.isBlank() && ql.contains(kw.toLowerCase(Locale.ROOT))) return true;
            }
        }
        String qq = nullToEmpty(k.getQuestion()).toLowerCase(Locale.ROOT);
        return !qq.isBlank() && (ql.contains(qq) || qq.contains(ql));
    }

    private boolean containsAnyKeyword(String question, String content) {
        if (question == null || content == null) return false;
        String c = content.toLowerCase(Locale.ROOT);
        for (String t : tokenize(question)) {
            if (t.length() >= 2 && c.contains(t)) return true;
        }
        return false;
    }

    private Set<String> tokenize(String text) {
        Set<String> set = new HashSet<>();
        if (text == null) return set;
        // 去掉标点；中文引号用 Unicode 转义，避免打断 Java 字符串字面量
        String t = text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}，。！？、；：（）【】\\u201c\\u201d\\u2018\\u2019]", " ");
        for (String p : t.split("\\s+")) {
            if (p.length() >= 2) set.add(p);
        }
        String compact = t.replaceAll("\\s+", "");
        for (int i = 0; i < compact.length() - 1; i++) {
            set.add(compact.substring(i, i + 2));
        }
        return set;
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int inter = 0;
        for (String x : a) if (b.contains(x)) inter++;
        int union = a.size() + b.size() - inter;
        return union == 0 ? 0 : (double) inter / union;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
