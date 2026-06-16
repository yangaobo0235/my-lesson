import util from './util.js';
import constant from './const.js';

/**
 * （私有）API前缀处理：根据模块名称返回API前缀
 *
 * @param module 模块名称，如 user, course 等
 * @return 对应的API前缀，如 /user-server/api/v1/user 等，末尾无 / 符号
 * */
function apiPrefixFormat(module) {
    // 微服务与模块的映射关系：key 为微服务名称，value 为模块名称数组
    const serviceModuleMap = {
        'user-server': ['menu', 'role', 'user'],
        'course-server': ['category', 'comment', 'course', 'episode', 'follow', 'report', 'season'],
        'sale-server': ['article', 'banner', 'coupons', 'notice', 'seckill', 'seckillDetail'],
        'order-server': ['cart', 'order', 'orderDetail']
    };
    // 查找匹配的微服务名称：遍历微服务与模块的映射关系，找到包含当前模块 module 的微服务名称
    const microServiceName = Object.keys(serviceModuleMap).find(key => serviceModuleMap[key].includes(module));
    // 返回拼接后的API前缀
    return `/${microServiceName}/api/v1/${module}`;
}

/**
 * （私有）请求方法
 *
 * <p> module: 模块名称，如 user，必传。
 * <p> url: 请求地址，如 /selectById，必传。
 * <p> method: 请求方式，默认 GET。
 * <p> header: 请求头，默认 application/json 类型并自动携带 Token 令牌。
 * <p> params: API请求函数参数，JSON格式，作为请求参数。
 *
 * @returns Promise 函数
 */
function sendRequest(config) {

    // 必传参数（需要空值保护）
    let module = config['module'];
    let url = config['url'];
    if (util.hasNull(module, url)) return;

    // 可选参数（需要设置默认值）
    let method = config['method'];
    let params = config['params'];
    let header = config['header'];
    if (util.isEmpty(method)) method = 'GET';
    if (util.isNull(header)) {
        let token = wx.getStorageSync('token') || null;
        header = {'Content-Type': 'application/json', 'token': token};
    }

    // 处理 url 参数：添加对应的网关路由名称
    url = constant.GATEWAY_HOST + apiPrefixFormat(module) + url;

    // 发送请求
    return new Promise((resolve, reject) => {
        wx.request({
            url: url,
            method: method,
            data: params,
            header: header,
            success(res) {
                if (util.isNull(res)) util.error('服务器无响应');
                // 若存在2层data，则直接拆除第1层data
                res = undefined !== res.data && undefined !== res.data['data'] ? res.data : res;
                // 请求成功，返回 data 数据
                if (res['code'] === constant.STATUS.SUCCESS) {
                    // DQL 操作成功时返回查询到的数据，DML 操作成功时返回 true
                    resolve(util.isNotNull(res.data) ? res.data : true);
                }
                // 请求失败 - 服务器异常
                else {
                    util.error(res['message']);
                    reject(new Error(res['message'] || '请求失败'));
                }
            },
            // 请求失败
            fail(err) {
                reject(new Error('请求异常: ' + (err.errMsg || JSON.stringify(err))));
            }
        });
    });
}

/**
 * 发送 GET 请求
 *
 * @param module 模块名称，如 user 等
 * @param url 请求地址，如 /selectById 等
 * @param params API请求函数参数，JSON格式，默认 null
 * @returns {Promise<unknown>}
 */
function get(module, url, params = null) {
    return sendRequest({module, url, params});
}

/**
 * 发送 POST 请求
 *
 * @param module 模块名称，如 user 等
 * @param url 请求地址，如 /selectById 等
 * @param params API请求函数参数，JSON格式，默认 null
 * @returns {Promise<unknown>}
 */
function post(module, url, params = null) {
    return sendRequest({module, url, params, method: 'POST'});
}

/**
 * 发送 PUT 请求
 *
 * @param module 模块名称，如 user 等
 * @param url 请求地址，如 /selectById 等
 * @param params API请求函数参数，JSON格式，默认 null
 * @returns {Promise<unknown>}
 */
function put(module, url, params = null) {
    return sendRequest({module, url, params, method: 'PUT'});
}

/**
 * 发送 DEL 请求
 *
 * @param module 模块名称，如 user 等
 * @param url 请求地址，如 /selectById 等，默认 null
 * @param params API请求函数参数，JSON格式
 * @returns {Promise<unknown>}
 */
function del(module, url, params = null) {
    return sendRequest({module, url, params, method: 'DELETE'});
}

// 导出
module.exports = {get, post, put, del};
