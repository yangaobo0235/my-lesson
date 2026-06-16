import api from '../../utils/api.js';
import constant from '../../utils/const.js';
import util from "../../utils/util.js";

Page({
  data: {
    isLogin: false, // 是否已登录
    currentNotice: null, // 当前通知对象
    PROJECT_TITLE: constant.PROJECT_TITLE, // 项目主标题
    PROJECT_SUB_TITLE: constant.PROJECT_SUB_TITLE, // 项目副标题
    MINIO_BANNER: constant.MINIO_BANNER, // 横幅轮播图片MINIO地址
    banners: null, // 横幅列表对象
    currentArticleIdx: 1, // 当前公告的value值，用于切换公告
    articles: null, // 公告列表对象
    seckills: null, // 秒杀活动列表对象
    activeSeckillIdx: 0, // 当前秒杀活动的value值，用于切换秒杀活动
    MINIO: constant.MINIO_COURSE_COVER // 课程封面图片MINIO地址
  },

  // 跳入登录页面
  toLogin: function () {
    util.page('/pages/index/login-by-account/login-by-account', true);
  },

  // 查询1条通知记录
  topNotice1: function () {
    let that = this;
    api.get('notice', '/top/1')
        .then(res => that.setData({currentNotice: res[0]['content']}))
        .catch(err => console.error(err));
  },

  // 查询5条轮播图记录
  topBanner5: function () {
    let that = this;
    api.get('banner', '/top/5')
        .then(res => that.setData({banners: res}))
        .catch(err => console.error(err));
  },

  // 查询5条通知记录
  topArticle5: function () {
    let that = this;
    api.get('article', '/top/5')
        .then(res => that.setData({articles: res}))
        .catch(err => console.error(err));
  },

  // 查询10条未开始的营销活动
  todaySeckill: function () {
    let that = this;
    api.get('seckill', '/today')
        .then(res => that.setData({seckills: res}))
        .catch(err => console.error(err));
  },

  // 切换公告
  changeArticle: function (ev) {
    this.setData({'currentArticleIdx': ev.detail});
  },

  // 切换秒杀活动
  changeSeckill: function (ev) {
    this.setData({'activeSeckillIdx': ev.detail});
  },

  // 加载函数
  onLoad: function () {
    this.setData({isLogin: !wx.getStorageSync('token')});
    this.topNotice1();
    this.topBanner5();
    this.topArticle5();
    this.todaySeckill();
    this.getTabBar().setData({"activeTab": 0});
  },
});