import api from "../../../utils/api.js";
import util from "../../../utils/util.js";
import constant from "../../../utils/const.js";

Page({
    data: {
        phone: '',
        vcode: '', // 短信验证码
    },

    // 获取短信验证码
    getVcode: function () {
        let phone = this.data.phone;

        // 空值校验
        if (util.isEmpty(phone)) {
            util.tip('手机号码不能为空');
            return;
        }

        // 校验手机号码
        if (!constant.RULE.PHONE[0]['pattern'].test(phone)) {
            util.tip(constant.RULE.PHONE[0]['message']);
            return;
        }

        // 发送请求：根据手机号码获取短信验证码
        api.get('user', '/getVcode/' + phone).then(res => {
                util.success('验证码已发送');
            }
        ).catch(err => console.error(err));
    },

    // 根据手机号码和验证码登录系统
    loginByPhone: function () {
        let phone = this.data.phone;
        let vcode = this.data.vcode;

        // 空值校验
        if (util.hasEmpty(phone, vcode)) {
            util.tip('手机号码或验证码不能为空');
            return;
        }

        // 检验手机号码
        if (!constant.RULE.PHONE[0]['pattern'].test(phone)) {
            util.tip(constant.RULE.PHONE[0]['message']);
            return;
        }

        // 发送登录请求
        api.post('user', '/loginByPhone', {phone, vcode}).then(res => {
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

    // 跳转到账号登录页面
    toLoginByAccount: function () {
        util.page('/pages/index/login-by-account/login-by-account', false);
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
