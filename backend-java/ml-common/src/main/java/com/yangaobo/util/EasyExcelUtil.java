package com.yangaobo.util;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author 杨奥博
 */
public class EasyExcelUtil {  

	// 定义每个sheet的最大数据量（行）
    private static final int BATCH_SIZE = 100;  

    /**
     * 生成Excel报表并自动下载
     *
     * @param resp      HTTP响应对象
     * @param fileName  Excel文件名，可以省略 .xlsx 后缀
     * @param data      数据
     */
    @SneakyThrows
    public static void download(HttpServletResponse resp,
                                String fileName,
                                Collection<?> data) {
		// 空数据处理
        if (CollectionUtil.isEmpty(data)) return;
        // 处理文件名：若未包含.xlsx则添加，否则保持原文件名
        fileName = fileName.endsWith(".xlsx") ? fileName : fileName + ".xlsx";
        // 对文件名重新编码，以避免文件名中文乱码问题
        fileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        // 设置响应头
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        resp.setHeader("Content-disposition", "attachment;filename=" + fileName);
        // 获取数据类型
        Class<?> type = data.iterator().next().getClass();
        // 准备数据分批
        List<Object> allData = new ArrayList<>(data);
        int totalSize = allData.size();
        // sheet索引
        int sheetIndex = 1;
        // 创建写入器
        try (ExcelWriter excelWriter = EasyExcel.write(resp.getOutputStream(), type)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .build()) {
            // 循环分片处理
            for (int i = 0; i < totalSize; i += BATCH_SIZE) {
                // 计算当前分片的结束索引：确保结束索引不会超过数据总量，避免数组越界。
                int end = Math.min(i + BATCH_SIZE, totalSize);
                // 截取当前分片数据：从索引 i 到 end（不包含）
                List<Object> batchData = allData.subList(i, end);
                // 创建sheet，指定索引和名称
                WriteSheet writeSheet = EasyExcel.writerSheet(sheetIndex, "sheet" + String.format("%02d", sheetIndex)).build();
                // 将数据写入当前sheet数据
                excelWriter.write(batchData, writeSheet);
                // sheet索引自增
                sheetIndex++;
            }
        }
    }
}
