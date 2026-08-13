package com.yangaobo.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.yangaobo.component.MyRedis;
import com.yangaobo.constant.ML;
import com.yangaobo.entity.Course;
import com.yangaobo.entity.Seckill;
import com.yangaobo.entity.SeckillDetail;
import com.yangaobo.exception.ServiceException;
import com.yangaobo.feign.CourseFeign;
import com.yangaobo.mapper.SeckillMapper;
import com.yangaobo.result.Result;
import com.yangaobo.result.ResultCode;
import com.alibaba.fastjson.JSON;
import com.mybatisflex.core.query.QueryChain;
import com.mybatisflex.core.update.UpdateChain;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
//import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.yangaobo.entity.table.SeckillTableDef.SECKILL;
import static com.mybatisflex.core.query.QueryMethods.curDate;
import static com.mybatisflex.core.query.QueryMethods.date;

/**
 * @author 杨奥博
 */
@Slf4j
@Component
public class InitSeckillJob {

    private static final int STOCK_CACHE_TTL_HOURS = 12;
    private static final String COURSE_CACHE_PREFIX =
            ML.Redis.SECKILL_COURSE_INFO_PREFIX;

    @Resource
    private MyRedis myRedis;

    @Resource
    private SeckillMapper seckillMapper;

    @Resource
    private CourseFeign courseFeign;

//    @Resource
//    private RocketMQTemplate rocketMQTemplate;

    @XxlJob("initSeckill")
    public void initSeckill() {
        log.info("秒杀活动预热开始");

        // 查询当天的所有秒杀活动
        List<Seckill> todaySeckills = QueryChain.of(seckillMapper)
                .where(date(SECKILL.START_TIME).eq(curDate()))
                .orderBy(SECKILL.START_TIME.asc())
                .withRelations()
                .list();
        // 判断是否查询到秒杀活动
        if (CollUtil.isEmpty(todaySeckills)) {
            XxlJobHelper.log("今日暂无秒杀活动，缓存预热跳过");
            XxlJobHelper.handleSuccess("秒杀活动预热完成（无数据）");
            return;
        }

        // 查询秒杀商品ID列表（便于后续批量查询）
        Set<Long> courseIds = new LinkedHashSet<>();
        todaySeckills.forEach(seckill -> {
            List<SeckillDetail> seckillDetails = seckill.getSeckillDetails();
            if (ObjectUtil.isNotEmpty(seckillDetails)) {
                seckillDetails.forEach(seckillDetail -> {
                    Long fkCourseId = seckillDetail.getFkCourseId();
                    Integer skCount = seckillDetail.getSkCount();
                    if (fkCourseId == null || skCount == null || skCount <= 0) {
                        throw new ServiceException(
                                ResultCode.ILLEGAL_PARAM,
                                "秒杀课程库存配置无效");
                    }
                    // 预热库存，秒杀入口只访问 Redis，不在高峰期回源数据库。
                    myRedis.setEx(
                            ML.Redis.SECKILL_COURSE_COUNT_PREFIX + fkCourseId,
                            skCount.toString(),
                            STOCK_CACHE_TTL_HOURS,
                            TimeUnit.HOURS);
                    // 将商品信息加入List中
                    courseIds.add(fkCourseId);
                });
            }
            int detailCount = seckillDetails == null ? 0 : seckillDetails.size();
            XxlJobHelper.log(
                    "秒杀活动 {} 查询到商品 {} 个",
                    seckill.getId(),
                    detailCount);
        });

        // 预热秒杀商品信息
        courseIds.forEach(courseId -> {
            // 远程调用 - 查询课程数据
            Result<Course> courseResult = courseFeign.select(courseId);
            if (ObjectUtil.isNull(courseResult)) {
                throw new ServiceException(ResultCode.OPEN_FEIGN_ERROR, "课程微服务远程调用失败，请联系管理员。");
            }
            Course course = courseResult.getData();
            if (ObjectUtil.isNull(course)) {
                throw new ServiceException(ResultCode.COURSE_NOT_FOUND, courseId + "号课程数据不存在");
            }
            // 预热商品信息，与库存使用相同的活动窗口。
            myRedis.setEx(
                    COURSE_CACHE_PREFIX + courseId,
                    JSON.toJSONString(course),
                    STOCK_CACHE_TTL_HOURS,
                    TimeUnit.HOURS);
            XxlJobHelper.log("秒杀活动商品 {} 预热完成", courseId);
        });

        log.info("秒杀活动预热结束，共预热 {} 个商品", courseIds.size());
        XxlJobHelper.handleSuccess("秒杀活动预热完成（共预热 " + courseIds.size() + " 个商品）");
    }

