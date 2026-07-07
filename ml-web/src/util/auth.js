const ADMIN_ROLES = ['超级管理员', '管理员', 'ADMIN', 'ROLE_ADMIN'];
const AI_ADMIN_ROLES = [...ADMIN_ROLES, '内容运营', '课程管理员'];

const AI_NAV_ITEMS = [
    {path: '/ai/overview', label: '能力总览', icon: 'Grid', permission: 'ai:use'},
    {path: '/ai/chat', label: 'AI 对话', icon: 'ChatLineRound', permission: 'ai:use'},
    {path: '/ai/conversations', label: '历史会话', icon: 'Clock', permission: 'ai:use'},
    {path: '/ai/plans', label: '学习计划', icon: 'Notebook', permission: 'ai:use'},
    {path: '/ai/approvals', label: '待确认操作', icon: 'CircleCheck', permission: 'ai:approval'},
    {path: '/ai/admin/evaluation', label: '评测与知识库', icon: 'DataAnalysis', permission: 'ai:admin'},
];

const STUDENT_NAV_ITEMS = [
    {path: '/student/home', label: '学习首页', icon: 'House'},
    {path: '/student/courses', label: '课程大厅', icon: 'Reading'},
    {path: '/student/cart', label: '购物车', icon: 'ShoppingCart'},
    {path: '/student/orders', label: '我的订单', icon: 'Tickets'},
    {path: '/student/learning', label: '已购课程', icon: 'VideoPlay'},
    {path: '/student/follows', label: '我的收藏', icon: 'Star'}
];

const ROUTE_PERMISSION = {
    '/User': '/User',
    '/UserInsert': '/User',
    '/UserUpdate': '/User',
    '/UserUpdateRoles': '/User',
    '/Role': '/Role',
    '/RoleInsert': '/Role',
    '/RoleUpdate': '/Role',
    '/RoleUpdateMenus': '/Role',
    '/Menu': '/Menu',
    '/MenuInsert': '/Menu',
    '/MenuUpdate': '/Menu',
    '/SubMenu': '/Menu',
    '/SubMenuInsert': '/Menu',
    '/SubMenuUpdate': '/Menu',
    '/Category': '/Category',
    '/CategoryInsert': '/Category',
    '/CategoryUpdate': '/Category',
    '/Course': '/Course',
    '/CourseInsert': '/Course',
    '/CourseUpdate': '/Course',
    '/Season': '/Course',
    '/SeasonInsert': '/Course',
    '/SeasonUpdate': '/Course',
    '/Episode': '/Course',
    '/EpisodeInsert': '/Course',
    '/EpisodeUpdate': '/Course',
    '/Comment': '/Comment',
    '/CommentInsert': '/Comment',
    '/CommentUpdate': '/Comment',
    '/SubComment': '/Comment',
    '/SubCommentInsert': '/Comment',
    '/SubCommentUpdate': '/Comment',
    '/Report': '/Report',
    '/ReportInsert': '/Report',
    '/ReportUpdate': '/Report',
    '/Notice': '/Notice',
    '/NoticeInsert': '/Notice',
    '/NoticeUpdate': '/Notice',
    '/Article': '/Article',
    '/ArticleInsert': '/Article',
    '/ArticleUpdate': '/Article',
    '/Banner': '/Banner',
    '/BannerInsert': '/Banner',
    '/BannerUpdate': '/Banner',
    '/Coupons': '/Coupons',
    '/CouponsInsert': '/Coupons',
    '/CouponsUpdate': '/Coupons',
    '/Seckill': '/Seckill',
    '/SeckillInsert': '/Seckill',
    '/SeckillUpdate': '/Seckill',
    '/SeckillDetail': '/Seckill',
    '/SeckillDetailInsert': '/Seckill',
    '/SeckillDetailUpdate': '/Seckill',
    '/Cart': '/Cart',
    '/CartInsert': '/Cart',
    '/CartUpdate': '/Cart',
    '/Order': '/Order',
    '/OrderInsert': '/Order',
    '/OrderUpdate': '/Order',
    '/OrderDetail': '/Order',
    '/OrderDetailInsert': '/Order',
    '/OrderDetailUpdate': '/Order',
};

const MODULE_WRITE_ROLES = {
    user: ADMIN_ROLES,
    role: ADMIN_ROLES,
    menu: ADMIN_ROLES,
    category: ['超级管理员', '管理员', 'ADMIN', 'ROLE_ADMIN', '内容运营'],
    course: ['超级管理员', '管理员', 'ADMIN', 'ROLE_ADMIN', '内容运营'],
    season: ['超级管理员', '管理员', 'ADMIN', 'ROLE_ADMIN', '内容运营'],
    episode: ['超级管理员', '管理员', 'ADMIN', 'ROLE_ADMIN', '内容运营'],
    comment: ['超级管理员', '管理员', 'ADMIN', 'ROLE_ADMIN', '内容运营'],
    report: ['超级管理员', '管理员', 'ADMIN', 'ROLE_ADMIN', '内容运营'],
    notice: ['超级管理员', '管理员', 'ADMIN', 'ROLE_ADMIN', '营销运营'],
    article: ['超级管理员', '管理员', 'ADMIN', 'ROLE_ADMIN', '营销运营'],
    banner: ['超级管理员', '管理员', 'ADMIN', 'ROLE_ADMIN', '营销运营'],
    coupons: ['超级管理员', '管理员', 'ADMIN', 'ROLE_ADMIN', '营销运营'],
    seckill: ['超级管理员', '管理员', 'ADMIN', 'ROLE_ADMIN', '营销运营'],
    seckillDetail: ['超级管理员', '管理员', 'ADMIN', 'ROLE_ADMIN', '营销运营'],
    cart: ADMIN_ROLES,
    order: ADMIN_ROLES,
    orderDetail: ADMIN_ROLES,
};

