package com.yangaobo.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alipay.easysdk.factory.Factory;
import com.alipay.easysdk.kernel.Config;
import com.alipay.easysdk.payment.facetoface.models.AlipayTradePrecreateResponse;
import com.mybatisflex.core.paginate.Page;
import com.yangaobo.constant.ML;
import com.yangaobo.dto.*;
import com.yangaobo.exception.ServiceException;
import com.yangaobo.properties.AlipayProperties;
import com.yangaobo.result.Result;
import com.yangaobo.result.ResultCode;
import com.yangaobo.vo.PageVO;
import com.yangaobo.security.RequireAdmin;
import com.yangaobo.security.SecurityContext;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.yangaobo.entity.Order;
import com.yangaobo.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单表 控制层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@RestController
@Slf4j
@Tag(name = "订单表接口")
@RequestMapping("/api/v1/order")
public class OrderController {

    @Resource
    private OrderService orderService;

    @Autowired
    private AlipayProperties alipayProperties;

    @Autowired
    private Config alipaySdkConfig;

    @Operation(summary = "新增 - 单条新增", description = "新增一条订单记录")
    @PostMapping("insert")
    @RequireAdmin
    public boolean insert(@Validated @RequestBody OrderInsertDTO dto) {
        return orderService.insert(dto);
    }

    @Operation(summary = "查询 - 单条查询", description = "按主键查询一条订单记录")
    @GetMapping("select/{id}")
    @RequireAdmin
    public Order select(@PathVariable("id") Long id) {
        return orderService.select(id);
    }

    @Operation(summary = "查询 - 分页查询", description = "分页查询订单记录")
    @GetMapping("page")
    public PageVO<Order> page(@Validated OrderPageDTO dto) {
        return orderService.page(dto);
    }

    @Operation(summary = "修改 - 单条修改", description = "按主键修改一条订单记录")
    @PutMapping("update")
    @RequireAdmin
    public boolean update(@Validated @RequestBody OrderUpdateDTO dto) {
        return orderService.update(dto);
    }

    @Operation(summary = "删除 - 单条删除", description = "按主键删除一条订单记录")
    @DeleteMapping("delete/{id}")
    public boolean delete(@PathVariable("id") Long id) {
        return orderService.delete(id);
    }

    @Operation(summary = "删除 - 批量删除", description = "按主键批量删除订单记录")
    @DeleteMapping("deleteBatch")
    @RequireAdmin
    public boolean deleteBatch(@RequestParam("ids") List<Long> ids) {
        return orderService.deleteBatch(ids);
    }

    @Operation(summary = "查询 - 统计数据", description = "查询订单相关的统计数据")
    @GetMapping("statistics")
    @RequireAdmin
    public Map<String, Object> statistics() {
        return orderService.statistics();
    }

    @Operation(summary = "添加 - 预支付", description = "用户下订单，创建一个未支付的订单")
    @PostMapping("/prePay")
    public Object prePay(@RequestBody PrePayDTO dto) {
        return new Result<>(orderService.prePay(dto));
    }

    @SneakyThrows
    @Operation(summary = "查询 - 预支付二维码", description = "获取预支付二维码")
    @PostMapping("/getQrCode")
    public void getQrCode(HttpServletResponse resp, @RequestBody QrCodeDTO qrCodeDTO) {
        if (!alipayProperties.isConfigured()) {
            writePaymentError(resp, "支付宝沙箱配置未完成，请检查 ALIPAY_APP_ID、支付宝公钥、应用私钥和回调地址");
            return;
        }
        Order order = orderService.selectBySn(qrCodeDTO.getSn());
        if (order == null) {
            throw new ServiceException(ResultCode.ORDER_NOT_FOUND, "订单不存在");
        }
        SecurityContext.requireOwner(order.getFkUserId());
        // 初始化配置
        Factory.setOptions(alipaySdkConfig);
        try {
            // 同一商户订单号的预创建请求具备幂等性，不应在生成二维码前撤销交易。
            AlipayTradePrecreateResponse alipayTradePrecreateResponse = Factory.Payment
                    .FaceToFace()
                    .preCreate(
                            "ML订单支付",
                            qrCodeDTO.getSn(),
                            order.getPayAmount().toPlainString());
            // 解析预支付响应
            JSONObject alipayResponse = JSONUtil.parseObj(alipayTradePrecreateResponse.getHttpBody())
                    .getJSONObject("alipay_trade_precreate_response");
            // 检查支付宝 API 是否返回成功
            String code = alipayResponse.getStr("code");
            if (!"10000".equals(code)) {
                String subMessage = alipayResponse.getStr("sub_msg");
                String message = StrUtil.isNotBlank(subMessage) ? subMessage : alipayResponse.getStr("msg");
                log.warn("Alipay pre-create rejected, orderSn={}, code={}, subCode={}, message={}",
                        qrCodeDTO.getSn(), code, alipayResponse.getStr("sub_code"), message);
                writePaymentError(resp, "支付宝预支付失败：" + message);
                return;
            }
            // 校验二维码数据是否存在
            String qrCode = alipayResponse.getStr("qr_code");
            if (StrUtil.isEmpty(qrCode)) {
                writePaymentError(resp, "支付宝预支付返回二维码为空");
                return;
            }
            log.info("Created Alipay sandbox QR code, orderSn={}, amount={}",
                    qrCodeDTO.getSn(), order.getPayAmount());
            // 设置响应头：响应类型为图片，不缓存（addHeader 项是为了兼容老版本浏览器）
            resp.setContentType(MediaType.IMAGE_JPEG_VALUE);
            resp.setDateHeader("Expires", 0);
            resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            resp.addHeader("Cache-Control", "post-check=0, pre-check=0");
            // 生成二维码图片
            BufferedImage bufferedImage = QrCodeUtil.generate(qrCode, new QrConfig(300, 200));
            // 将图片写入响应输出流
            try (ServletOutputStream outputStream = resp.getOutputStream()) {
                ImageIO.write(bufferedImage, "jpg", outputStream);
                outputStream.flush();
            }
        } catch (Exception e) {
            log.error("Failed to create Alipay QR code for order {}", qrCodeDTO.getSn(), e);
            writePaymentError(resp, "获取二维码失败，请检查支付宝沙箱配置");
        }
    }

