package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.ArticleInsertDTO;
import com.yangaobo.dto.ArticlePageDTO;
import com.yangaobo.dto.ArticleUpdateDTO;
import com.yangaobo.entity.Article;
import com.yangaobo.vo.PageVO;

import java.util.List;

/**
 * 新闻表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface ArticleService extends IService<Article> {
    boolean insert(ArticleInsertDTO dto);
    Article select(Long id);
    PageVO<Article> page(ArticlePageDTO dto);
    boolean update(ArticleUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);

    /**
     * 查看前N条新闻记录，根据序号升序，序号相同根据ID降序
     *
     * @param n 前N条
     * @return 前N条新闻记录
     */
    List<Article> top(Long n);

}
