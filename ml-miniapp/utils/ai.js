const {GATEWAY_HOST} = require('./const.js');

const AI_BASE = '/api/v1/ai';

function request(path, method = 'GET', data = null) {
    return new Promise((resolve, reject) => {
        wx.request({
            url: GATEWAY_HOST + AI_BASE + path,
            method,
            data,
            header: {
                'Content-Type': 'application/json; charset=utf-8',
                token: wx.getStorageSync('token')
            },
            success(res) {
                if (res.statusCode >= 200 && res.statusCode < 300) {
                    resolve(res.data);
                    return;
                }
                reject({
                    statusCode: res.statusCode,
                    message: res.data && res.data.message
                        ? res.data.message
                        : '请求失败'
                });
            },
            fail: reject
        });
    });
}

function uuid() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(
        /[xy]/g,
        char => {
            const random = Math.random() * 16 | 0;
            const value = char === 'x' ? random : (random & 0x3 | 0x8);
            return value.toString(16);
        }
    );
}

function decodeChunk(buffer, decoder) {
    if (decoder) {
        return decoder.decode(buffer, {stream: true});
    }
    const bytes = new Uint8Array(buffer);
    let encoded = '';
    bytes.forEach(byte => encoded += '%' + byte.toString(16).padStart(2, '0'));
    try {
        return decodeURIComponent(encoded);
    } catch (error) {
        return '';
    }
}

function parseSseBlock(block, onEvent) {
    let eventName = 'message';
    const dataLines = [];
    block.split(/\r?\n/).forEach(line => {
        if (line.startsWith('event:')) eventName = line.slice(6).trim();
        if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
    });
    if (!dataLines.length) return;
    try {
        onEvent(eventName, JSON.parse(dataLines.join('\n')));
    } catch (error) {
        console.error('SSE event parse failed', error);
    }
}

function openStream(conversationId, onEvent, onError) {
    let pending = '';
    const decoder = typeof TextDecoder !== 'undefined'
        ? new TextDecoder('utf-8')
        : null;
    const task = wx.request({
        url: `${GATEWAY_HOST}${AI_BASE}/conversations/${conversationId}/stream`,
        method: 'GET',
        enableChunked: true,
        header: {
            'Accept': 'text/event-stream',
            token: wx.getStorageSync('token')
        },
        fail: onError
    });
    task.onChunkReceived(res => {
        pending += decodeChunk(res.data, decoder);
        const blocks = pending.split(/\r?\n\r?\n/);
        pending = blocks.pop() || '';
        blocks.forEach(block => parseSseBlock(block, onEvent));
    });
    return task;
}

module.exports = {
    uuid,
    createConversation: title => request('/conversations', 'POST', title ? {title} : {}),
    conversations: () => request('/conversations'),
    messages: id => request(`/conversations/${id}/messages?limit=100`),
    sendMessage: (id, message, requestId) => request(`/conversations/${id}/messages`, 'POST', {message, requestId}),
    deleteConversation: id => request(`/conversations/${id}`, 'DELETE'),
    plans: () => request('/plans'),
    approvals: () => request('/approvals'),
    approve: id => request(`/approvals/${id}/approve`, 'POST'),
    reject: id => request(`/approvals/${id}/reject`, 'POST'),
    openStream
};