    private void writePaymentError(HttpServletResponse response, String message) {
        try {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setCharacterEncoding("UTF-8");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(JSONUtil.toJsonStr(
                    new Result<>(ResultCode.SERVER_ERROR, message)));
        } catch (Exception exception) {
            log.error("Failed to write payment error response", exception);
        }
    }

    @Operation(summary = "回调 - 预支付回调", description = "支付成功后，支付宝自动回调的接口")
    @PostMapping("/prePayNotify")
    public String prePayNotify(HttpServletRequest request) {
        // 初始化支付宝配置
        Factory.setOptions(alipaySdkConfig);

        // 将请求参数转为 Map，用于签名验证
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            String name = entry.getKey();
            String[] values = entry.getValue();
            params.put(name, (values != null && values.length > 0) ? values[0] : "");
        }

        // ========== 1. 验证支付宝签名 ==========
        try {
            boolean verified = Factory.Payment.Common().verifyNotify(params);
            if (!verified) {
                log.warn("Rejected Alipay callback because signature verification failed, orderSn={}",
                        params.get("out_trade_no"));
                return "failure";
            }
        } catch (Exception e) {
            log.error("Alipay callback signature verification failed", e);
            return "failure";
        }

        // ========== 2. 验证 app_id ==========
        String appId = params.get("app_id");
        if (!alipayProperties.getAppId().equals(appId)) {
            log.warn("Rejected Alipay callback because appId does not match, actualAppId={}", appId);
            return "failure";
        }

        // ========== 3. 解析业务参数 ==========
        String tradeStatus = params.get("trade_status");
        String sn = params.get("out_trade_no");
        String totalAmount = params.get("total_amount");
        String tradeNo = params.get("trade_no");

        log.info("Received Alipay callback, orderSn={}, tradeNo={}, status={}", sn, tradeNo, tradeStatus);

        // ========== 4. 判断交易状态 ==========
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            // 4.1 校验订单是否存在
            Order order = orderService.selectBySn(sn);
            if (order == null) {
                log.warn("Rejected Alipay callback because order does not exist, orderSn={}", sn);
                return "failure";
            }
            // 4.2 校验金额是否匹配（允许 0.01 的浮点误差）
            BigDecimal expectedAmount = order.getPayAmount();
            BigDecimal notifiedAmount;
            try {
                notifiedAmount = new BigDecimal(totalAmount);
            } catch (NumberFormatException exception) {
                log.warn("Rejected Alipay callback because amount is invalid, orderSn={}", sn);
                return "failure";
            }
            if (expectedAmount.compareTo(notifiedAmount) != 0) {
                log.warn("Rejected Alipay callback because amount does not match, orderSn={}", sn);
                return "failure";
            }
            // 4.3 防止重复通知：已支付的订单直接返回成功
            if (ML.Order.PAID.equals(order.getStatus())) {
                log.info("Ignored duplicate Alipay callback for paid order, orderSn={}", sn);
                return "success";
            }
            // 4.4 更新订单状态为已支付
            orderService.updateStatusBySn(sn, ML.Order.PAID, ML.Order.ALI_PAY);
            log.info("Marked order as paid from Alipay callback, orderSn={}, tradeNo={}", sn, tradeNo);
        }

        return "success";
    }

    @Operation(summary = "修改 - 取消订单", description = "根据订单号取消未支付订单")
    @PostMapping("/cancelBySn/{sn}")
    public boolean cancelBySn(@PathVariable("sn") String sn) {
        Order order = orderService.selectBySn(sn);
        if (order == null) {
            throw new ServiceException(ResultCode.ORDER_NOT_FOUND, "订单不存在");
        }
        SecurityContext.requireOwner(order.getFkUserId());
        return orderService.updateStatusBySn(sn, ML.Order.CANCEL, ML.Order.NO_PAY);
    }

    @Operation(summary = "查询 - 订单状态", description = "根据订单号查询订单状态（是否已支付）")
    @GetMapping("/checkStatusBySn/{sn}")
    public boolean checkStatusBySn(@PathVariable("sn") String sn) {
        Order order = orderService.selectBySn(sn);
        if (order == null) {
            throw new ServiceException(ResultCode.ORDER_NOT_FOUND, "订单不存在");
        }
        SecurityContext.requireOwner(order.getFkUserId());
        return orderService.checkStatusBySn(sn);
    }

}
