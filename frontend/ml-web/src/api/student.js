import {GATEWAY_AXIOS, apiPrefixFormat} from './index.js';

const body = response => response.data;
const responseError = error => error.response?.data || {message: error.message || '请求失败'};
const data = promise => promise.then(body).catch(responseError);
const blob = promise => promise.catch(error => {
    const response = error.response;
    if (response) return response;
    throw new Error(error.message || '请求失败');
});

export const studentApi = {
    banners: (n = 5) => data(GATEWAY_AXIOS.get(`${apiPrefixFormat('banner')}/top/${n}`)),
    articles: (n = 4) => data(GATEWAY_AXIOS.get(`${apiPrefixFormat('article')}/top/${n}`)),
    notices: (n = 4) => data(GATEWAY_AXIOS.get(`${apiPrefixFormat('notice')}/top/${n}`)),
    courseSearch: params => data(GATEWAY_AXIOS.get(`${apiPrefixFormat('course')}/search`, {params})),
    coursePage: params => data(GATEWAY_AXIOS.get(`${apiPrefixFormat('course')}/page`, {params})),
    course: id => data(GATEWAY_AXIOS.get(`${apiPrefixFormat('course')}/select/${id}`)),
    carts: params => data(GATEWAY_AXIOS.get(`${apiPrefixFormat('cart')}/page`, {params})),
    addCart: (userId, courseId) => data(GATEWAY_AXIOS.post(`${apiPrefixFormat('cart')}/insert`, {
        fkUserId: userId,
        fkCourseId: courseId
    })),
    removeCart: id => data(GATEWAY_AXIOS.delete(`${apiPrefixFormat('cart')}/delete/${id}`)),
    clearCart: userId => data(GATEWAY_AXIOS.delete(`${apiPrefixFormat('cart')}/clearByUserId/${userId}`)),
    couponByCode: code => data(GATEWAY_AXIOS.get(`${apiPrefixFormat('coupons')}/selectByCode/${code}`)),
    prePay: params => data(GATEWAY_AXIOS.post(`${apiPrefixFormat('order')}/prePay`, params)),
    qrCode: params => blob(GATEWAY_AXIOS.post(`${apiPrefixFormat('order')}/getQrCode`, params, {responseType: 'blob'})),
    checkOrder: sn => data(GATEWAY_AXIOS.get(`${apiPrefixFormat('order')}/checkStatusBySn/${sn}`)),
    cancelOrder: sn => data(GATEWAY_AXIOS.post(`${apiPrefixFormat('order')}/cancelBySn/${sn}`)),
    orders: params => data(GATEWAY_AXIOS.get(`${apiPrefixFormat('order')}/page`, {params})),
    deleteOrder: id => data(GATEWAY_AXIOS.delete(`${apiPrefixFormat('order')}/delete/${id}`)),
    follows: params => data(GATEWAY_AXIOS.get(`${apiPrefixFormat('follow')}/page`, {params})),
    addFollow: (userId, episodeId) => data(GATEWAY_AXIOS.post(`${apiPrefixFormat('follow')}/insert`, {
        fkUserId: userId,
        fkEpisodeId: episodeId
    })),
    removeFollow: id => data(GATEWAY_AXIOS.delete(`${apiPrefixFormat('follow')}/delete/${id}`)),
    comments: params => data(GATEWAY_AXIOS.get(`${apiPrefixFormat('comment')}/page`, {params})),
    addComment: (userId, episodeId, content) => data(GATEWAY_AXIOS.post(`${apiPrefixFormat('comment')}/insert`, {
        pid: 0,
        fkUserId: userId,
        fkEpisodeId: episodeId,
        content
    })),
    report: (userId, episodeId, content) => data(GATEWAY_AXIOS.post(`${apiPrefixFormat('report')}/insert`, {
        fkUserId: userId,
        fkEpisodeId: episodeId,
        content
    }))
};
