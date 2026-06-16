import api from '../../../utils/api.js';
import util from '../../../utils/util.js';
import constant from "../../../utils/const.js";

Page({
    data: {
        MINIO_COURSE_SUMMARY: constant.MINIO_COURSE_SUMMARY, // 视频封面MINIO地址
        course: null, // 课程对象
        videoSrc: null, // 免费视频地址
        videoPoster: null, // 免费视频封面
        videoTitle: null, // 免费视频标题
        welcomeBarrage: [{text: '一大波弹幕即将来袭', color: '#ff0000', time: 1}], // 欢迎弹幕
        activeTab: '摘要', // 当前选中的tab的name值
        payStep: 'confirm', // 支付步骤：confirm-确认订单, payment-扫码支付
        confirmDialogShow: false, // 是否显示确认订单对话框
        time: 15 * 60 * 1000, // 倒计时起始时间
        timeData: {}, // 倒计时数据
        countDownShow: false, // 是否显示倒计时
        qrCodeImage: '', // 二维码图片路径
        sn: '', // 订单流水号
        timer: null // 支付倒计时定时器
    },

    // 查询视频详情
    getCourseInfo: function (courseId) {
        let that = this;
        let param = '/select/' + courseId;
        api.get('course', param).then(res => {
            res['created'] = util.dateFormat(res['created']);
            res['updated'] = util.dateFormat(res['updated']);
            if (res['seasons'].length > 0 && res['seasons'][0]['episodes'].length > 0) {
                let firstEpisode = res['seasons'][0]['episodes'][0];
                that.setData({
                    'videoSrc': constant.MINIO_EPISODE_VIDEO + firstEpisode['video'],
                    'videoPoster': constant.MINIO_EPISODE_VIDEO_COVER + firstEpisode['cover'],
                    'videoTitle': firstEpisode['title'],
                });
            }
            that.setData({'course': res});
            // 加载收藏状态
            that.loadFollowStatus();
        }).catch(err => console.error(err));
    },

    // 加载当前用户对课程中各集次的收藏状态
    loadFollowStatus: function () {
        let that = this;
        let course = that.data.course;
        if (!course || !course['seasons']) return;
        let userId = wx.getStorageSync('user').id;
        // 查询用户所有收藏
        api.get('follow', '/page', {'fkUserId': userId, pageNum: 1, pageSize: 999}).then(res => {
            let records = res['records'] || [];
            // 构建 episodeId -> followId 的映射
            let followMap = {};
            records.forEach(f => {
                followMap[f['fkEpisodeId']] = f['id'];
            });
            // 遍历 seasons > episodes，标记收藏状态
            course['seasons'].forEach(season => {
                season['episodes'].forEach(episode => {
                    if (followMap.hasOwnProperty(episode['id'])) {
                        episode['followed'] = true;
                        episode['followId'] = followMap[episode['id']];
                    } else {
                        episode['followed'] = false;
                        episode['followId'] = null;
                    }
                });
            });
            that.setData({'course': course});
        }).catch(err => console.error('加载收藏状态失败', err));
    },

    // 切换收藏状态
    toggleFollow: function (ev) {
        if (!util.isLogin()) return;
        let that = this;
        let dataset = ev.currentTarget.dataset;
        let episodeId = dataset['episodeId'];
        let followId = dataset['followId'];
        // dataset 中 boolean false 在微信小程序中会变成字符串 "false"
        let followed = dataset['followed'];
        if (followed === 'false' || followed === false) followed = false;
        if (followed === 'true' || followed === true) followed = true;
        let userId = wx.getStorageSync('user').id;

        console.log('[收藏] toggleFollow 触发', {episodeId, followId, followed, userId});

        if (followed) {
            // 已收藏，取消收藏
            if (!followId || followId === 'null' || followId === 'undefined') {
                util.error('收藏ID异常，请刷新重试');
                return;
            }
            console.log('[收藏] 取消收藏 followId=' + followId);
            api.del('follow', '/delete/' + followId).then(() => {
                util.success('已取消收藏');
                that.updateEpisodeFollowStatus(episodeId, false, null);
            }).catch(err => {
                util.error('取消收藏失败');
                console.error('[收藏] 取消收藏失败', err);
            });
        } else {
            // 未收藏，添加收藏
            let params = {'fkEpisodeId': episodeId, 'fkUserId': userId};
            console.log('[收藏] 添加收藏', params);
            api.post('follow', '/insert', params).then(() => {
                util.success('收藏成功');
                // 重新加载收藏状态以获取新的 followId
                that.loadFollowStatus();
            }).catch(err => {
                util.error('收藏失败');
                console.error('[收藏] 添加收藏失败', err);
            });
        }
    },

    // 更新单个集次的收藏状态
    updateEpisodeFollowStatus: function (episodeId, followed, followId) {
        let course = this.data.course;
        course['seasons'].forEach(season => {
            season['episodes'].forEach(episode => {
                if (episode['id'] === episodeId) {
                    episode['followed'] = followed;
                    episode['followId'] = followId;
                }
            });
        });
        this.setData({'course': course});
    },

    // 跳转到购物车页面
    toCart: function () {
        if (util.isLogin()) {
            util.tab('/pages/cart/cart');
        }
    },

    // 添加购物车
    addToCart: function () {
        if (util.isLogin()) {
            let that = this;
            let params = {
                'fkUserId': wx.getStorageSync('user').id,
                "fkCourseId": that.data.course['id'],
            };
            api.post('cart', '/insert', params).then(res => {
                util.error('加购成功');
                setTimeout(() => {
                    util.tab('/pages/cart/cart', true);
                }, 500);
            }).catch(err => console.error(err))
        }
    },

    // 客服
    chatMe: function () {
        util.page('/pages/course/detail/chat/chat')
    },

    // 立即购买 - 第一步：弹出订单确认框
    pay: function () {
        if (!util.isLogin()) {
            return;
        }
        // 先重置状态，确保对话框从确认步骤开始
        this.setData({
            payStep: 'confirm',
            confirmDialogShow: true,
            countDownShow: false,
            qrCodeImage: ''
        });
    },

    // 确认支付 - 第二步：生成订单并弹出支付二维码
    confirmPay: function () {
        let that = this;
        let course = that.data.course;

        // 切换到支付中状态
        that.setData({
            payStep: 'payment',
            countDownShow: true
        });

        // 发送预支付请求，生成订单
        let params = {
            'fkUserId': wx.getStorageSync('user').id,
            'courseIds': [course['id']],
            'totalAmount': course['price'],
            'payAmount': course['price'],
            'fkCouponsId': null,
        };
        api.post('order', '/prePay', params).then(res => {
            that.setData({'sn': res});
            // 获取支付二维码
            wx.request({
                url: constant.GATEWAY_HOST + '/order-server/api/v1/order/getQrCode',
                method: 'POST',
                data: {
                    'sn': that.data.sn,
                    'payAmount': course['price']
                },
                header: {'token': wx.getStorageSync('token')},
                responseType: 'arraybuffer',
                success(res) {
                    if (res.statusCode !== 200) {
                        util.tip(that.parsePaymentError(res.data));
                        that.setData({
                            payStep: 'confirm',
                            countDownShow: false
                        });
                        return;
                    }
                    // 显示二维码
                    that.setData({
                        'qrCodeImage': 'data:image/png;base64,' + wx.arrayBufferToBase64(res.data)
                    });
                    // 每隔2秒钟轮询查询订单状态
                    that.data.timer = setInterval(function () {
                        api.get('order', '/checkStatusBySn/' + that.data.sn).then(res => {
                            if (res === true) {
                                util.success('支付成功');
                                clearInterval(that.data.timer);
                                that.setData({
                                    confirmDialogShow: false,
                                    countDownShow: false
                                });
                                // 跳转到订单页面
                                util.page('/pages/user/order/order');
                            }
                        }).catch(err => util.error('查询订单状态失败', err));
                    }, 2000);
                },
                fail(err) {
                    util.error('获取二维码失败');
                    that.setData({
                        payStep: 'confirm',
                        countDownShow: false
                    });
                }
            });
        }).catch(err => {
            console.log(err);
            util.error('订单生成失败');
            that.setData({
                payStep: 'confirm',
                countDownShow: false
            });
        });
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

    // 关闭对话框
    cancelPay: function () {
        if (this.data.payStep === 'payment') {
            // 已生成订单，取消支付跳转到订单页
            util.success('取消支付成功');
            clearInterval(this.data.timer);
            util.page('/pages/user/order/order');
        }
        this.setData({
            confirmDialogShow: false,
            countDownShow: false,
            payStep: 'confirm'
        });
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
            confirmDialogShow: false,
            countDownShow: false,
            payStep: 'confirm'
        });
    },

    // 加载函数
    onLoad: function (ev) {
        // 查询视频详情：获取路径传递过来的值
        this.getCourseInfo(ev['courseId']);
    }
});
