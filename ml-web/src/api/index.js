import axios from 'axios';
import {GATEWAY_HOST} from '../const';
import {isNotEmpty} from "../util";
import {STATUS} from "../request";

/* =============== Axios实例配置 =============== */

// 创建Axios实例: 配置请求前缀和超时时间
export const GATEWAY_AXIOS = axios.create({
    baseURL: GATEWAY_HOST,
    timeout: 5000
});

/* =============== 基本Axios请求 =============== */

/**
 * API前缀处理：根据模块名称返回API前缀
 *
 * @param module 模块名称，如 user, course 等
 * @return 对应的API前缀，如 /user-server/api/v1/user 等，末尾无 / 符号
 * */
export function apiPrefixFormat(module) {
    // 微服务与模块的映射关系：key 为微服务名称，value 为模块名称数组
    const serviceModuleMap = {
        'user-server': ['menu', 'role', 'user'],
        'course-server': ['category', 'comment', 'course', 'episode', 'report', 'season'],
        'sale-server': ['article', 'banner', 'coupons', 'notice', 'seckill', 'seckillDetail'],
        'order-server': ['cart', 'order', 'orderDetail']
    };
    // 查找匹配的微服务名称：遍历微服务与模块的映射关系，找到包含当前模块 module 的微服务名称
    const microServiceName = Object.keys(serviceModuleMap).find(key => serviceModuleMap[key].includes(module));
    // 返回拼接后的API前缀
    return `/${microServiceName}/api/v1/${module}`;
}

// POST - 添加一条记录
export function insertApi(params, args) {
    return GATEWAY_AXIOS.post(`${apiPrefixFormat(args['module'])}/insert`, params)
}

// GET - 根据主键查询
export function selectApi(id, args) {
    return GATEWAY_AXIOS.get(`${apiPrefixFormat(args['module'])}/select/${id}`)
}

// GET - 分页查询记录
export function pageApi(params, args) {
    return GATEWAY_AXIOS.get(`${apiPrefixFormat(args['module'])}/page`, {params: params});
}

// GET - 查询简单列表
export function simpleListApi(params, args) {
    return GATEWAY_AXIOS.get(`${apiPrefixFormat(args['module'])}/simpleList`);
}

// PUT - 根据主键修改
export function updateApi(params, args) {
    return GATEWAY_AXIOS.put(`${apiPrefixFormat(args['module'])}/update`, params)
}

// DELETE - 根据主键删除
export function deleteApi(id, args) {
    return GATEWAY_AXIOS.delete(`${apiPrefixFormat(args['module'])}/delete/${id}`)
}

// DELETE - 根据主键批删
export function deleteBatchApi(ids, args) {
    return GATEWAY_AXIOS.delete(`${apiPrefixFormat(args['module'])}/deleteBatch?ids=${ids}`)
}

// GET - 下载Excel报表
export function excelApi(url, fileName) {
    // 自动拼接后缀：若文件名未包含 .xlsx 后缀，则自动添加
    fileName = fileName.endsWith('.xlsx') ? fileName : fileName + '.xlsx';
    // 发送下载请求：响应类型为blob，返回Promise对象
    return GATEWAY_AXIOS.get(url, {responseType: 'blob'}).then(res => {
        // 借助超链接标签完成下载功能
        let a = document.createElement('a');
        a.style.display = 'none';
        a.href = URL.createObjectURL(new Blob([res.data]));
        a.setAttribute('download', fileName);
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        a = null;
    });
}

/* =============== 拦截器 =============== */

// 在发送请求前执行
GATEWAY_AXIOS.interceptors.request.use(
    req => {
        // 从 sessionStorage 中获取 Token，加入请求头并放行请求
        const token = sessionStorage.getItem("token");
        if (isNotEmpty(token)) req.headers['token'] = token;
        return req;
    },
    err => Promise.reject(err)
);

// 在接收到响应之后执行（直接放行）
GATEWAY_AXIOS.interceptors.response.use(
    resp => {
        return resp;
    },
    err => Promise.reject(err)
);