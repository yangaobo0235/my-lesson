const ai = require('../../../utils/ai.js');

Page({
    data: {approvals: [], loading: false},
    onShow() { this.load(); },
    async load() {
        this.setData({loading: true});
        try {
            this.setData({approvals: await ai.approvals()});
        } catch (error) {
            wx.showToast({title: error.message || '加载失败', icon: 'none'});
        } finally {
            this.setData({loading: false});
        }
    },
    async decide(event) {
        const {id, action} = event.currentTarget.dataset;
        await (action === 'approve' ? ai.approve(id) : ai.reject(id));
        wx.showToast({title: action === 'approve' ? '已批准' : '已拒绝'});
        this.load();
    }
});
