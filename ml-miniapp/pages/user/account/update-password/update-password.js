import api from "../../../../utils/api.js";
import util from "../../../../utils/util.js";
import constant from "../../../../utils/const.js";

Page({
    data: {
        oldPassword: '',
        newPassword: '',
        rePassword: ''
    },

    // 修改密码
    updatePassword: function () {
        let oldPassword = this.data.oldPassword;
        let newPassword = this.data.newPassword;
        let rePassword = this.data.rePassword;

        // 新密码和原密码不能一致
        if (newPassword === oldPassword) {
            util.tip('新密码和原密码不能一致');
            return;
        }

        // 检验两次密码是否一致
        if (newPassword !== rePassword) {
            util.tip('两次密码不一致');
            return;
        }

        // 检验旧密码
        if (!constant.RULE.PASSWORD[0]['pattern'].test(oldPassword)) {
            util.tip(constant.RULE.PASSWORD[0]['message']);
            return;
        }

        // 检验新密码
        if (!constant.RULE.PASSWORD[0]['pattern'].test(newPassword)) {
            util.tip(constant.RULE.PASSWORD[0]['message']);
            return;
        }

        // 发送请求修改密码
        let param = {oldPassword, newPassword, id: wx.getStorageSync("user").id};
        api.put('user', '/updatePassword', param).then(res => {
            util.success('修改成功');
            // 0.5秒后切换到登录页面
            setTimeout(() => util.page('/pages/login/login-by-account/login-by-account', false), 500);
        }).catch(err => util.tip(err));
    },

    // 加载函数
    onLoad: function (options) {
    }
});
