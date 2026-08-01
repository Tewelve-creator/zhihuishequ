package com.community.ai.rag;

import com.community.ai.entity.AiKnowledge;
import lombok.Data;

@Data
public class RagHit {
    private AiKnowledge knowledge;
    private double score;
    private boolean accepted;

    /** qa | document */
    private String hitType = "qa";
    private String docTitle;
    private String docFileName;
    private String docCategory;
    private String chunkContent;
    private Long docId;
}
