import api from "../../../utils/api.js";
import util from "../../../utils/util.js";
import constant from "../../../utils/const.js";

Page({
    data: {
        info: ''
    },

    // 当信息内容改变时触发
    changeInfo: function (ev) {
        this.setData({'info': ev.detail});
    },

    // 修改描述
    updateInfo: function () {
        let info = this.data.info;

        // 检验描述
        if (!constant.RULE.INFO[0]['pattern'].test(info)) {
            util.tip(constant.RULE.INFO[0]['message']);
            return;
        }

        // 发送请求修改描述
        let loginUser = wx.getStorageSync('user');
        loginUser['info'] = info;
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
        this.setData({'info': options['info']});
    }
});
