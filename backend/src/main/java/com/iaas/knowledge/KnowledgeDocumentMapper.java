package com.iaas.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    /**
     * 中文全文检索。
     *
     * <p>两个必须：建表时指定 ngram 分词器，否则 MySQL 按空格切词，
     * 中文整句会被当成一个词；这里用**自然语言模式**而不是布尔模式。
     *
     * <p>布尔模式会把整句当成一个必须完整出现的词组，「缓考能申请几门」
     * 于是 0 命中；自然语言模式会把查询同样切成 n-gram 再算相关度。
     * 这一点是实测确认的，不是猜的。
     */
    @Select("""
            SELECT c.*, MATCH(c.content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) AS score
            FROM knowledge_chunk c
            JOIN knowledge_document d ON d.id = c.document_id
            WHERE MATCH(c.content) AGAINST(#{query} IN NATURAL LANGUAGE MODE)
              AND d.status = '生效'
              AND d.visibility IN ('PUBLIC','CAMPUS')
            ORDER BY score DESC
            LIMIT #{limit}
            """)
    List<ChunkHit> searchByKeyword(String query, int limit);

    /** 检索命中的切片与相关度分数。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    class ChunkHit extends KnowledgeChunk {
        private Double score;
    }
}
