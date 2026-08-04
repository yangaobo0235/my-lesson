package com.yangaobo.service;

import com.mybatisflex.core.service.IService;
import com.yangaobo.dto.BannerInsertDTO;
import com.yangaobo.dto.BannerPageDTO;
import com.yangaobo.dto.BannerUpdateDTO;
import com.yangaobo.entity.Banner;
import com.yangaobo.vo.PageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 横幅表 服务层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
public interface BannerService extends IService<Banner> {
    boolean insert(BannerInsertDTO dto);
    Banner select(Long id);
    PageVO<Banner> page(BannerPageDTO dto);
    boolean update(BannerUpdateDTO dto);
    boolean delete(Long id);
    boolean deleteBatch(List<Long> ids);

    /**
     * 查看前N条横幅记录，根据序号升序，序号相同根据ID降序
     *
     * @param n 前N条
     * @return 前N条横幅记录
     */
    List<Banner> top(Long n);

    /**
     * 上传横幅轮播图片
     *
     * @param newFile 轮播图片文件
     * @param id      横幅主键
     * @return 文件名
     */
    String uploadBanner(MultipartFile newFile, Long id);

}
