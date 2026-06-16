const ai = require('../../../utils/ai.js');

Page({
    data: {conversations: [], loading: false},
    onShow() { this.load(); },
    async load() {
        this.setData({loading: true});
        try {
            this.setData({conversations: await ai.conversations()});
        } catch (error) {
            wx.showToast({title: error.message || '加载失败', icon: 'none'});
        } finally {
            this.setData({loading: false});
        }
    },
    open(event) {
        const id = event.currentTarget.dataset.id;
        wx.navigateTo({url: `/pages/ai/chat/chat?conversationId=${id}`});
    },
    create() {
        wx.navigateTo({url: '/pages/ai/chat/chat'});
    },
    remove(event) {
        const id = event.currentTarget.dataset.id;
        wx.showModal({
            title: '删除会话',
            content: '确认删除这个会话吗？',
            success: async result => {
                if (!result.confirm) return;
                await ai.deleteConversation(id);
                this.load();
            }
        });
    }
});
