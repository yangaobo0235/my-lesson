import util from "../../../utils/util.js";
import api from "../../../utils/api.js";
import constant from "../../../utils/const.js";

Page({
    data: {
        username: '',
        password: '',
    },

    // 登录方法
    loginByAccount: function () {
        let username = this.data.username;
        let password = this.data.password;

        // 空值校验
        if (util.hasEmpty(username, password)) {
            util.tip('账号或密码不能为空');
            return;
        }

        // 检验登录账号
        if (!constant.RULE.USERNAME[0]['pattern'].test(username)) {
            util.tip(constant.RULE.USERNAME[0]['message']);
            return;
        }

        // 校验登录密码
        if (!constant.RULE.PASSWORD[0]['pattern'].test(password)) {
            util.tip(constant.RULE.PASSWORD[0]['message']);
            return;
        }

        // 发送登录请求
        api.post('user', '/loginByAccount', {username, password}).then(res => {
            // 将用户信息以及对应的Token令牌存储起来
            wx.setStorageSync('token', res['token']);
            wx.setStorageSync('user', res['user']);
            util.success('登录成功');
            // 0.5秒后切换到 "我的" 选项卡
            setTimeout(() => util.tab('/pages/user/user'), 500);
        }).catch(err => console.error(err));
    },

    // 跳转到注册页面
    toRegister: function () {
        util.page('/pages/index/register/register', false);
    },

    // 跳转到手机登录页面
    toLoginByPhone: function () {
        util.page('/pages/index/login-by-phone/login-by-phone', false);
    },

    // 返回首页
    toIndex: function () {
        util.tab('/pages/index/index');
    },

    // 加载函数
    onLoad: function (options) {
        wx.removeStorageSync('token');
        wx.removeStorageSync('user');
    }
});
