import api from "../../../utils/api.js";
import util from "../../../utils/util.js";

Page({
    data: {
        currentGenderIdx: null,
        gender: '',
        genderArrays: ['我是女孩', '我是男孩', '我不告诉你'],
    },

    // 点击改变时触发
    onChange(event) {
        this.setData({'gender': event.detail.value});
    },

    // 点击取消时触发
    onCancel: function () {
        util.tab('/pages/user/user');
    },

    // 修改性别
    updateGender: function () {
        // 检验性别
        if (this.data.currentGenderIdx == null) {
            util.tip('请选择性别！');
            return;
        }
        // 发送请求修改性别
        let loginUser = wx.getStorageSync('user');
        loginUser['gender'] = this.data.genderArrays.indexOf(this.data.gender);
        api.put('user', '/update', loginUser).then(res => {
            util.success('修改成功');
            // 刷新本地存储的用户信息
            wx.setStorageSync('user', loginUser);
            // 0.5秒后切换到 "我的" 选项卡
            setTimeout(() => util.tab('/pages/user/user'), 500);
        }).catch(err => console.log(err));
    },

    // 加载函数
    onLoad: function (options) {
        this.setData({'currentGenderIdx': options['gender']});
    }
});
