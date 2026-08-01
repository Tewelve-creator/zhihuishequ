package com.community.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chat_log")
public class AiChatLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String userNickname;
    private String question;
    private String answer;
    private String source;
    private Integer transferHuman;
    /** 1 好评 / 2 差评 */
    private Integer rating;
    private String ratingRemark;
    /** RAG 相似度得分 */
    private Double ragScore;
    /** RAG 召回片段 */
    private String ragChunk;
    /** RAG 命中文档标题 */
    private String ragDocTitle;
    private LocalDateTime createTime;
}
