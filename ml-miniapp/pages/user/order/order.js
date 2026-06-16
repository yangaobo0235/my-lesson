import util from "../../../utils/util.js";
import api from "../../../utils/api.js";
import constant from "../../../utils/const.js";

Page({
    data: {
        MINIO_COURSE_COVER: constant.MINIO_COURSE_COVER, // 课程封面MINIO地址
        pageInfo: {pageNum: 1, pageSize: 10, totalPage: 0, totalRow: 0}, // 分页信息
        orders: null, // 订单记录
        // 支付弹窗相关
        payDialogShow: false,
        countDownShow: false,
        time: 15 * 60 * 1000,
        timeData: {},
        qrCodeImage: '',
        paySn: '',
        timer: null,
    },

    // 分页查询订单记录
    page: function () {
        let that = this;
        let pageNum = that.data.pageInfo['pageNum'];
        let pageSize = that.data.pageInfo['pageSize'];
        if(util.isNull(pageNum)) pageNum = 1;
        if(util.isNull(pageSize)) pageSize = 10;
        let params = {pageNum, pageSize, 'fkUserId': wx.getStorageSync('user').id};
        api.get('order', '/page', params).then(res => {
            that.setData({
                'orders': pageNum === 1 ? res['records'] : that.data.orders.concat(res['records']),
                'pageInfo.pageNum': res['pageNumber'],
                'pageInfo.pageSize': res['pageSize'],
                'pageInfo.totalPage': res['totalPage'],
                'pageInfo.totalRow': res['totalRow'],
            });
        }).catch(err => console.log(err));
    },

    // 列表触底时追查下一页记录
    pageMore: function () {
        let that = this;
        let pageNum = that.data.pageInfo['pageNum'];
        let totalPage = that.data.pageInfo['totalPage'];
        if (pageNum < totalPage) {
            this.setData({'pageInfo.pageNum': this.data.pageInfo['pageNum'] + 1});
            this.page();
        }
    },

    // 删除订单记录
    removeOrder: function (ev) {
        let id = ev.currentTarget.dataset['id'];
        util.confirm('整单课程将全部被删除，确定吗？', () => {
            api.del('order', `/delete/${id}`).then(res => {
                util.success('订单删除成功');
                this.setData({'orders': null});
                this.page();
            }).catch(err => console.error(err));
        });
    },

    // 播放视频
    playVideo: function (ev) {
        let courseId = ev.currentTarget.dataset['courseId'];
        let orderStatus = ev.currentTarget.dataset['orderStatus'];
        if (orderStatus === 0) {
            util.tip('请先付款');
        }else {
            util.page('/pages/user/order/player/player?courseId=' + courseId, false);
        }

    },

    // 去支付 — 为未支付订单生成二维码
    payOrder: function (ev) {
        let that = this;
        let sn = ev.currentTarget.dataset['sn'];
        let payAmount = parseFloat(ev.currentTarget.dataset['payAmount']);
        console.log('[支付] payOrder 触发', {sn, payAmount});
        that.setData({paySn: sn});
        wx.request({
            url: constant.GATEWAY_HOST + '/order-server/api/v1/order/getQrCode',
            method: 'POST',
            data: {sn: sn, payAmount: payAmount},
            header: {'token': wx.getStorageSync('token')},
            responseType: 'arraybuffer',
            success(res) {
                console.log('[支付] getQrCode 响应', {statusCode: res.statusCode, errMsg: res.errMsg});
                if (res.statusCode !== 200) {
                    const message = that.parsePaymentError(res.data);
                    console.warn('[支付] 状态码非200', {message});
                    util.tip(message);
                    return;
                }
                that.setData({
                    'qrCodeImage': 'data:image/png;base64,' + wx.arrayBufferToBase64(res.data),
                    'payDialogShow': true,
                    'countDownShow': true
                }, () => {
                    console.log('[支付] setData完成', {payDialogShow: that.data.payDialogShow, imageSize: res.data.byteLength});
                });
                // 每隔2秒轮询订单状态
                that.data.timer = setInterval(function () {
                    api.get('order', '/checkStatusBySn/' + that.data.paySn).then(res => {
                        if (res === true) {
                            util.success('支付成功');
                            clearInterval(that.data.timer);
                            that.setData({
                                'payDialogShow': false,
                                'countDownShow': false
                            });
                            // 刷新订单列表
                            that.setData({'orders': null});
                            that.page();
                        }
                    }).catch(err => util.error('查询订单状态失败', err));
                }, 2000);
            },
            fail(err) {
                console.error('[支付] getQrCode 请求失败', err);
                util.error('获取二维码失败: ' + (err.errMsg || '未知错误'));
            }
        });
    },

    parsePaymentError: function (data) {
        try {
            const text = typeof data === 'string'
                ? data
                : new TextDecoder('utf-8').decode(new Uint8Array(data));
            const body = JSON.parse(text);
            return body.message || '获取二维码失败，请稍后重试';
        } catch (err) {
            return '获取二维码失败，请稍后重试';
        }
    },

    // 取消支付
    cancelPay: function () {
        let that = this;
        let sn = that.data.paySn;
        clearInterval(that.data.timer);
        that.setData({
            'payDialogShow': false,
            'countDownShow': false
        });
        if (sn) {
            api.post('order', '/cancelBySn/' + sn).then(() => {
                util.success('已取消支付');
                that.setData({'orders': null});
                that.page();
            }).catch(err => console.error('取消订单失败', err));
        }
    },

    // 支付倒计时
    countDown: function (ev) {
        if (this.data.countDownShow) {
            this.setData({'timeData': ev.detail});
        }
    },

    // 倒计时结束
    onCountDownFinish: function () {
        let that = this;
        let sn = that.data.paySn;
        util.error('支付超时，订单已取消');
        clearInterval(that.data.timer);
        that.setData({
            'payDialogShow': false,
            'countDownShow': false
        });
        if (sn) {
            api.post('order', '/cancelBySn/' + sn).then(() => {
                that.setData({'orders': null});
                that.page();
            }).catch(err => console.error('取消订单失败', err));
        }
    },

    // 加载函数
    onLoad: function (options) {
        if (util.isLogin()) {
            this.page();
        }
    }
});
