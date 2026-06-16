import {GATEWAY_AXIOS, apiPrefixFormat} from "../index.js";

// GET - 按角色主键查询角色的全部菜单ID列表
export function listMenuIdsByRoleIdApi(roleId) {
    return GATEWAY_AXIOS.get(`${apiPrefixFormat('menu')}/listMenuIdsByRoleId/${roleId}`)
}

// PUT - 按角色主键修改角色的菜单列表
export function updateMenusByRoleIdApi(roleId, menuIds) {
    return GATEWAY_AXIOS.put(`${apiPrefixFormat('menu')}/updateMenusByRoleId?roleId=${roleId}&menuIds=${menuIds}`)
}
