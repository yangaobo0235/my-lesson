import {ElMessage, ElMessageBox} from "element-plus";
import {hasNull, isNotNull, isNull} from "../util/index.js";
import router from "../router/index.js";

/** 常用响应码 */
export const STATUS = {
    SUCCESS: 1000,
    TOKEN_EXPIRED: 8000
}

/**
 * 获取服务器响应中的data数据
 *
 * @param res axios响应结果对象
 * @return result 请求成功时返回data数据或true，响应失败时返回null
 * */
export function getResponseData(res) {
    let result = null;
    // 若响应结果为空，提示并返回null
    if (isNull(res)) {
        ElMessage.warning('服务器无响应！');
        return result;
    }
    // 若存在2层data，则直接拆除第1层data
    res = undefined !== res.data && undefined !== res.data.data ? res.data : res;
    // 请求成功，返回 data 数据
    if (res.code === STATUS.SUCCESS) {
        // DQL 操作成功时返回查询到的数据，DML 操作成功时返回 true
        result = isNotNull(res.data) ? res.data : true;
    }
    // 请求失败 - Token过期：提示并跳转到登录页面
    else if (res.code === STATUS.TOKEN_EXPIRED) {
        ElMessage.warning('Token过期，请重新登录！');
        setTimeout(() => router.push('/'), 1000);
    }
    // 请求失败 - 服务器异常
    else {
        ElMessage.warning(res['message']);
    }
    return result;
}

/**
 * 新增一条数据（表单，异步）
 *
 * <p> form: 表单对象，必须是reactive变量，必传。
 * <p> api: API请求函数名（不带小括号），必传。
 * <p> params: API请求函数参数，JSON格式，作为请求参数，必传。
 * <p> args: API请求函数参数，JSON格式，不作为请求参数，而是有一些其它作用，可选。
 * <p> callback: 回调函数名（不带小括号），即当操作成功后调用的函数，可选。
 */
export async function myInsert(config) {
    // 必传参数
    const form = config['form'];
    const api = config['api'];
    const params = config['params'];
    // 空值保护
    if (hasNull(form, api, params)) return;
    // 可选参数
    const args = config['args'];
    const callback = config['callback'];
    // 验证表单：只有全部校验规则都通过，valid 参数才为 true
    form.value.validate(async (valid) => {
        if (valid) {
            // 发送请求
            let res = isNotNull(args) ? await api(params, args) : await api(params);
            if (isNotNull(getResponseData(res))) {
                // 存在回调函数时，异步调用回调函数
                if (callback) await callback();
            }
        }
    });
}

/**
 * 分页查询数据（异步）
 *
 * <p> api: API请求函数名（不带小括号），必传。
 * <p> params: API请求函数参数，JSON格式，作为请求参数，必传。
 * <p> args: API请求函数参数，JSON格式，不作为请求参数，而是有一些其它作用，可选。
 * <p> records: 分页结果中的数据列表，必传。
 * <p> pageInfo: 分页结果中的分页信息，包括 pageNum，pageSize 和 total 三项，必传。
 */
export async function myPage(config) {
    // 必传参数
    const api = config['api'];
    const records = config['records'];
    const pageInfo = config['pageInfo'];
    const params = config['params'];
    // 空值保护
    if (hasNull(api, params, records, pageInfo)) return;
    // 可选参数
    const args = config['args'];
    // 发送请求
    let res = isNotNull(args) ? await api(params, args) : await api(params);
    let data = getResponseData(res);
    if (isNotNull(data)) {
        records.value = data['list'] || data['records'];
        pageInfo['pageNum'] = data['pageNum'] || data['pageNumber'] || data['page'];
        pageInfo['pageSize'] = data['pageSize'] || data['size'];
        pageInfo['total'] = data['total'] || data['totalRow'];
    }
}

/**
 * 修改一条数据（表单，异步）
 *
 * <p> form: 表单对象，必须是reactive变量，必传。
 * <p> api: API请求函数名（不带小括号），必传。
 * <p> params: API请求函数参数，JSON格式，作为请求参数，必传。
 * <p> args: API请求函数参数，JSON格式，不作为请求参数，而是有一些其它作用，可选。
 * <p> callback: 回调函数名（不带小括号），即当操作成功后调用的函数，可选。
 * <p> successTip: 是否需要成功提示，默认true，可选。
 * <p> successTipContent: 操作成功后的提示文字，可选。
 */
export async function myUpdate(config) {
    await myInsert(config);
}

/**
 * 删除一条数据（异步）
 *
 * <p> id: 数据主键，必传。
 * <p> api: API请求函数名（不带小括号），必传。
 * <p> args: API请求函数参数，JSON格式，不作为请求参数，而是有一些其它作用，可选。
 * <p> callback: 回调函数名（不带小括号），即当操作成功后调用的函数，可选。
 */
export async function myDelete(config) {
    // 必传参数
    const id = config['id'];
    const api = config['api'];
    // 空值保护
    if (hasNull(id, api)) return;
    // 可选参数
    const args = config['args'];
    const callback = config['callback'];
    // 危险操作保护
    await ElMessageBox.confirm('即将删除1条数据，确认吗？').then(async () => {
        // 发送请求
        let res = isNotNull(args) ? await api(id, args) : await api(id);
        if (isNotNull(getResponseData(res))) {
            // 存在回调函数时，异步调用回调函数
            if (callback) callback();
        }
    });
}

/**
 * 批量删除数据（异步）
 *
 * <p> ids: 主键数组，必传。
 * <p> api: API请求函数名（不带小括号），必传。
 * <p> args: API请求函数参数，JSON格式，不作为请求参数，而是有一些其它作用，可选。
 * <p> callback: 回调函数名（不带小括号），即当操作成功后调用的函数，可选。
 */
export async function myDeleteBatch(config) {
    // 必传参数
    const ids = config['ids'];
    const api = config['api'];
    // 空值保护
    if (hasNull(ids, api)) return;
    // 主键数组保护
    if (ids.length <= 0) {
        ElMessage.warning('至少选择1项！');
        return;
    }
    // 可选参数
    const args = config['args'];
    const callback = config['callback'];
    // 危险操作保护
    await ElMessageBox.confirm(`即将删除 ${ids.length} 条数据，确认吗？`).then(async () => {
        // 发送请求
        let res = isNotNull(args) ? await api(ids.join(','), args) : await api(ids.join(','));
        if (isNotNull(getResponseData(res))) {
            // 存在回调函数时，异步调用回调函数
            if (callback) callback();
        }
    })
}