    @XxlJob("startMorningSeckill")
    public void startMorningSeckill() {
        log.info("准备开启今日上午场的秒杀活动");

        // 修改当天的上午场的秒杀活动状态
        UpdateChain.of(seckillMapper)
                .set(SECKILL.STATUS, ML.Seckill.STARTED)
                .where(date(SECKILL.START_TIME).eq(curDate()))
                .and(SECKILL.TITLE.eq("上午场"))
                .update();

        log.info("上午场的秒杀活动已开启");
        XxlJobHelper.handleSuccess("上午场的秒杀活动开启成功");
    }

    @XxlJob("stopMorningSeckill")
    public void stopMorningSeckill() {
        log.info("准备关闭今日上午场的秒杀活动");

        // 修改当天的上午场的秒杀活动状态
        UpdateChain.of(seckillMapper)
                .set(SECKILL.STATUS, ML.Seckill.ENDED)
                .where(date(SECKILL.START_TIME).eq(curDate()))
                .and(SECKILL.TITLE.eq("上午场"))
                .update();

        log.info("上午场的秒杀活动已关闭");
        XxlJobHelper.handleSuccess("上午场的秒杀活动关闭成功");
    }

    @XxlJob("startNoonSeckill")
    public void startNoonSeckill() {
        log.info("准备开启今日中午场的秒杀活动");

        // 修改当天的中午场的秒杀活动状态
        UpdateChain.of(seckillMapper)
                .set(SECKILL.STATUS, ML.Seckill.STARTED)
                .where(date(SECKILL.START_TIME).eq(curDate()))
                .and(SECKILL.TITLE.eq("中午场"))
                .update();

        log.info("中午场的秒杀活动已开启");
        XxlJobHelper.handleSuccess("中午场的秒杀活动开启成功");
    }

    @XxlJob("stopNoonSeckill")
    public void stopNoonSeckill() {
        log.info("准备关闭今日中午场的秒杀活动");

        // 修改当天的中午场的秒杀活动状态
        UpdateChain.of(seckillMapper)
                .set(SECKILL.STATUS, ML.Seckill.ENDED)
                .where(date(SECKILL.START_TIME).eq(curDate()))
                .and(SECKILL.TITLE.eq("中午场"))
                .update();

        log.info("中午场的秒杀活动已关闭");
        XxlJobHelper.handleSuccess("中午场的秒杀活动关闭成功");
    }

    @XxlJob("startAfterNoonSeckill")
    public void startAfterNoonSeckill() {
        log.info("准备开启今日下午场的秒杀活动");

        // 修改当天的下午场的秒杀活动状态
        UpdateChain.of(seckillMapper)
                .set(SECKILL.STATUS, ML.Seckill.STARTED)
                .where(date(SECKILL.START_TIME).eq(curDate()))
                .and(SECKILL.TITLE.eq("下午场"))
                .update();

        log.info("下午场的秒杀活动已开启");
        XxlJobHelper.handleSuccess("下午场的秒杀活动开启成功");
    }

    @XxlJob("stopAfterNoonSeckill")
    public void stopAfterNoonSeckill() {
        log.info("准备关闭今日下午场的秒杀活动");

        // 修改当天的下午场的秒杀活动状态
        UpdateChain.of(seckillMapper)
                .set(SECKILL.STATUS, ML.Seckill.ENDED)
                .where(date(SECKILL.START_TIME).eq(curDate()))
                .and(SECKILL.TITLE.eq("下午场"))
                .update();

        log.info("下午场的秒杀活动已关闭");
        XxlJobHelper.handleSuccess("下午场的秒杀活动关闭成功");
    }
}
