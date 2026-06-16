import util from "../utils/util.js";

Component({
    data: {
        activeTab: 0,
        tabs: [
            {pagePath: "/pages/index/index", text: "首页", icon: 'home-o'},
            {pagePath: "/pages/course/course", text: "课程", icon: 'shop-o'},
            {pagePath: "/pages/cart/cart", text: "购物车", icon: 'cart-o'},
            {pagePath: "/pages/user/user", text: "我的", icon: 'user-o'}
        ]
    },
    methods: {
        // 切换Tab
        changeTab(ev) {
            util.tab(this.data.tabs[ev.detail]['pagePath']);
        }
    }
});
