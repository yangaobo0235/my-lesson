import util from "../../../utils/util.js";
import api from "../../../utils/api.js";
import constant from "../../../utils/const.js";

Page({
    data: {
        MINIO_COURSE_COVER: constant.MINIO_COURSE_COVER,
        pageInfo: {pageNum: 1, pageSize: 10, totalPage: 0, totalRow: 0},
        // 已购课程的展平列表：从订单明细中抽取
        courses: null,
    },

    // 分页查询已支付订单，从中提取课程明细
    page: function () {
        let that = this;
        let pageNum = that.data.pageInfo['pageNum'];
        let pageSize = that.data.pageInfo['pageSize'];
        if (util.isNull(pageNum)) pageNum = 1;
        if (util.isNull(pageSize)) pageSize = 10;
        let params = {
            pageNum, pageSize,
            'username': wx.getStorageSync('user').username,
            'status': 1  // 只查已支付订单
        };
        api.get('order', '/page', params).then(res => {
            // 从订单记录中展平所有订单明细，得到课程列表
            let courses = [];
            let records = res['records'];
            if (records) {
                for (let i in records) {
                    let order = records[i];
                    let orderDetails = order['orderDetails'];
                    if (orderDetails) {
                        for (let j in orderDetails) {
                            let detail = orderDetails[j];
                            courses.push({
                                orderId: order['id'],
                                orderSn: order['sn'],
                                courseId: detail['fkCourseId'],
                                title: detail['courseTitle'],
                                cover: detail['courseCover'],
                                price: detail['coursePrice'],
                                created: order['created']
                            });
                        }
                    }
                }
            }
            that.setData({
                'courses': pageNum === 1 ? courses : that.data.courses.concat(courses),
                'pageInfo.pageNum': res['pageNumber'],
                'pageInfo.pageSize': res['pageSize'],
                'pageInfo.totalPage': res['totalPage'],
                'pageInfo.totalRow': res['totalRow'],
            });
        }).catch(err => console.log(err));
    },

    // 列表触底加载更多
    pageMore: function () {
        let pageNum = this.data.pageInfo['pageNum'];
        let totalPage = this.data.pageInfo['totalPage'];
        if (pageNum < totalPage) {
            this.setData({'pageInfo.pageNum': pageNum + 1});
            this.page();
        }
    },

    // 播放视频
    playVideo: function (ev) {
        let courseId = ev.currentTarget.dataset['courseId'];
        util.page('/pages/user/order/player/player?courseId=' + courseId, false);
    },

    onLoad: function () {
        if (util.isLogin()) {
            this.page();
        }
    }
});
