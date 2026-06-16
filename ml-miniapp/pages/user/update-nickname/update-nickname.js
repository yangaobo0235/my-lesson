import api from "../../../utils/api.js";
import util from "../../../utils/util.js";
import constant from "../../../utils/const.js";

Page({
    data: {
        nickname: ''
    },

    // 修改昵称
    updateNickname: function () {
        let nickname = this.data.nickname;

        // 检验昵称
        if (!constant.RULE.NICKNAME[0]['pattern'].test(nickname)) {
            util.tip(constant.RULE.NICKNAME[0]['message']);
            return;
        }

        // 发送请求修改昵称
        let loginUser = wx.getStorageSync('user');
        loginUser['nickname'] = nickname;
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
        this.setData({'nickname': options['nickname']});
    }
});
