import {GATEWAY_AXIOS, apiPrefixFormat} from "../index.js";
import {GATEWAY_HOST} from "../../const/index.js";

// 上传头像地址
export const UPLOAD_AVATAR_URL = GATEWAY_HOST + '/user-server/api/v1/user/uploadAvatar/';

// POST - 按账号密码登录
export function loginByAccountApi(params) {
    return GATEWAY_AXIOS.post(`${apiPrefixFormat('user')}/loginByAccount`, params)
}

// GET - 用户统计数据
export function statisticsApi(params) {
    return GATEWAY_AXIOS.get(`${apiPrefixFormat('user')}/statistics`, params)
}

// GET - 获取手机验证码
export function getVcodeApi(phone) {
    return GATEWAY_AXIOS.get(`${apiPrefixFormat('user')}/getVcode/${phone}`)
}

// GET - 查询解绑验证码
export function getUnboundVcodeApi(id) {
    return GATEWAY_AXIOS.get(`${apiPrefixFormat('user')}/getUnboundVcode/${id}`)
}

// GET - 校验解绑验证码
export function checkUnboundVcodeApi(id, vcode) {
    return GATEWAY_AXIOS.get(`${apiPrefixFormat('user')}/checkUnboundVcode/${id}/${vcode}`)
}

// GET - 查询解绑验证码
export function getBoundVcodeApi(phone) {
    return GATEWAY_AXIOS.get(`${apiPrefixFormat('user')}/getBoundVcode/${phone}`)
}

// PUT - 修改用户手机号码
export function updatePhoneApi(params) {
    return GATEWAY_AXIOS.put(`${apiPrefixFormat('user')}/updatePhone`, params)
}

// PUT - 根据主键修改密码
export function updatePasswordApi(params) {
    return GATEWAY_AXIOS.put(`${apiPrefixFormat('user')}/updatePassword`, params)
}

// PUT - 根据主键重置密码
export function resetPasswordApi(id) {
    return GATEWAY_AXIOS.put(`${apiPrefixFormat('user')}/resetPassword/${id}`)
}
