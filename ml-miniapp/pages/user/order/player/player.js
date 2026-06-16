import util from "../../../../utils/util.js";
import api from "../../../../utils/api.js";
import constant from "../../../../utils/const.js";

Page({

    data: {
        myVideo: null, // video对象
        course: null, // 课程对象
        episode: {id: null, title: '', src: '', cover: ''}, // 当前播放的视频
        socket: null, // websocket对象
        socketSuccess: false, // websocket是否连接成功
        barrages: [{text: '一大波弹幕即将来袭', color: '#ff0000', time: 1}], // 弹幕列表
        videoPlay: false, // 视频是否处于播放状态
        like: false, // 是否点赞
        follow: false, // 是否收藏
        reportText: '', // 举报内容
        reportSheetShow: false, // 举报Sheet是否显示
        barrageText: '', // 弹幕内容
        barrageSheetShow: false, // 弹幕Sheet是否显示
        shareSheetShow: false, // 分享Sheet是否显示
        shareSheetOptions: [
            {name: '微信', icon: 'wechat'},
            {name: '微博', icon: 'weibo'},
            {name: '复制链接', icon: 'link'},
            {name: '分享海报', icon: 'poster'},
            {name: '二维码', icon: 'qrcode'},
        ], // 分享Sheet选项
        commentText: '', // 评论内容
        commentSheetShow: false, // 评论Sheet是否显示
        currentTab: 0, // 当前Tab
        currentTabIdx: 0, // 当前折叠
        MINIO_AVATAR: constant.MINIO_AVATAR, // 头像地址
        replyText: '', // 回复内容
        replyTargetId: null, // 回复目标id
        replySheetShow: false, // 回复Sheet是否显示
        comments: null, // 评论列表
    },

    // 查询课程详情
    getCourseInfo: function (courseId) {
        let that = this;
        api.get('course', '/select/' + courseId).then(res => {
            if (res['seasons'].length > 0 && res['seasons'][0]['episodes'].length > 0) {
                let firstEpisode = res['seasons'][0]['episodes'][0];
                that.setData({
                    'course': res,
                    'episode.src': constant.MINIO_EPISODE_VIDEO + firstEpisode['video'],
                    'episode.cover': constant.MINIO_EPISODE_VIDEO_COVER + firstEpisode['cover'],
                    'episode.title': firstEpisode['title'],
                    'episode.id': firstEpisode['id'],
                });
            }
        }).catch(err => console.log(err));
    },

    // 当视频播放时触发
    onVideoPlay: function () {
        this.setData({'videoPlay': true});
    },

    // 当视频暂停时触发
    onVideoPause: function () {
        this.setData({'videoPlay': false});
    },

    // 切换课程点赞
    toggleLike: function () {
        this.setData({'like': !this.data.like});
    },

    // 切换课程收藏
    toggleFollow: function () {
        this.setData({'follow': !this.data.follow});
    },

    // 打开举报Sheet
    openReportSheet: function () {
        this.setData({'reportSheetShow': true});
    },

    // 当举报内容改变时触发
    changeReportText: function (ev) {
        this.setData({'reportText': ev.detail});
    },

    // 举报课程
    sendReportText: function () {
        let that = this;
        // 举报文字
        let text = this.data.reportText.trim();
        // 空值保护
        if (util.isEmpty(text)) {
            util.error('举报内容为空');
            return;
        }
        // 发送举报
        api.post('report', '/insert', {
            'fkEpisodeId': this.data.episode['id'],
            'fkUserId': wx.getStorageSync('user').id,
            'content': text,
        }).then(res => {
            util.success('举报成功');
            that.setData({'commentText': ''});
        }).catch(err => console.log(err));
        this.closeReportSheet();
    },

    // 关闭举报Sheet
    closeReportSheet: function () {
        this.setData({
            'reportSheetShow': false,
            'reportText': ''
        });
    },

    // 打开分享Sheet
    openShareSheet: function () {
        this.setData({'shareSheetShow': true});
    },

    // 关闭分享Sheet
    closeShareSheet: function () {
        this.setData({'shareSheetShow': false});
    },

    // 打开评论Sheet
    openCommentSheet: function () {
        this.setData({'commentSheetShow': true});
    },

    // 当评论内容改变时触发
    changeCommentText: function (ev) {
        this.setData({'commentText': ev.detail});
    },

    // 评论课程
    sendCommentText: function (ev) {
        let that = this;
        // 举报文字
        let text = this.data.commentText.trim();
        // 空值保护
        if (util.isEmpty(text)) {
            util.error('评论内容为空');
            return;
        }
        api.post('comment', '/insert', {
            'pid': 0,
            'fkEpisodeId': this.data.episode['id'],
            'fkUserId': wx.getStorageSync('user').id,
            'content': text,
        }).then(res => {
            util.success('评论成功');
            that.setData({'commentText': ''});
        }).catch(err => console.log(err));
        this.closeCommentSheet();
    },

    // 关闭评论Sheet
    closeCommentSheet: function () {
        this.setData({'commentSheetShow': false});
    },

    // 打开弹幕Sheet
    openBarrageSheet: function () {
        // 当前视频未处于播放状态
        if (!this.data.videoPlay) {
            util.error('视频未播放');
            return;
        }
        // 暂停视频
        this.data.myVideo.pause();
        // 打开Sheet
        this.setData({'barrageSheetShow': true});
    },

    // 当弹幕内容改变时触发
    changeBarrageText: function (ev) {
        this.setData({'barrageText': ev.detail});
    },

    // 发送弹幕（弹幕颜色随机）
    sendBarrageText: function () {
        // 弹幕文字
        let text = this.data.barrageText.trim();
        // 空值保护
        if (util.isEmpty(text)) {
            util.error('弹幕内容为空');
            return;
        }
        // 发送弹幕文字到WebSocket服务端
        if (this.data.socketSuccess) {
            this.data.socket.send({data: text});
        }
        // 关闭弹幕Sheet，重置弹幕文字
        this.setData({
            'barrageSheetShow': false,
            'barrageText': ''
        });
        this.data.myVideo.play();
    },

    // 关闭弹幕Sheet
    closeBarrageSheet: function () {
        this.setData({
            'barrageSheetShow': false,
            'barrageText': '',
            'videoPlay': true
        });
        this.data.myVideo.play();
    },

    // 切换Tab
    changeTab: function (ev) {
        let currentTab = ev.detail.name;
        // 评论列表
        if (currentTab === 1) {
            this.pageCommentsByEpisodeId(this.data.episode['id']);
        }
        this.setData({'currentTab': ev.detail.name});
    },


    // 切换Collapse
    changeCollapse: function (ev) {
        this.setData({'currentTabIdx': ev.detail});
    },

    // 切换视频
    toggleEpisode: function (ev) {
        // 暂停视频
        this.data.myVideo.pause();
        this.setData({
            'episode.src': constant.MINIO_EPISODE_VIDEO + ev.currentTarget.dataset['src'],
            'episode.cover': constant.MINIO_EPISODE_VIDEO_COVER + ev.currentTarget.dataset['cover'],
            'episode.title': ev.currentTarget.dataset['title'],
            'episode.id': ev.currentTarget.dataset['episodeId']
        });
    },

    /* ==================== 评论列表 ==================== */

    // 获取该视频全部评论
    pageCommentsByEpisodeId: function (episodeId) {
        let that = this;
        let params = {
            'pageNum': 1,
            'pageSize': 10,
            'fkEpisodeId': episodeId,
        };
        api.get('comment', '/page', params).then(res => {
            res['records'].forEach(comment => {
                comment['updated'] = util.dateFormat(comment['updated']);
            });
            that.setData({'comments': res['records']})
        });
    },

    /* ==================== 回复评论 ==================== */

    // 打开回复Sheet
    openReplySheet: function (ev) {
        this.setData({
            'replySheetShow': true,
            'replyTargetId': ev.currentTarget.dataset['commentId'],
        });
    },

    // 关闭回复Sheet
    closeReplySheet: function () {
        this.setData({'replySheetShow': false});
    },

    // 当回复内容改变时触发
    changeReplyText: function (ev) {
        this.setData({'replyText': ev.detail});
    },

    // 回复评论
    sendReplyText: function (ev) {
        let that = this;
        api.post('/comment/save', {
            'type': 1,
            'fkCommentId': this.data.replyTargetId,
            'content': this.data.replyText,
        }).then(res => {
            util.success('回复成功');
            that.setData({'replyText': ''});
            this.pageCommentsByEpisodeId(this.data.episode['id']);
        }).catch(err => util.error('回复异常'))
        this.closeReplySheet();
    },

    // 加载函数
    onLoad: function (options) {
        if (util.isLogin()) {
            const userId = wx.getStorageSync('user').id;
            // 初始化视频容器
            this.setData({
                myVideo: wx.createVideoContext('myVideo')
            });
            // 查询课程详情
            this.getCourseInfo(options['courseId']);
            // 将当前用户连接 WebSocket 服务器
            this.setData({
                'socket': wx.connectSocket({'url': `${constant.SOCKET_SERVER}/api/v1/barrage/${userId}`})
            });
            // 当用户成功连接到 WebSocket 服务器时触发
            this.data.socket.onOpen(res => {
                this.setData({'socketSuccess': true});
            });
            // 当客户端接收到 WebSocket 发送的消息时触发
            this.data.socket.onMessage(res => {
                // 在当前播放的视频上发送一条弹幕
                this.data.myVideo.sendDanmu({
                    'text': res['data'],
                    'color': util.randomColor()
                });
            });
        }
    },

    // 卸载函数
    onUnload: function () {
        if (this.data.socketSuccess) {
            this.setData({'socketSuccess': false});
            // 断连WebSocket
            this.data.socket.close();
        }
    }
});
