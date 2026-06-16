import util from "../../../utils/util.js";
import api from "../../../utils/api.js";
import constant from "../../../utils/const.js";

Page({
    data: {
        MINIO_AVATAR: constant.MINIO_AVATAR,
        MINIO_COURSE_COVER: constant.MINIO_COURSE_COVER,
        pageInfo: {pageNum: 1, pageSize: 10, totalPage: 0, totalRow: 0},
        follows: null,
    },

    // 分页查询收藏记录
    page: function () {
        let that = this;
        let pageNum = that.data.pageInfo['pageNum'];
        let pageSize = that.data.pageInfo['pageSize'];
        if (util.isNull(pageNum)) pageNum = 1;
        if (util.isNull(pageSize)) pageSize = 10;
        let params = {
            pageNum, pageSize,
            'fkUserId': wx.getStorageSync('user').id
        };
        api.get('follow', '/page', params).then(res => {
            that.setData({
                'follows': pageNum === 1 ? res['records'] : that.data.follows.concat(res['records']),
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

    // 取消收藏
    removeFollow: function (ev) {
        let id = ev.currentTarget.dataset['id'];
        util.confirm('确定取消收藏吗？', () => {
            api.del('follow', `/delete/${id}`).then(() => {
                util.success('已取消收藏');
                this.setData({'follows': null});
                this.page();
            }).catch(err => console.log(err));
        });
    },

    onLoad: function () {
        if (util.isLogin()) {
            this.page();
        }
    }
});
