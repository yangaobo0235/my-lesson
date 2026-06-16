import api from "../../../utils/api.js";
import util from "../../../utils/util.js";
import constant from "../../../utils/const.js";

Page({
    data: {
        email: ''
    },

    // 修改电子邮箱
    updateEmail: function () {
        let email = this.data.email;

        // 检验电子邮箱
        if (!constant.RULE.EMAIL[0]['pattern'].test(email)) {
            util.tip(constant.RULE.EMAIL[0]['message']);
            return;
        }

        // 发送请求修改电子邮箱
        let loginUser = wx.getStorageSync('user');
        loginUser['email'] = email;
        api.put('user', '/update', loginUser).then(res => {
            util.success('修改成功');
            // 刷新本地存储的用户信息
            wx.setStorageSync('user', loginUser);
            // 0.5秒后切换到 "我的" 选项卡
            setTimeout(() => util.tab('/pages/user/user'), 500);
        }).catch(err => console.error(err));
    },

    // 加载函数
    onLoad: function (options) {
        this.setData({'email': options['email']});
    }
});
