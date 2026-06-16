package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.*;
import com.yangaobo.entity.Episode;
import com.yangaobo.es.BarrageDoc;
import com.yangaobo.vo.EpisodeSimpleListVO;
import com.yangaobo.vo.PageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 集次表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface EpisodeService extends IService<Episode> {
    boolean insert(EpisodeInsertDTO dto);
    Episode select(Long id);
    List<EpisodeSimpleListVO> simpleList();
    PageVO<Episode> page(EpisodePageDTO dto);
    boolean update(EpisodeUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);

    /**
     * 上传视频封面图片
     *
     * @param newFile 封面图片文件
     * @param id      集次主键
     * @return 文件名
     */
    String uploadVideoCover(MultipartFile newFile, Long id);

    /**
     * 上传集次的视频文件
     *
     * @param newFile 视频文件
     * @param id      集次主键
     * @return 文件名
     */
    String uploadVideo(MultipartFile newFile, Long id);

    /**
     * 获取集次记录的Excel数据
     *
     * @return 用户集次的Excel数据列表
     */
    List<EpisodeExcelDTO> getExcelData();

    /**
     * 根据视频集主键查询该视频的弹幕，按弹幕发送时间字段正序
     * @param episodeId 视频集主键
     * @return 弹幕列表
     */
    List<BarrageDoc> searchBarrage(String episodeId);
}
