import api from "../../../utils/api.js";
import util from "../../../utils/util.js";
import constant from "../../../utils/const.js";

Page({
    data: {
        currentZodiacIdx: null,
        zodiac: '',
        zodiacOptions: constant.ZODIAC_OPTIONS
    },

    // 点击改变时触发
    onChange(event) {
        this.setData({'zodiac': event.detail.value});
    },

    // 点击取消时触发
    onCancel: function () {
        util.tab('/pages/user/user');
    },

    // 点击确认时触发
    updateZodiac: function () {
        let zodiac = this.data.zodiac;

        // 发送请求修改星座（星座只要前3个中文字）
        let loginUser = wx.getStorageSync('user');
        loginUser['zodiac'] = zodiac.substring(0, 3);
        api.put('user', '/update', loginUser).then(res => {
            util.success('修改成功');
            // 刷新本地存储的用户信息
            wx.setStorageSync('user', loginUser);
            // 0.5秒后切换到 "我的" 选项卡
            setTimeout(() => util.tab('/pages/user/user'), 500);
        }).catch(err => util.error('修改失败', err));
    },

    // 加载函数
    onLoad: function (options) {
        this.setData({'zodiac': options['zodiac']});
        this.setData({'currentZodiacIdx': this.data.zodiacOptions.indexOf(options['zodiac'])});
    }
});
