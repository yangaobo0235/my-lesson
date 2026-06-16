const ai = require('../../../utils/ai.js');

Page({
    data: {plans: [], loading: false},
    onShow() { this.load(); },
    async load() {
        this.setData({loading: true});
        try {
            this.setData({plans: await ai.plans()});
        } catch (error) {
            wx.showToast({title: error.message || '加载失败', icon: 'none'});
        } finally {
            this.setData({loading: false});
        }
    }
});
