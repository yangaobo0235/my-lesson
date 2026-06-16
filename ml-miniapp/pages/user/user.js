import api from '../../utils/api.js';
import util from '../../utils/util.js';
import constant from '../../utils/const.js';

Page({
    data: {
        user: null,
        MINIO_AVATAR: constant.MINIO_AVATAR,
        avatarFile: [],
        NICKNAME_UPDATE_URL: '/pages/user/update-nickname/update-nickname?nickname=',
        GENDER_UPDATE_URL: '/pages/user/update-gender/update-gender?gender=',
        AGE_UPDATE_URL: '/pages/user/update-age/update-age?age=',
        ZODIAC_UPDATE_URL: '/pages/user/update-zodiac/update-zodiac?zodiac=',
        PROVINCE_UPDATE_URL: '/pages/user/update-province/update-province?province=',
        EMAIL_UPDATE_URL: '/pages/user/update-email/update-email?email=',
        INFO_UPDATE_URL: '/pages/user/update-info/update-info?info='
    },

    // 获取个人信息
    getInfo: function () {
        let that = this;
        let url = '/select/' + wx.getStorageSync("user").id;
        api.get('user', url).then(res => {
            that.setData({'user': res});
        }).catch(err => console.log(err));
    },

    // 上传用户头像
    uploadAvatar(ev) {
        const {file} = ev.detail;

        // 检查文件类型
        if (file.type !== 'image') {
            util.error('图片格式有误');
            return false;
        }

        // 检查文件大小
        if (file.size > 500 * 1024) {
            util.error('图片过大');
            return false;
        }

        // 上传文件
        wx.uploadFile({
            url: constant.UPLOAD_AVATAR_URL + wx.getStorageSync("user").id,
            filePath: file.url,
            name: 'avatarFile',
            header: {
                'Content-Type': 'multipart/form-data',
                'token': wx.getStorageSync('token')
            },
            success: (res) => {
                let body;
                try {
                    body = JSON.parse(res.data);
                } catch (err) {
                    util.error('服务器返回格式异常');
                    return;
                }
                if (res.statusCode < 200 || res.statusCode >= 300 || body.code !== constant.STATUS.SUCCESS) {
                    util.error(body.message || '头像上传失败');
                    return;
                }
                this.setData({avatarFile: [file]});
                this.getInfo();
                util.success('上传成功');
            },
            fail: (err) => {
                console.error(err);
                util.error('头像上传失败，请检查网络连接');
            }
        });
    },

    // 退出登录
    logout: function () {
        util.confirm('即将退出登录，确认吗？', () => {
            wx.removeStorageSync('token');
            wx.removeStorageSync('user');
            util.page('/pages/index/login-by-account/login-by-account', false);
        });
    },

    // 加载函数
    onLoad: function () {
        if (util.isLogin()) {
            this.getInfo();
        }
        this.getTabBar().setData({"activeTab": 3});
    },
});
