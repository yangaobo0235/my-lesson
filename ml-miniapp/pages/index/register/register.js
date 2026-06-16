import util from "../../../utils/util.js";
import api from "../../../utils/api.js";
import constant from "../../../utils/const.js";

Page({
    data: {
        username: '',
        password: '',
        rePassword: '',
        realname: '',
        phone: '',
        idcard: '',
        email: '',
    },

    // 注册账号
    register: function () {
        let username = this.data.username;
        let password = this.data.password;
        let rePassword = this.data.rePassword;
        let realname = this.data.realname;
        let phone = this.data.phone;
        let idcard = this.data.idcard;
        let email = this.data.email;

        // 检验两次密码是否一致
        if (password !== rePassword) {
            util.tip('两次密码不一致');
            return;
        }

        // 校验登录账号规则
        if (!constant.RULE.USERNAME[0]['pattern'].test(username)) {
            util.tip(constant.RULE.USERNAME[0]['message']);
            return;
        }

        // 校验登录密码规则
        if (!constant.RULE.PASSWORD[0]['pattern'].test(password)) {
            util.tip(constant.RULE.PASSWORD[0]['message']);
            return;
        }

        // 校验真实姓名规则
        if (!constant.RULE.REALNAME[0]['pattern'].test(realname)) {
            util.tip(constant.RULE.REALNAME[0]['message']);
            return;
        }

        // 校验电话号码规则
        if (!constant.RULE.PHONE[0]['pattern'].test(phone)) {
            util.tip(constant.RULE.PHONE[0]['message']);
            return;
        }

        // 校验身份证号规则
        if (!constant.RULE.IDCARD[0]['pattern'].test(idcard)) {
            util.tip(constant.RULE.IDCARD[0]['message']);
            return;
        }

        // 校验电子邮箱规则
        if (!constant.RULE.EMAIL[0]['pattern'].test(email)) {
            util.tip(constant.RULE.EMAIL[0]['message']);
            return;
        }

        // 发送登录请求
        let param = {username, password, realname, phone, idcard, email};
        api.post('user', '/insert', param).then(res => {
            util.success('注册成功');
            // 0.5秒后切换到登录页面
            setTimeout(() => {
                util.page('/pages/index/login-by-account/login-by-account', false);
            }, 500);
        }).catch(err => console.error(err));
    },

    // 跳转到账号登录页面
    toLoginByAccount: function () {
        util.page('/pages/index/login-by-account/login-by-account', false);
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
    onLoad: function (options) {}
});