package com.yangaobo.service.ai;

import com.mybatisflex.core.query.QueryChain;
import com.yangaobo.dto.ai.AiCursorPage;
import com.yangaobo.dto.ai.ArticleKnowledgeDTO;
import com.yangaobo.dto.ai.NoticeKnowledgeDTO;
import com.yangaobo.dto.ai.SaleSearchHitDTO;
import com.yangaobo.entity.Article;
import com.yangaobo.entity.Notice;
import com.yangaobo.mapper.ArticleMapper;
import com.yangaobo.mapper.NoticeMapper;
import com.yangaobo.service.ArticleService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.yangaobo.entity.table.ArticleTableDef.ARTICLE;
import static com.yangaobo.entity.table.NoticeTableDef.NOTICE;

@Service
public class SaleAiQueryService {

    private final ArticleService articleService;
    private final ArticleMapper articleMapper;
    private final NoticeMapper noticeMapper;

    public SaleAiQueryService(
            ArticleService articleService,
            ArticleMapper articleMapper,
            NoticeMapper noticeMapper) {
        this.articleService = articleService;
        this.articleMapper = articleMapper;
        this.noticeMapper = noticeMapper;
    }

    public ArticleKnowledgeDTO getArticle(Long id) {
        return toArticle(articleService.select(id));
    }

    public NoticeKnowledgeDTO getNotice(Long id) {
        Notice notice = noticeMapper.selectOneById(id);
        if (notice == null) {
            throw new com.yangaobo.exception.ServiceException(
                    com.yangaobo.result.ResultCode.NOTICE_NOT_FOUND,
                    id + "号通知数据不存在");
        }
        return toNotice(notice);
    }

    public AiCursorPage<ArticleKnowledgeDTO> articleKnowledge(Long cursor, int size) {
        List<ArticleKnowledgeDTO> fetched = QueryChain.of(articleMapper)
                .where(ARTICLE.ID.gt(cursor))
                .orderBy(ARTICLE.ID.asc())
                .limit(size + 1)
                .list()
                .stream()
                .map(this::toArticle)
                .toList();
        return AiCursorPage.of(fetched, size, ArticleKnowledgeDTO::id);
    }

    public AiCursorPage<NoticeKnowledgeDTO> noticeKnowledge(Long cursor, int size) {
        List<NoticeKnowledgeDTO> fetched = QueryChain.of(noticeMapper)
                .where(NOTICE.ID.gt(cursor))
                .orderBy(NOTICE.ID.asc())
                .limit(size + 1)
                .list()
                .stream()
                .map(this::toNotice)
                .toList();
        return AiCursorPage.of(fetched, size, NoticeKnowledgeDTO::id);
    }

    public List<SaleSearchHitDTO> search(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        List<SaleSearchHitDTO> results = new ArrayList<>(limit * 2);
        QueryChain.of(articleMapper)
                .where(ARTICLE.TITLE.like(keyword)
                        .or(ARTICLE.CONTENT.like(keyword)))
                .orderBy(ARTICLE.UPDATED.desc(), ARTICLE.ID.desc())
                .limit(limit)
                .list()
                .forEach(article -> results.add(new SaleSearchHitDTO(
                        "ARTICLE",
                        article.getId(),
                        article.getTitle(),
                        article.getContent(),
                        article.getUpdated())));

        QueryChain.of(noticeMapper)
                .where(NOTICE.CONTENT.like(keyword))
                .orderBy(NOTICE.UPDATED.desc(), NOTICE.ID.desc())
                .limit(limit)
                .list()
                .forEach(notice -> results.add(new SaleSearchHitDTO(
                        "NOTICE",
                        notice.getId(),
                        "公告 " + notice.getId(),
                        notice.getContent(),
                        notice.getUpdated())));

        return results.stream()
                .sorted(Comparator.comparing(
                        SaleSearchHitDTO::updated,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private ArticleKnowledgeDTO toArticle(Article article) {
        return new ArticleKnowledgeDTO(
                article.getId(),
                article.getTitle(),
                article.getContent(),
                article.getUpdated());
    }

    private NoticeKnowledgeDTO toNotice(Notice notice) {
        return new NoticeKnowledgeDTO(
                notice.getId(),
                notice.getContent(),
                notice.getUpdated());
    }
}
