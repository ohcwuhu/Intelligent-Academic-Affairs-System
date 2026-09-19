package com.iaas.knowledge;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语料入库：把规章原文切成带层级路径的切片。
 *
 * <p>学生手册没有任何 Markdown 标题，层级完全靠「第X部分 / 第X章 / 第X节 / 第X条」
 * 这套中文编号表达，所以解析器得自己把层级重建出来。这正是真实规章文档与
 * 网上示例语料的区别：示例语料格式干净，真实文档要靠规则去认。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeIngestService {

    /** 一条超过这个长度就按「（一）（二）」拆片，避免长条款稀释检索相关度。 */
    private static final int MAX_CHUNK_CHARS = 420;

    private static final Pattern RE_PART = Pattern.compile("^\\s*第([一二三四五六七八九十]+)部分\\s*(.*)$");
    private static final Pattern RE_CHAPTER = Pattern.compile("^\\s*第([一二三四五六七八九十]+)章\\s*(.*)$");
    private static final Pattern RE_SECTION = Pattern.compile("^\\s*第([一二三四五六七八九十]+)节\\s*(.*)$");
    private static final Pattern RE_ARTICLE = Pattern.compile("^\\s*第([一二三四五六七八九十百]+)条\\s*(.*)$");
    private static final Pattern RE_SUBITEM = Pattern.compile("^\\s*[（(]([一二三四五六七八九十]+)[）)]\\s*(.*)$");

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;

    /** 最近一次灌入的文档 ID，供启动流程接着走发布。 */
    private volatile Long lastDocumentId;

    @Value("${iaas.knowledge.source-path:}")
    private String sourcePath;
    public boolean isEmpty() {
        return documentMapper.selectCount(
                Wrappers.<KnowledgeDocument>lambdaQuery().eq(KnowledgeDocument::getStatus, "生效")) == 0;
    }

    public Long lastIngestedDocumentId() {
        return lastDocumentId;
    }

    @Transactional(rollbackFor = Exception.class)
    public int ingestFromConfiguredPath() {
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new IllegalStateException("未配置 iaas.knowledge.source-path，无法灌入语料");
        }
        Path path = Path.of(sourcePath);
        if (!Files.isReadable(path)) {
            throw new IllegalStateException("语料文件不可读：" + sourcePath);
        }
        String title = stripExtension(path.getFileName().toString());
        try {
            return ingest(title, Files.readString(path, StandardCharsets.UTF_8), sourcePath);
        } catch (IOException e) {
            throw new IllegalStateException("读取语料失败：" + e.getMessage(), e);
        }
    }

    /**
     * 灌入一份文档。
     *
     * <p>元数据说明：这份语料本身没有文号与审核人，生效日期也只能填占位值。
     * 按发布门禁它只能进「草稿」——审核人留空是刻意的，因为确实没有人确认过。
     * 演示环境由启动流程带理由强行发布，风险接受声明进审计。
     */
    @Transactional(rollbackFor = Exception.class)
    public int ingest(String title, String text, String source) {
        log.warn("语料《{}》没有文号与审核人，生效日期用的是占位值；"
                + "按发布门禁它只能进草稿，必须有人确认后才能生效", title);

        List<KnowledgeDocument> old = documentMapper.selectList(
                Wrappers.<KnowledgeDocument>lambdaQuery().eq(KnowledgeDocument::getTitle, title));
        for (KnowledgeDocument d : old) {
            chunkMapper.delete(Wrappers.<KnowledgeChunk>lambdaQuery()
                    .eq(KnowledgeChunk::getDocumentId, d.getId()));
            documentMapper.deleteById(d.getId());
        }

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTitle(title);
        doc.setDocNo(null);
        doc.setDept("教务处");
        doc.setEffectiveDate(LocalDate.of(2024, 9, 1));
        doc.setScope("全校");
        doc.setVisibility("PUBLIC");
        doc.setStatus("草稿");
        doc.setAuditor(null);
        doc.setSourcePath(source);
        doc.setChunkCount(0);
        documentMapper.insert(doc);

        List<KnowledgeChunk> chunks = parse(text, doc.getId());
        for (KnowledgeChunk c : chunks) {
            chunkMapper.insert(c);
        }
        doc.setChunkCount(chunks.size());
        documentMapper.updateById(doc);
        lastDocumentId = doc.getId();
        log.info("语料《{}》入库完成，共 {} 片", title, chunks.size());
        return chunks.size();
    }

    /** 解析并切片。包级可见，便于单独测试。 */
    List<KnowledgeChunk> parse(String text, Long documentId) {
        String[] lines = text.replace("\r\n", "\n").split("\n");
        List<KnowledgeChunk> result = new ArrayList<>();

        String part = null;
        String chapter = null;
        String section = null;
        String articleNo = null;
        StringBuilder buffer = new StringBuilder();
        int seq = 0;

        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher mPart = RE_PART.matcher(line);
            Matcher mChapter = RE_CHAPTER.matcher(line);
            Matcher mSection = RE_SECTION.matcher(line);
            Matcher mArticle = RE_ARTICLE.matcher(line);

            if (mPart.matches()) {
                flush(result, documentId, part, chapter, section, articleNo, buffer, ++seq);
                part = "第" + mPart.group(1) + "部分 " + mPart.group(2).strip();
                chapter = null;
                section = null;
                articleNo = null;
                continue;
            }
            if (mChapter.matches() && !isArticleLike(line)) {
                flush(result, documentId, part, chapter, section, articleNo, buffer, ++seq);
                chapter = "第" + mChapter.group(1) + "章 " + mChapter.group(2).strip();
                section = null;
                articleNo = null;
                continue;
            }
            if (mSection.matches()) {
                flush(result, documentId, part, chapter, section, articleNo, buffer, ++seq);
                section = "第" + mSection.group(1) + "节 " + mSection.group(2).strip();
                articleNo = null;
                continue;
            }
            if (mArticle.matches()) {
                flush(result, documentId, part, chapter, section, articleNo, buffer, ++seq);
                articleNo = "第" + mArticle.group(1) + "条";
                String head = mArticle.group(2).strip();
                buffer = new StringBuilder(head.isEmpty() ? "" : head + "\n");
                continue;
            }
            buffer.append(line).append('\n');
        }
        flush(result, documentId, part, chapter, section, articleNo, buffer, ++seq);
        return result;
    }

    /** 「第一章」这类词可能出现在正文里，用长度区分标题行与引用行。 */
    private boolean isArticleLike(String line) {
        return line.length() > 30;
    }

    private void flush(List<KnowledgeChunk> out, Long docId, String part, String chapter,
                       String section, String articleNo, StringBuilder buffer, int seq) {
        String body = buffer.toString().strip();
        buffer.setLength(0);
        if (body.isEmpty()) {
            return;
        }
        String path = buildPath(part, chapter, section, articleNo);
        List<String> pieces = splitIfLong(body, articleNo);
        for (int i = 0; i < pieces.size(); i++) {
            String content = pieces.get(i);
            KnowledgeChunk c = new KnowledgeChunk();
            c.setDocumentId(docId);
            c.setHierarchyPath(path);
            c.setPart(part);
            c.setChapter(chapter);
            c.setSection(section);
            c.setArticleNo(articleNo);
            c.setSeq(seq * 100 + i);
            c.setContent(content);
            c.setCharCount(content.length());
            out.add(c);
        }
    }

    private String buildPath(String part, String chapter, String section, String articleNo) {
        List<String> seg = new ArrayList<>();
        if (part != null) seg.add(part);
        if (chapter != null) seg.add(chapter);
        if (section != null) seg.add(section);
        if (articleNo != null) seg.add(articleNo);
        return String.join(" > ", seg);
    }

    /**
     * 过长的条款按「（一）（二）」拆片。
     *
     * <p>标题行必须并入第一片而不是单独成片：只有一个标题的切片会命中大量查询，
     * 却给不出任何内容，是检索里最典型的噪音。这一点实测踩过——194 片里有 15 片
     * 是纯标题，剔掉之后降到 179 片。
     */
    private List<String> splitIfLong(String body, String articleNo) {
        String header = articleNo == null ? "" : articleNo + " ";
        if (body.length() <= MAX_CHUNK_CHARS) {
            return List.of(header + body);
        }
        String[] lines = body.split("\n");
        String title = "";
        int startIdx = 0;
        if (lines.length > 1 && !RE_SUBITEM.matcher(lines[0]).find()) {
            title = lines[0].strip();
            startIdx = 1;
        }
        String prefix = header + (title.isEmpty() ? "" : title + " ");

        List<String> pieces = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = startIdx; i < lines.length; i++) {
            String line = lines[i];
            boolean isSub = RE_SUBITEM.matcher(line).find();
            if (isSub && cur.length() > 0) {
                pieces.add(prefix + cur.toString().strip());
                cur.setLength(0);
            }
            cur.append(line).append('\n');
            if (cur.length() >= MAX_CHUNK_CHARS) {
                pieces.add(prefix + cur.toString().strip());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) {
            pieces.add(prefix + cur.toString().strip());
        }
        return pieces.isEmpty() ? List.of(header + body) : pieces;
    }

    private static String stripExtension(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }
}