const MODULE_READ_ROLES = {
    ...MODULE_WRITE_ROLES,
};

const MENU_PATH_MODULE = {
    '/User': 'user',
    '/Role': 'role',
    '/Menu': 'menu',
    '/Category': 'category',
    '/Course': 'course',
    '/Comment': 'comment',
    '/Report': 'report',
    '/Notice': 'notice',
    '/Article': 'article',
    '/Banner': 'banner',
    '/Coupons': 'coupons',
    '/Seckill': 'seckill',
    '/Cart': 'cart',
    '/Order': 'order',
};

export function readJsonStorage(key, fallback) {
    try {
        const value = sessionStorage.getItem(key);
        return value ? JSON.parse(value) : fallback;
    } catch {
        return fallback;
    }
}

export function getLoginRoles() {
    return readJsonStorage('loginRoleTitles', []);
}

export function getLoginMenus() {
    return readJsonStorage('loginMenus', []);
}

export function hasAnyRole(roles = getLoginRoles(), allowedRoles = []) {
    return roles.some(role => allowedRoles.some(allowed => role === allowed));
}

export function isAdmin(roles = getLoginRoles()) {
    return hasAnyRole(roles, ADMIN_ROLES);
}

export function isAiAdmin(roles = getLoginRoles()) {
    return hasAnyRole(roles, AI_ADMIN_ROLES);
}

export function canUseAi() {
    return !!sessionStorage.getItem('token');
}

export function canAccessAiPermission(permission, roles = getLoginRoles()) {
    if (permission === 'ai:admin') return isAiAdmin(roles);
    if (permission === 'ai:approval') return canUseAi();
    return canUseAi();
}

export function getAccessibleAiNavItems(roles = getLoginRoles()) {
    return AI_NAV_ITEMS.filter(item => canAccessAiPermission(item.permission, roles));
}

export function getAccessibleStudentNavItems() {
    return canUseAi() ? STUDENT_NAV_ITEMS : [];
}

export function getAccessibleBusinessMenus(menus = getLoginMenus(), roles = getLoginRoles()) {
    if (isAdmin(roles)) return menus;
    return menus.map(menu => {
        const subMenus = (menu?.subMenus || [])
            .filter(subMenu => isBusinessMenuRoute(subMenu?.url))
            .filter(subMenu => canAccessRoute(subMenu?.url, roles, menus));
        if (Array.isArray(menu?.subMenus) && menu.subMenus.length > 0) {
            return subMenus.length > 0 ? {...menu, subMenus} : null;
        }
        const menuPath = menu?.url && menu.url !== '/' ? menu.url : null;
        const menuVisible = menuPath ? isBusinessMenuRoute(menuPath) && canAccessRoute(menuPath, roles, menus) : false;
        return menuVisible ? {...menu, subMenus} : null;
    }).filter(Boolean);
}

export function flattenMenuPaths(menus = getLoginMenus()) {
    const paths = new Set();
    menus.forEach(menu => {
        if (menu?.url && menu.url !== '/') paths.add(menu.url);
        (menu?.subMenus || []).forEach(subMenu => {
            if (subMenu?.url && subMenu.url !== '/') paths.add(subMenu.url);
        });
    });
    return paths;
}

export function canAccessRoute(path, roles = getLoginRoles(), menus = getLoginMenus()) {
    if (path === '/' || path === '/Main' || path === '/Dashboard') return true;
    if (path.startsWith('/Personal')) return true;
    if (path.startsWith('/student/')) return canUseAi();

    const aiItem = AI_NAV_ITEMS.find(item => item.path === path);
    if (aiItem) return canAccessAiPermission(aiItem.permission, roles);

    if (isAdmin(roles)) return true;

    const requiredPath = ROUTE_PERMISSION[path];
    if (!requiredPath) return true;

    const hasMenu = flattenMenuPaths(menus).has(requiredPath);
    if (!hasMenu) return false;

    if (isWriteRoute(path)) {
        return canWriteModule(MENU_PATH_MODULE[requiredPath], roles);
    }

    return canReadModule(MENU_PATH_MODULE[requiredPath], roles);
}

export function canWriteByMenu(basePath, roles = getLoginRoles(), menus = getLoginMenus()) {
    if (isAdmin(roles)) return true;
    const module = MENU_PATH_MODULE[basePath];
    return flattenMenuPaths(menus).has(basePath) && canWriteModule(module, roles);
}

export function canReadModule(module, roles = getLoginRoles()) {
    if (isAdmin(roles)) return true;
    const allowedRoles = MODULE_READ_ROLES[module] || [];
    return hasAnyRole(roles, allowedRoles);
}

export function canWriteModule(module, roles = getLoginRoles()) {
    if (isAdmin(roles)) return true;
    const allowedRoles = MODULE_WRITE_ROLES[module] || [];
    return hasAnyRole(roles, allowedRoles);
}

function isWriteRoute(path) {
    return /Insert$|Update$|UpdateRoles$|UpdateMenus$/.test(path.replace('/', ''));
}

function isBusinessMenuRoute(path) {
    return !!path
        && path !== '/'
        && !path.startsWith('/Personal')
        && !path.startsWith('/student/')
        && !path.startsWith('/ai/');
}
