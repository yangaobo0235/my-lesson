import api from "../../../utils/api.js";
import util from "../../../utils/util.js";
import constant from "../../../utils/const.js";

Page({
    data: {
        province: '',
        mainActiveIndex: 0,
        activeId: null,
        provinceOptions: constant.PROVINCE_OPTIONS
    },

    // 点击左侧导航栏时触发
    onClickNav({detail = {}}) {
        this.setData({mainActiveIndex: detail.index || 0});
    },

    // 点击右侧选项时触发
    onClickItem({detail = {}}) {
        const activeId = this.data.activeId === detail.id ? null : detail.id;
        const activeText = this.data.province === detail.text ? null : detail.text;
        this.setData({activeId, province: activeText});
    },

    // 修改省份
    updateProvince: function () {
        let province = this.data.province;

        // 发送请求修改省份
        let loginUser = wx.getStorageSync('user');
        loginUser['province'] = province;
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
        let province = options['province'];
        this.data.provinceOptions.forEach(parent => {
            parent.children.forEach(child => {
                if (child.id === province) {
                    this.setData({'mainActiveIndex': parent.id});
                }
            });
        });
        this.setData({
            'province': options['province'],
            'activeId': options['province']
        });
    }
});
