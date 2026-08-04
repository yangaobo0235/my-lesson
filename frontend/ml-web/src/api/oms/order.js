import {GATEWAY_AXIOS, apiPrefixFormat} from "../index.js";

// GET - 订单统计数据
export function statisticsApi(params) {
    return GATEWAY_AXIOS.get(`${apiPrefixFormat('order')}/statistics`, params)
}
