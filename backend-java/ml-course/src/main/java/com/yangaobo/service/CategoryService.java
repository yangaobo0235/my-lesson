package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.CategoryInsertDTO;
import com.yangaobo.dto.CategoryPageDTO;
import com.yangaobo.dto.CategorySimpleListVO;
import com.yangaobo.dto.CategoryUpdateDTO;
import com.yangaobo.entity.Category;
import com.yangaobo.vo.PageVO;

import java.util.List;

/**
 * 课程类别表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface CategoryService extends IService<Category> {
    boolean insert(CategoryInsertDTO dto);
    Category select(Long id);
    List<CategorySimpleListVO> simpleList();
    PageVO<Category> page(CategoryPageDTO dto);
    boolean update(CategoryUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);

}
