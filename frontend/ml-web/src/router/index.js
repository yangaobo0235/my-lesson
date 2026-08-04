import {createRouter, createWebHashHistory} from "vue-router";
import {ElMessage} from "element-plus";
import vuex from "../vuex/index.js";
import Login from "../views/Login.vue";
import Main from "../views/Main.vue";
import {canAccessRoute, getLoginMenus, getLoginRoles} from "../util/auth.js";
const Dashboard = () => import("../views/Dashboard.vue");
const Personal = () => import("../views/personal/Personal.vue");
const PersonalUpdate = () => import("../views/personal/PersonalUpdate.vue");
const PersonalUpdatePhone = () => import('../views/personal/PersonalUpdatePhone.vue');
const User = () => import("../views/ums/user/User.vue");
const UserInsert = () => import("../views/ums/user/UserInsert.vue");
const UserUpdate = () => import("../views/ums/user/UserUpdate.vue");
const UserUpdateRoles = () => import("../views/ums/user/UserUpdateRoles.vue");
const Role = () => import("../views/ums/role/Role.vue");
const RoleInsert = () => import("../views/ums/role/RoleInsert.vue");
const RoleUpdate = () => import("../views/ums/role/RoleUpdate.vue");
const RoleUpdateMenus = () => import("../views/ums/role/RoleUpdateMenus.vue");
const Menu = () => import("../views/ums/menu/Menu.vue");
const MenuInsert = () => import("../views/ums/menu/MenuInsert.vue");
const MenuUpdate = () => import("../views/ums/menu/MenuUpdate.vue");
const SubMenu = () => import("../views/ums/menu/sub/SubMenu.vue");
const SubMenuInsert = () => import("../views/ums/menu/sub/SubMenuInsert.vue");
const SubMenuUpdate = () => import("../views/ums/menu/sub/SubMenuUpdate.vue");
const Category = () => import("../views/cms/category/Category.vue");
const CategoryInsert = () => import("../views/cms/category/CategoryInsert.vue");
const CategoryUpdate = () => import("../views/cms/category/CategoryUpdate.vue");
const Course = () => import("../views/cms/course/Course.vue");
const CourseInsert = () => import("../views/cms/course/CourseInsert.vue");
const CourseUpdate = () => import("../views/cms/course/CourseUpdate.vue");
const Season = () => import("../views/cms/course/season/Season.vue");
const SeasonInsert = () => import("../views/cms/course/season/SeasonInsert.vue");
const SeasonUpdate = () => import("../views/cms/course/season/SeasonUpdate.vue");
const Episode = () => import("../views/cms/course/episode/Episode.vue");
const EpisodeInsert = () => import("../views/cms/course/episode/EpisodeInsert.vue");
const EpisodeUpdate = () => import("../views/cms/course/episode/EpisodeUpdate.vue");
const Comment = () => import("../views/cms/comment/Comment.vue");
const CommentInsert = () => import("../views/cms/comment/CommentInsert.vue");
const CommentUpdate = () => import("../views/cms/comment/CommentUpdate.vue");
const SubComment = () => import("../views/cms/comment/sub/SubComment.vue");
const SubCommentInsert = () => import("../views/cms/comment/sub/SubCommentInsert.vue");
const SubCommentUpdate = () => import("../views/cms/comment/sub/SubCommentUpdate.vue");
const Report = () => import("../views/cms/report/Report.vue");
const ReportInsert = () => import("../views/cms/report/ReportInsert.vue");
const ReportUpdate = () => import("../views/cms/report/ReportUpdate.vue");
const Notice = () => import("../views/sms/notice/Notice.vue");
const NoticeInsert = () => import("../views/sms/notice/NoticeInsert.vue");
const NoticeUpdate = () => import("../views/sms/notice/NoticeUpdate.vue");
const Article = () => import("../views/sms/article/Article.vue");
const ArticleInsert = () => import("../views/sms/article/ArticleInsert.vue");
const ArticleUpdate = () => import("../views/sms/article/ArticleUpdate.vue");
const Banner = () => import("../views/sms/banner/Banner.vue");
const BannerInsert = () => import("../views/sms/banner/BannerInsert.vue");
const BannerUpdate = () => import("../views/sms/banner/BannerUpdate.vue");
const Seckill = () => import("../views/sms/seckill/Seckill.vue");
const SeckillInsert = () => import("../views/sms/seckill/SeckillInsert.vue");
const SeckillUpdate = () => import("../views/sms/seckill/SeckillUpdate.vue");
const SeckillDetail = () => import("../views/sms/seckill/detail/SeckillDetail.vue");
const SeckillDetailInsert = () => import("../views/sms/seckill/detail/SeckillDetailInsert.vue");
const SeckillDetailUpdate = () => import("../views/sms/seckill/detail/SeckillDetailUpdate.vue");
const Coupons = () => import("../views/sms/coupons/Coupons.vue");
const CouponsInsert = () => import("../views/sms/coupons/CouponsInsert.vue");
const CouponsUpdate = () => import("../views/sms/coupons/CouponsUpdate.vue");
const Cart = () => import("../views/oms/cart/Cart.vue");
const CartInsert = () => import("../views/oms/cart/CartInsert.vue");
const CartUpdate = () => import("../views/oms/cart/CartUpdate.vue");
const Order = () => import("../views/oms/order/Order.vue");
const OrderInsert = () => import("../views/oms/order/OrderInsert.vue");
const OrderUpdate = () => import("../views/oms/order/OrderUpdate.vue");
const OrderDetail = () => import("../views/oms/order/detail/OrderDetail.vue");
const OrderDetailInsert = () => import("../views/oms/order/detail/OrderDetailInsert.vue");
const OrderDetailUpdate = () => import("../views/oms/order/detail/OrderDetailUpdate.vue");


