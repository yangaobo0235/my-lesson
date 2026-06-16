import util, {isNull} from '../../utils/util.js';
import api from "../../utils/api.js";
import constant from "../../utils/const.js";

Page({
    data: {
        MINIO_COURSE_COVER: constant.MINIO_COURSE_COVER, // 课程封面路径
        pageInfo: {pageNum: 1, pageSize: 12, totalPage: 0, totalRow: 0}, // 分页信息
        courses: null, // 课程列表对象
        keyword: '', // 课程标题
    },

    // 分页搜索课程记录
    page: function () {
        let that = this;
        let keyword = this.data.keyword;
        let pageNum = isNull(this.data.pageInfo['pageNum']) ? 1 : this.data.pageInfo['pageNum'];
        let pageSize = isNull(this.data.pageInfo['pageSize']) ? 5 : this.data.pageInfo['pageSize'];

        // 搜索关键字不能超过42个字
        if (keyword.length > 42){
            util.error('搜索关键字过长');
            return;
        }

        // 请求数据
        let params = {pageNum, pageSize, keyword: keyword.trim()};
        api.get('course', '/search', params).then(res => {
            that.setData({
                'courses': pageNum === 1 ? res['records'] : that.data.courses.concat(res['records']),
                'pageInfo.pageNum': res['pageNum'],
                'pageInfo.pageSize': res['pageSize'],
                'pageInfo.totalPage': res['totalPage'],
                'pageInfo.totalRow': res['totalRow'],
            });
        }).catch(err => console.error(err));
    },

    // 列表触底时
    onListEnd: function () {
        let that = this;
        let pageNum = that.data.pageInfo['pageNum'];
        let totalPage = that.data.pageInfo['totalPage'];
        if (pageNum < totalPage) {
            this.setData({'pageInfo.pageNum': pageNum + 1});
            this.page();
        }
    },

    // 按课程标题搜索课程
    searchByKeyword: function (ev) {
        this.setData({
            'keyword': ev.detail,
            'pageInfo.pageNum': 1,
        });
        this.page();
    },

    // 取消搜索
    cancelSearch: function (ev) {
        this.setData({
            'keyword': '',
            'pageInfo.pageNum': 1,
        });
        this.page();
    },

    // 查看课程详情
    showDetail: function (ev) {
        let courseId = ev.currentTarget.dataset['courseId'];
        util.page('/pages/course/detail/detail?courseId=' + courseId, false);
    },

    // 加载函数
    onLoad: function (options) {
        this.page();
        this.getTabBar().setData({"activeTab": 1});
    }
});