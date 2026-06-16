import api from '../../../utils/api.js';
import util from '../../../utils/util.js';
import constant from '../../../utils/const.js';

Page({
    data: {
        user: null,
    },

    // 获取个人信息
    getInfo: function () {
        let that = this;
        let url = '/select/' + wx.getStorageSync("user").id;
        api.get('user', url).then(res => {
            res['updated'] = util.dateFormat(res['updated']);
            res['created'] = util.dateFormat(res['created']);
            that.setData({'user': res});
        }).catch(err => console.error(err));
    },

    // 注销账号
    remove: function () {
        util.confirm('即将注销账号，确认吗？', () => {
            let url = '/delete/' + wx.getStorageSync("user").id;
            api.del('user', url).then(res => {
                util.success('注销成功');
                util.page('/pages/login/login-by-account/login-by-account', false);
            }).catch(err => console.log(err));
        });
    },

    // 修改密码
    updatePassword: function () {
        util.page('/pages/user/account/update-password/update-password', false);
    },

    onLoad: function (options) {
        if (util.isLogin()) {
            this.getInfo();
        }
    }
});
