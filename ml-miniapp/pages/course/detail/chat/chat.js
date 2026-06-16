import util from "../../../../utils/util.js";

Page({
    data: {
        message: '一首李白的诗', // 用户输入的问题
        active: 0, // 当前激活的步骤
        steps: [{
            text: `${util.dateFormat(new Date())}`,
            desc: '你好，有什么可以帮助您？'
        }],
        sseServerUrl: 'http://localhost:5900/api/v1/base/chat', // SSE服务端地址
        isReplying: false, // AI是否正在回复中
        scrollTop: 0 // 滚动条位置
    },

    // 当内容改变时触发
    changeMessage: function (ev) {
        this.setData({'message': ev.detail});
    },

    // 发送消息
    sendMessage: function () {
        let that = this;
        // 如果AI正在回复中或用户输入的内容为空，均不处理
        if (this.data.isReplying || util.isEmpty(this.data.message)) return;
        // 加入用户输入的信息
        this.data.steps.push({
            text: `${util.dateFormat(new Date())}`,
            desc: this.data.message
        });
        // 加入AI回复的信息
        this.data.steps.push({
            text: `${util.dateFormat(new Date())}`,
            desc: 'waiting...'
        });
        // 更新聊天框
        this.setData({
            steps: this.data.steps,
            active: this.data.active + 2,
            isReplying: true
        });
        // 发送请求并接收分块响应的消息
        wx.request({
            url: this.data.sseServerUrl + '?msg=' + this.data.message,
            enableChunked: true // 启用分块传输
        }).onChunkReceived((res) => {
            // 获取当前AI回复的信息
            let desc = that.data.steps[that.data.active]['desc'];
            // 如果当前AI回复的信息是 "waiting..."，则移除它
            if (desc === 'waiting...') {
                that.data.steps[that.data.active]['desc'] = desc.replaceAll('waiting...', '');
            }
            // AI回复的信息类型固定为arrayBuffer，需要开发者自己进行转换为字符串
            let str = new TextDecoder('utf-8').decode(res.data, {stream: true});
            // 移除 "data:"
            str = str.replaceAll('data:', '').trim();
            // 如果不是 "[over]"，则拼接AI回复的信息
            if (!str.includes('[over]')) {
                that.data.steps[that.data.active]['desc'] += str;
                // 更新
                that.setData({steps: that.data.steps});
            }
            // 否则，AI回复完毕
            else {
                // 更新
                that.setData({isReplying: false});
            }
            // 滚动到底部
            const query = wx.createSelectorQuery().in(this);
            query.select('#chatDialog').boundingClientRect((rect) => {
                if (rect) this.setData({scrollTop: rect.height});
            }).exec();
        });
        // 清空用户输入的内容
        that.setData({'message': ''});
    },

    onLoad: function (options) {}
});
