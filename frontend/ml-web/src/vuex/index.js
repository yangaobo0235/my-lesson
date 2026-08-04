import {createStore} from 'vuex'

const vuex = createStore({
    state: {
        // 用户登录状态变量: 若sessionStorage中存在token则为true，反之为false
        loginFlag: !!sessionStorage.getItem('token')
    },
    mutations: {
        setLoginFlag: (state, loginFlag) => state.loginFlag = loginFlag
    },
    actions: {
        setLoginFlag: async (context, loginFlag) => await context.commit('setLoginFlag', loginFlag)
    }
});

export default vuex;