const router = createRouter({
    history: createWebHashHistory(),
    routes: [
        {path: '/', name: 'Login', component: Login},
        {
            path: '/Main', name: 'Main', component: Main,
            redirect: '/Dashboard',
            children: [
                {path: '/Dashboard', name: 'Dashboard', component: Dashboard},
                {path: '/Personal', name: 'Personal', component: Personal},
                {path: '/PersonalUpdate', name: 'PersonalUpdate', component: PersonalUpdate},
                {path: '/PersonalUpdatePhone', name: 'PersonalUpdatePhone', component: PersonalUpdatePhone},
                {path: '/User', name: 'User', component: User},
                {path: '/UserInsert', name: 'UserInsert', component: UserInsert},
                {path: '/UserUpdate', name: 'UserUpdate', component: UserUpdate},
                {path: '/UserUpdateRoles', name: 'UserUpdateRoles', component: UserUpdateRoles},
                {path: '/Role', name: 'Role', component: Role},
                {path: '/RoleInsert', name: 'RoleInsert', component: RoleInsert},
                {path: '/RoleUpdate', name: 'RoleUpdate', component: RoleUpdate},
                {path: '/RoleUpdateMenus', name: 'RoleUpdateMenus', component: RoleUpdateMenus},
                {path: '/Menu', name: 'Menu', component: Menu},
                {path: '/MenuInsert', name: 'MenuInsert', component: MenuInsert},
                {path: '/MenuUpdate', name: 'MenuUpdate', component: MenuUpdate},
                {path: '/SubMenu', name: 'SubMenu', component: SubMenu},
                {path: '/SubMenuInsert', name: 'SubMenuInsert', component: SubMenuInsert},
                {path: '/SubMenuUpdate', name: 'SubMenuUpdate', component: SubMenuUpdate},
                {path: '/Category', name: 'Category', component: Category},
                {path: '/CategoryInsert', name: 'CategoryInsert', component: CategoryInsert},
                {path: '/CategoryUpdate', name: 'CategoryUpdate', component: CategoryUpdate},
                {path: '/Course', name: 'Course', component: Course},
                {path: '/CourseInsert', name: 'CourseInsert', component: CourseInsert},
                {path: '/CourseUpdate', name: 'CourseUpdate', component: CourseUpdate},
                {path: '/Season', name: 'Season', component: Season},
                {path: '/SeasonInsert', name: 'SeasonInsert', component: SeasonInsert},
                {path: '/SeasonUpdate', name: 'SeasonUpdate', component: SeasonUpdate},
                {path: '/Episode', name: 'Episode', component: Episode},
                {path: '/EpisodeInsert', name: 'EpisodeInsert', component: EpisodeInsert},
                {path: '/EpisodeUpdate', name: 'EpisodeUpdate', component: EpisodeUpdate},
                {path: '/Comment', name: 'Comment', component: Comment},
                {path: '/CommentInsert', name: 'CommentInsert', component: CommentInsert},
                {path: '/CommentUpdate', name: 'CommentUpdate', component: CommentUpdate},
                {path: '/SubComment', name: 'SubComment', component: SubComment},
                {path: '/SubCommentInsert', name: 'SubCommentInsert', component: SubCommentInsert},
                {path: '/SubCommentUpdate', name: 'SubCommentUpdate', component: SubCommentUpdate},
                {path: '/Report', name: 'Report', component: Report},
                {path: '/ReportInsert', name: 'ReportInsert', component: ReportInsert},
                {path: '/ReportUpdate', name: 'ReportUpdate', component: ReportUpdate},
                {path: '/Notice', name: 'Notice', component: Notice},
                {path: '/NoticeInsert', name: 'NoticeInsert', component: NoticeInsert},
                {path: '/NoticeUpdate', name: 'NoticeUpdate', component: NoticeUpdate},
                {path: '/Article', name: 'Article', component: Article},
                {path: '/ArticleInsert', name: 'ArticleInsert', component: ArticleInsert},
                {path: '/ArticleUpdate', name: 'ArticleUpdate', component: ArticleUpdate},
                {path: '/Banner', name: 'Banner', component: Banner},
                {path: '/BannerInsert', name: 'BannerInsert', component: BannerInsert},
                {path: '/BannerUpdate', name: 'BannerUpdate', component: BannerUpdate},
                {path: '/Seckill', name: 'Seckill', component: Seckill},
                {path: '/SeckillInsert', name: 'SeckillInsert', component: SeckillInsert},
                {path: '/SeckillUpdate', name: 'SeckillUpdate', component: SeckillUpdate},
                {path: '/SeckillDetail', name: 'SeckillDetail', component: SeckillDetail},
                {path: '/SeckillDetailInsert', name: 'SeckillDetailInsert', component: SeckillDetailInsert},
                {path: '/SeckillDetailUpdate', name: 'SeckillDetailUpdate', component: SeckillDetailUpdate},
                {path: '/Coupons', name: 'Coupons', component: Coupons},
                {path: '/CouponsInsert', name: 'CouponsInsert', component: CouponsInsert},
                {path: '/CouponsUpdate', name: 'CouponsUpdate', component: CouponsUpdate},
                {path: '/Cart', name: 'Cart', component: Cart},
                {path: '/CartInsert', name: 'CartInsert', component: CartInsert},
                {path: '/CartUpdate', name: 'CartUpdate', component: CartUpdate},
                {path: '/Order', name: 'Order', component: Order},
                {path: '/OrderInsert', name: 'OrderInsert', component: OrderInsert},
                {path: '/OrderUpdate', name: 'OrderUpdate', component: OrderUpdate},
                {path: '/OrderDetail', name: 'OrderDetail', component: OrderDetail},
                {path: '/OrderDetailInsert', name: 'OrderDetailInsert', component: OrderDetailInsert},
                {path: '/OrderDetailUpdate', name: 'OrderDetailUpdate', component: OrderDetailUpdate},
                {path: '/ai/overview', name: 'AiOverview', component: () => import('../views/ai/Overview.vue')},
                {path: '/ai/chat', name: 'AiChat', component: () => import('../views/ai/Chat.vue')},
                {path: '/ai/conversations', name: 'AiConversations', component: () => import('../views/ai/Conversations.vue')},
                {path: '/ai/plans', name: 'AiPlans', component: () => import('../views/ai/Plans.vue')},
                {path: '/ai/admin/evaluation', name: 'AiAdminEvaluation', component: () => import('../views/ai/AdminEvaluation.vue')},
                {path: '/student/home', name: 'StudentHome', component: () => import('../views/student/Home.vue')},
                {path: '/student/courses', name: 'StudentCourses', component: () => import('../views/student/Courses.vue')},
                {path: '/student/course/:id', name: 'StudentCourseDetail', component: () => import('../views/student/CourseDetail.vue')},
                {path: '/student/cart', name: 'StudentCart', component: () => import('../views/student/Cart.vue')},
                {path: '/student/orders', name: 'StudentOrders', component: () => import('../views/student/Orders.vue')},
                {path: '/student/learning', name: 'StudentLearning', component: () => import('../views/student/Learning.vue')},
                {path: '/student/follows', name: 'StudentFollows', component: () => import('../views/student/Follows.vue')},
                {path: '/student/player/:courseId', name: 'StudentPlayer', component: () => import('../views/student/Player.vue')},
            ]
        }
    ]
});

/*
 * 路由前置守卫：每次转发路由前执行的函数
 * param to: 来源地址
 * param from: 目标地址
 * next: 放行函数
 */
router.beforeEach((to, from, next) => {
    if (to.path === '/') {
        next();
        return;
    }

    if (!vuex.state['loginFlag']) {
        ElMessage.warning('请先登录！');
        setTimeout(() => next('/'), 2000);
        return;
    }

    if (!canAccessRoute(to.path, getLoginRoles(), getLoginMenus())) {
        ElMessage.warning('当前账号没有访问该页面的权限');
        next('/Dashboard');
        return;
    }

    next();
});

export default router;
