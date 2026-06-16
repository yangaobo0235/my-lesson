import {GATEWAY_AXIOS, apiPrefixFormat} from "../index.js";

// GET - 按用户主键查询用户的全部角色ID列表
export function listRoleIdsByUserIdApi(userId) {
    return GATEWAY_AXIOS.get(`${apiPrefixFormat('role')}/listRoleIdsByUserId/${userId}`)
}

// PUT - 按用户主键修改用户的角色列表
export function updateRolesByUserIdApi(userId, roleIds) {
    return GATEWAY_AXIOS.put(`${apiPrefixFormat('role')}/updateRolesByUserId?userId=${userId}&roleIds=${roleIds}`)
}
