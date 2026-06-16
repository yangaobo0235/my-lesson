import util from "../../utils/util.js";
import api from "../../utils/api.js";
import constant from "../../utils/const.js";

Page({
    data: {
        couponsCode: '', // 优惠卷兑换口令
        coupons: {}, // 优惠卷对象
        totalAmount: 0.0, // 总金额
        payAmount: 0.0, // 应付金额
        couponsShow: false, // 是否显示优惠卷面板
        carts: null, // 购物车列表
        courseIdAndPrice: {}, // 存储课程id和对应价格的对象
        courseIds: [], // 存储课程id的数组
        MINIO_COURSE_COVER: constant.MINIO_COURSE_COVER, // 课程封面MINIO地址
        pageInfo: {pageNum: 1, pageSize: 8, totalPage: 0, totalRow: 0}, // 分页信息
        payDialogShow: false, // 是否显示支付对话框
        time: 15 * 60 * 1000, // 倒计时起始时间
        timeData: {}, // 倒计时数据
        countDownShow: false, // 是否显示倒计时
        qrCodeImage: '', // 二维码图片路径
        sn: '', // 订单流水号
        timer: null // 支付倒计时定时器
    },

    // 分页查询购物车记录
    page: function () {
        let that = this;
        let pageNum = that.data.pageInfo['pageNum'];
        let pageSize = that.data.pageInfo['pageSize'];
        if (util.isNull(pageNum)) pageNum = 1;
        if (util.isNull(pageSize)) pageSize = 8;

        let userId = wx.getStorageSync('user').id;
        let params = {
            pageNum: pageNum,
            pageSize: pageSize,
            'fkUserId': userId
        };
        api.get('cart', '/page', params).then(res => {
            that.setData({
                'carts': pageNum === 1 ? res['records'] : that.data.carts.concat(res['records']),
                'pageInfo.pageNum': res['pageNumber'],
                'pageInfo.pageSize': res['pageSize'],
                'pageInfo.totalPage': res['totalPage'],
                'pageInfo.totalRow': res['totalRow'],
            });
            // 将课程id和对应价格存入 courseIdAndPrice 变量
            let courseIdAndPrice = {};
            for (let i in that.data.carts) {
                let cart = that.data.carts[i];
                courseIdAndPrice[cart['fkCourseId']] = cart['coursePrice'];
            }
            that.setData({'courseIdAndPrice': courseIdAndPrice});
        }).catch(err => console.error(err));
    },

    // 列表触底时追查下一页数据
    pageMore: function () {
        let that = this;
        let pageNum = that.data.pageInfo['pageNum'];
        let totalPage = that.data.pageInfo['totalPage'];
        if (pageNum < totalPage) {
            this.setData({'pageInfo.pageNum': this.data.pageInfo['pageNum'] + 1});
            this.page();
        }
    },

    // 兑换优惠卷
    getCoupons: function (ev) {
        let that = this;
        let code = ev.detail;
        if (code) {
            let url = '/selectByCode/' + code;
            api.get('coupons', url).then(res => {
                if (util.isNull(res)) {
                    util.error('优惠卷无效');
                    return;
                }
                res['endTime'] = util.dateFormat(res['endTime']);
                res['startTime'] = util.dateFormat(res['startTime']);
                res['created'] = util.dateFormat(res['created']);
                res['updated'] = util.dateFormat(res['updated']);

                // 优惠金额大于总金额，直接设置为 0，否则减去优惠金额
                if (res['cpPrice'] > this.data.totalAmount) {
                    that.setData({payAmount: 0});
                } else {
                    that.setData({payAmount: that.data.totalAmount - res['cpPrice']});
                }

                that.setData({
                    'couponsCode': '',
                    'coupons': res,
                    'couponsShow': true,
                });
                util.success('优惠卷已生效');
            }).catch(err => console.log(err));
        }
    },

    // 打开优惠卷面板
    openCouponsSheet: function () {
        this.setData({'couponsShow': true});
    },

    // 关闭优惠卷面板
    closeCouponsSheet() {
        this.setData({'couponsShow': false});
    },

    // 删除购物车记录
    removeCart: function (ev) {
        let id = ev.currentTarget.dataset['id'];
        util.confirm('课程将被移出购物车，确定吗？', () => {
            api.del('cart', `/delete/${id}`).then(res => {
                util.success('移出成功');
                this.setData({'carts': null});
                this.page();
            }).catch(err => console.log(err));
        });
    },

    // 清空购物车记录
    clearCart: function (ev) {
        util.confirm('清空购物车中的全部课程，确定吗？', () => {
            let url = '/clearByUserId/' + wx.getStorageSync('user').id;
            api.del('cart', url).then(res => {
                util.success('清空成功');
                this.setData({
                    'carts': null,
                    'totalAmount': 0.0,
                    'payAmount': 0.0,
                    'coupons': null,
                });
            }).catch(err => console.log(err));
        });
    },

    // 打开支付对话框
    openPayDialog: function () {
        let that = this;
        // 若选择内容为空，则不进行支付动作
        if (this.data.courseIds.length <= 0) {
            util.error('至少选择一项');
            return;
        }
        // 发送预支付请求
        let params = {
            'fkUserId': wx.getStorageSync('user').id,
            'courseIds': this.data.courseIds,
            'totalAmount': this.data.totalAmount,
            'payAmount': this.data.payAmount,
            'fkCouponsId': this.data.coupons['id'] ? this.data.coupons['id'] : null,
        };
        api.post('order', '/prePay', params).then(res => {
            this.setData({'sn': res})
            // 获取支付二维码
            wx.request({
                url: constant.GATEWAY_HOST + '/order-server/api/v1/order/getQrCode',
                method: 'POST',
                data: {
                    'sn': that.data.sn,
                    'payAmount': this.data.payAmount
                },
                header: {'token': wx.getStorageSync('token')},
                responseType: 'arraybuffer',
                success(res) {
                    if (res.statusCode !== 200) {
                        util.tip(that.parsePaymentError(res.data));
                        return;
                    }
                    // 显示二维码
                    that.setData({
                        'qrCodeImage': 'data:image/png;base64,' + wx.arrayBufferToBase64(res.data),
                        'payDialogShow': true,
                        'countDownShow': true
                    });
                    // 每隔2秒钟轮询查询订单状态
                    that.data.timer = setInterval(function () {
                        api.get('order', '/checkStatusBySn/' + that.data.sn).then(res => {
                            if (res === true) {
                                util.success('支付成功');
                                clearInterval(that.data.timer);
                                that.setData({
                                    'payDialogShow': false,
                                    'countDownShow': false
                                });
                                // 跳转到订单页面
                                util.page('/pages/user/order/order');
                            }
                        }).catch(err => util.error('查询订单状态失败', err));
                    }, 2000);
                },
            });
        }).catch(err => console.log(err));
    },

    parsePaymentError: function (data) {
        try {
            const text = new TextDecoder('utf-8').decode(new Uint8Array(data));
            const body = JSON.parse(text);
            return body.message || '获取二维码失败';
        } catch (err) {
            return '获取二维码失败';
        }
    },

    // 取消支付
    cancelPay: function () {
        util.success('取消支付成功');
        clearInterval(this.data.timer);
        this.setData({
            'payDialogShow': false,
            'countDownShow': false
        });
        // 跳转到订单页面
        util.page('/pages/user/order/order');
    },

    // 倒计时
    countDown: function (ev) {
        if (this.data.countDownShow) {
            this.setData({'timeData': ev.detail});
        }
    },

    // 当倒计时结束时触发
    onCountDownFinish: function (ev) {
        util.error('支付超时');
        this.setData({
            'payDialogShow': false,
            'countDownShow': false
        });
    },

    // 计算总金额（点击全选按钮时触发）
    getTotalAmount: function () {
        let param = '/totalAmountByUserId/' + wx.getStorageSync('user').id;
        api.get('cart', param).then(res => {
            this.setData({
                'totalAmount': res,
                'payAmount': res
            });
        }).catch(err => util.error('总金额查询失败', err));
    },

    // 查看课程详情
    showDetail: function (ev) {
        let courseId = ev.currentTarget.dataset['courseId'];
        util.page('/pages/course/detail/detail?courseId=' + courseId, false);
    },

    // 选择课程时触发
    changeCourseIds: function (ev) {
        let courseIds = ev.detail;
        this.setData({'courseIds': courseIds});
        // 从 courseIdAndPrice 中获取当前选中的全部课程的价格
        let courseIdAndPrice = this.data.courseIdAndPrice;
        let payAmount = 0;
        let totalAmount = 0;
        for (let i in courseIds) {
            let courseId = courseIds[i];
            payAmount += courseIdAndPrice[courseId];
            totalAmount += courseIdAndPrice[courseId];
        }
        this.setData({payAmount, totalAmount});
    },

    // 加载函数
    onLoad: function (options) {
        if (util.isLogin()) {
            this.page();
        }
        this.getTabBar().setData({"activeTab": 2});
    }
});
