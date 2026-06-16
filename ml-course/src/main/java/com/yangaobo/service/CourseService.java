package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.CourseInsertDTO;
import com.yangaobo.dto.CoursePageDTO;
import com.yangaobo.dto.CourseSimpleListVO;
import com.yangaobo.dto.CourseUpdateDTO;
import com.yangaobo.entity.Course;
import com.yangaobo.es.CourseDoc;
import com.yangaobo.vo.PageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 课程表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface CourseService extends IService<Course> {
    boolean insert(CourseInsertDTO dto);
    Course select(Long id);
    List<CourseSimpleListVO> simpleList();
    PageVO<Course> page(CoursePageDTO dto);
    boolean update(CourseUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);

    /**
     * 上传课程封面图片
     *
     * @param newFile 封面图片文件
     * @param id      课程主键
     * @return 文件名
     */
    String uploadCover(MultipartFile newFile, Long id);

    /**
     * 上传课程摘要图片
     *
     * @param newFile 摘要图片文件
     * @param id      课程主键
     * @return 文件名
     */
    String uploadSummary(MultipartFile newFile, Long id);

    /**
     * 分页搜索课程记录
     *
     * @param dto 课程搜索DTO
     * @return 搜索结果
     */
    PageVO<CourseDoc> search(CoursePageDTO dto);

}
