const ai = require('../../../utils/ai.js');

Page({
    data: {
        conversationId: '',
        title: '新对话',
        messages: [],
        input: '',
        sending: false,
        streamState: '未连接',
        liveAnswer: '',
        citations: [],
        timeline: [],
        approval: null,
        traceId: '',
        lastMessage: '',
        scrollIntoView: ''
    },
    streamTask: null,

    async onLoad(options) {
        try {
            let conversationId = options.conversationId;
            if (!conversationId) {
                const conversation = await ai.createConversation();
                conversationId = conversation.id;
                this.setData({title: conversation.title || '新对话'});
            }
            this.setData({conversationId});
            await this.loadMessages();
            this.connectStream();
        } catch (error) {
            wx.showToast({title: error.message || '会话加载失败', icon: 'none'});
        }
    },

    onUnload() {
        if (this.streamTask && this.streamTask.abort) this.streamTask.abort();
    },

    async loadMessages() {
        const messages = await ai.messages(this.data.conversationId);
        this.setData({messages});
        this.scrollBottom();
    },

    connectStream() {
        if (this.streamTask && this.streamTask.abort) this.streamTask.abort();
        this.setData({streamState: '连接中'});
        this.streamTask = ai.openStream(
            this.data.conversationId,
            (type, event) => this.handleEvent(type, event),
            () => this.setData({streamState: '连接异常'})
        );
        this.setData({streamState: '已连接'});
    },

    handleEvent(type, event) {
        const data = event.data || {};
        const updates = {traceId: event.traceId || this.data.traceId};
        if (type === 'answer_delta') {
            updates.liveAnswer = this.data.liveAnswer + (data.delta || '');
        } else if (type === 'citation') {
            updates.citations = this.data.citations.concat([data.citation]);
        } else if (type === 'approval_required') {
            updates.approval = data;
            updates.timeline = this.addTimeline(type, data);
        } else {
            updates.timeline = this.addTimeline(type, data);
        }
        if (type === 'run_completed') {
            updates.sending = false;
            this.loadMessages();
        }
        if (type === 'run_failed') updates.sending = false;
        this.setData(updates);
        this.scrollBottom();
    },

    addTimeline(type, data) {
        const labels = {
            run_started: '开始处理',
            intent_detected: `识别意图：${data.intent || '-'}`,
            retrieval_started: '开始检索',
            retrieval_completed: '检索完成',
            tool_started: `调用工具：${data.toolName || '-'}`,
            tool_completed: `工具完成：${data.toolName || '-'}`,
            approval_required: `等待确认：${data.actionType || '-'}`,
            run_completed: '回答完成',
            run_failed: data.message || '处理失败'
        };
        return this.data.timeline.concat([{type, label: labels[type] || type}]);
    },

    changeInput(event) {
        this.setData({input: event.detail});
    },

    async send(event) {
        const retry = event && event.currentTarget.dataset.retry;
        const content = (retry ? this.data.lastMessage : this.data.input).trim();
        if (!content || this.data.sending) return;
        const optimistic = this.data.messages.concat([{
            id: ai.uuid(),
            role: 'USER',
            content
        }]);
        this.setData({
            messages: optimistic,
            input: '',
            sending: true,
            liveAnswer: '',
            citations: [],
            timeline: [],
            approval: null,
            lastMessage: content
        });
        try {
            const result = await ai.sendMessage(
                this.data.conversationId,
                content,
                ai.uuid()
            );
            this.setData({traceId: result.traceId || ''});
        } catch (error) {
            this.setData({sending: false});
            wx.showToast({title: error.message || '发送失败', icon: 'none'});
        }
        this.scrollBottom();
    },

    async decide(event) {
        const action = event.currentTarget.dataset.action;
        const id = this.data.approval.approvalId;
        await (action === 'approve' ? ai.approve(id) : ai.reject(id));
        this.setData({approval: null});
        wx.showToast({title: action === 'approve' ? '已批准' : '已拒绝'});
    },

    openCitation(event) {
        const url = event.currentTarget.dataset.url;
        if (!url) return;
        wx.setClipboardData({
            data: url,
            success: () => wx.showToast({title: '引用链接已复制'})
        });
    },

    scrollBottom() {
        this.setData({scrollIntoView: `bottom-${Date.now()}`});
    }
});
