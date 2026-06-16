import api from "../../../utils/api.js";
import util from "../../../utils/util.js";

Page({
    data: {
        age: null
    },

    // 修改年龄
    updateAge: function () {
        let age = this.data.age;

        // 检验年龄
        if (age < 18 || age > 80) {
            util.tip('年龄必须在18~80之间');
            return;
        }

        // 发送请求修改年龄
        let loginUser = wx.getStorageSync('user');
        loginUser['age'] = age;
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
        this.setData({'age': options['age']});
    }
});
