package com.yangaobo.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.*;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.query.QueryMethods;
import com.mybatisflex.core.relation.RelationManager;
import com.yangaobo.component.MyRedis;
import com.yangaobo.constant.ML;
import com.yangaobo.dto.*;
import com.yangaobo.entity.*;
import com.yangaobo.exception.ServiceException;
import com.yangaobo.mapper.UserMapper;
import com.yangaobo.mapper.UserRoleMapper;
import com.yangaobo.result.ResultCode;
import com.yangaobo.service.UserService;
import com.yangaobo.service.VerificationCodeService;
import com.yangaobo.util.MinioUtil;
import com.yangaobo.util.UserUtil;
import com.yangaobo.vo.LoginVO;
import com.yangaobo.vo.PageVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryChain;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yangaobo.vo.UserSimpleListVO;
import jakarta.annotation.Resource;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import cn.hutool.crypto.digest.BCrypt;
import org.springframework.web.multipart.MultipartFile;

import static com.mybatisflex.core.query.QueryMethods.*;
import static com.yangaobo.entity.table.MenuTableDef.MENU;
import static com.yangaobo.entity.table.RoleMenuTableDef.ROLE_MENU;
import static com.yangaobo.entity.table.RoleTableDef.ROLE;
import static com.yangaobo.entity.table.UserRoleTableDef.USER_ROLE;
import static com.yangaobo.entity.table.UserTableDef.USER;

/**
 * 用户表 服务层实现。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private UserRoleMapper userRoleMapper;
    @Resource
    private MyRedis redis;
    @Resource
    private MinioUtil minioUtil;
    @Resource
    private VerificationCodeService verificationCodeService;

    @Override
    public boolean insert(UserInsertDTO dto) {
        String idcard = dto.getIdcard();
        // 校验身份证号
        if (!IdcardUtil.isValidCard(idcard)) {
            throw new ServiceException(ResultCode.ID_CARD_ILLEGAL, "身份证号" + idcard + "错误");
        }
        // 登录账号查重
        // select count(*) from user where username = ?
        String username = dto.getUsername();
        if (QueryChain.of(mapper)
                .where(USER.USERNAME.eq(username))
                .exists()) {
            throw new ServiceException(ResultCode.USERNAME_REPEAT, "登录账号" + username + "重复");
        }
        // 身份证号查重
        // select count(*) from user where idcard = ?
        if (QueryChain.of(mapper)
                .where(USER.IDCARD.eq(dto.getIdcard()))
                .exists()) {
            throw new ServiceException(ResultCode.ID_CARD_REPEAT, "身份证号" + idcard + "重复");
        }
        // 手机号码查重
        // select count(*) from user where phone = ?
        String phone = dto.getPhone();
        if (QueryChain.of(mapper)
                .where(USER.PHONE.eq(phone))
                .exists()) {
            throw new ServiceException(ResultCode.PHONE_REPEAT, "手机号码" + phone + "重复");
        }
        // 电子邮箱查重
        // select count(*) from user where email = ?
        String email = dto.getEmail();
        if (QueryChain.of(mapper)
                .where(USER.EMAIL.eq(email))
                .exists()) {
            throw new ServiceException(ResultCode.EMAIL_REPEAT, "电子邮箱" + email + "重复");
        }
        // 组装实体类
        User user = BeanUtil.copyProperties(dto, User.class);
        user.setNickname(RandomUtil.randomString(10));
        user.setGender(UserUtil.defaultGender(idcard));
        user.setAge(UserUtil.defaultAge(idcard));
        user.setZodiac(UserUtil.defaultZodiac(idcard));
        user.setAvatar(UserUtil.defaultAvatar(idcard));
        user.setProvince(UserUtil.defaultProvince(idcard));
        user.setInfo(StrUtil.isEmpty(dto.getInfo()) ? "该用户很懒，没留下任何描述。" : dto.getInfo());
        user.setCreated(LocalDateTime.now());
        user.setUpdated(LocalDateTime.now());
        // 使用BCrypt指定盐值加密密码
        user.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt(10)));
        // insert into user (username, password, nickname, avatar, phone, email, gender, age, zodiac, province, realname, idcard, info, created, updated) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        if (mapper.insert(user) <= 0) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库添加失败");
        }
        return true;
    }

    @Override
    public User select(Long id) {
        // select * from user where id = ?
        User user = mapper.selectOneWithRelationsById(id);
        if (ObjectUtil.isNull(user)) {
            throw new ServiceException(ResultCode.USER_NOT_FOUND, id + "号用户数据不存在");
        }
        // 数据脱敏
        return UserUtil.desensitization(user);
    }

    @Override
    public List<UserSimpleListVO> simpleList() {
        // select * from user
        return QueryChain.of(mapper)
                .withRelations()
                .listAs(UserSimpleListVO.class);
    }

    @Override
    public PageVO<User> page(UserPageDTO dto) {
        QueryChain<User> queryChain = QueryChain.of(mapper);
        // username条件
        String username = dto.getUsername();
        if (ObjectUtil.isNotNull(username)) {
            queryChain.where(USER.USERNAME.like(username));
        }
        // nickname条件
        String nickname = dto.getNickname();
        if (ObjectUtil.isNotNull(nickname)) {
            queryChain.where(USER.NICKNAME.like(nickname));
        }
        // phone条件
        String phone = dto.getPhone();
        if (ObjectUtil.isNotNull(phone)) {
            queryChain.where(USER.PHONE.like(phone));
        }
        // DB分页
        Page<User> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        Page<User> result = queryChain.withRelations().page(page);
        // 脱敏
        result.setRecords(UserUtil.desensitization(result.getRecords()));
        // 转换为VO
        PageVO<User> pageVO = new PageVO<>();
        BeanUtil.copyProperties(result, pageVO);
        pageVO.setPageNum(result.getPageNumber());
        return pageVO;
    }

    @Override
    public boolean update(UserUpdateDTO dto) {
        // 检查用户是否存在
        this.existsById(dto.getId());
        // 邮箱查重
        // select count(*) from user where email = ? and id != ?
        String email = dto.getEmail();
        if (StrUtil.isNotEmpty(email) && QueryChain.of(mapper)
                .where(USER.EMAIL.eq(dto.getEmail()))
                .and(USER.ID.ne(dto.getId()))
                .exists()) {
            throw new ServiceException(ResultCode.EMAIL_REPEAT, "电子邮箱" + email + "重复");
        }
        // 组装实体类
        User user = BeanUtil.copyProperties(dto, User.class);
        user.setUpdated(LocalDateTime.now());
        // update user set username=?, password=?, nickname=?, avatar=?, phone=?, email=?, gender=?, age=?, zodiac=?, province=?, realname=?, idcard=?, info=?, updated=? where id = ?
        if(!UpdateChain.of(user)
                .where(USER.ID.eq(user.getId()))
                .update()){
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库修改失败");
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean delete(Long id) {
        // 检查用户是否存在
        this.existsById(id);
        // 删除中间表
        // delete from user_role where fk_user_id = ?
        UpdateChain.of(userRoleMapper)
                .where(USER_ROLE.FK_USER_ID.eq(id))
                .remove();
        // 删除基本表
        // delete from user where id = ?
        if (mapper.deleteById(id) <= 0) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库删除失败");
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean deleteBatch(List<Long> ids) {
        // 检查用户是否存在，如果不存在则直接抛出异常
        // select count(*) from user where id in (?)
        if (QueryChain.of(mapper)
                .where(USER.ID.in(ids))
                .count() < ids.size()) {
            throw new ServiceException(ResultCode.USER_NOT_FOUND, "至少一个用户数据不存在");
        }
        // 删除中间表
        // delete from user_role where fk_user_id in (?)
        UpdateChain.of(userRoleMapper)
                .where(USER_ROLE.FK_USER_ID.in(ids))
                .remove();
        // 删除基本表
        // delete from user where id in (?)
        if (mapper.deleteBatchByIds(ids) != ids.size()) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库删除失败");
        }
        return true;
    }

    @Override
    public String resetPassword(Long id) {
        // 检查用户是否存在
        this.existsById(id);
        String temporaryPassword = RandomUtil.randomString(12);
        // 重置密码
        // update user set password = ? where id = ?
        if (!UpdateChain.of(mapper)
                .set(USER.PASSWORD, BCrypt.hashpw(temporaryPassword, BCrypt.gensalt(10)))
                .where(USER.ID.eq(id))
                .update()) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库重置密码失败");
        }
        return temporaryPassword;
    }

    @Override
    public boolean updatePassword(UserUpdatePasswordDTO dto) {
        Long id = dto.getId();
        // 判断用户是否存在
        // select count(*) from user where id = ?
        User user = mapper.selectOneById(id);
        if (ObjectUtil.isNull(user)) {
            throw new ServiceException(ResultCode.USER_NOT_FOUND, id + "号用户数据不存在");
        }
        // 判断旧密码是否正确
        if (!BCrypt.checkpw(dto.getOldPassword(), user.getPassword())) {
            throw new ServiceException(ResultCode.OLD_PASSWORD_ILLEGAL, id + "号用户数据旧密码错误");
        }
        // 修改密码
        // update user set password = ? where id = ?
        if (!UpdateChain.of(mapper)
                .set(USER.PASSWORD, BCrypt.hashpw(dto.getNewPassword(), BCrypt.gensalt(10)))
                .where(USER.ID.eq(id))
                .update()) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库修改密码失败");
        }
        return true;
    }

    @Override
    public List<UserExcelDTO> getExcelData() {
        // 查询全部用户记录
        // select * from user
        List<User> users = QueryChain.of(mapper).withRelations().list();
        // 类型转换：List<User> -> List<UserExcelDTO>
        List<UserExcelDTO> result = new ArrayList<>();
        users.forEach(user -> {
            UserExcelDTO userExcelDTO = BeanUtil.copyProperties(user, UserExcelDTO.class);
            userExcelDTO.setGender(ML.User.genderFormat(user.getGender()));
            userExcelDTO.setRealname(DesensitizedUtil.chineseName(user.getRealname()));
            userExcelDTO.setIdcard(DesensitizedUtil.idCardNum(user.getIdcard(), 6, 3));
            userExcelDTO.setPhone(DesensitizedUtil.mobilePhone(user.getPhone()));
            result.add(userExcelDTO);
        });
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public String uploadAvatar(MultipartFile newFile, Long id) {

        // 按主键查询记录
        // select * from user where id = ?
        User user = mapper.selectOneById(id);
        if (ObjectUtil.isNull(user)) {
            throw new ServiceException(ResultCode.USER_NOT_FOUND, id + "号用户数据不存在");
        }

        // 备份旧文件名
        String oldFileName = user.getAvatar();

        // 生成新文件名
        String newFileName = minioUtil.randomFilename(newFile);

        try {

            // DB更新文件名
            user.setAvatar(newFileName);
            if (mapper.update(user) <= 0) {
                throw new ServiceException(ResultCode.SERVER_ERROR, "数据库更新失败");
            }

            // MinIO删除旧文件（默认文件不删除）
            if (!ML.User.DEFAULT_AVATARS.contains(oldFileName)) {
                minioUtil.delete(oldFileName, ML.MinIO.AVATAR_DIR, ML.MinIO.BUCKET_NAME);
            }

            // MinIO上传新文件
            minioUtil.upload(newFile, newFileName, ML.MinIO.AVATAR_DIR, ML.MinIO.BUCKET_NAME);
        } catch (Exception e) {
            throw new ServiceException(ResultCode.SERVER_ERROR, "MinIO操作失败：" + e.getMessage());
        }

        // 返回新文件名
        return newFileName;
    }

    @Override
    public boolean getUnboundVcode(Long id, String clientIp) {

        // 通过用户主键查询旧的手机号码
        // select phone from user where id = ?
        String phone = QueryChain.of(mapper)
                .select(USER.PHONE)
                .where(USER.ID.eq(id))
                .objAs(String.class);
        if (ObjectUtil.isNull(phone)) {
            throw new ServiceException(ResultCode.PHONE_NOT_FOUND, "手机号码" + phone + "不存在");
        }

        // 将短信验证码存入redis中，有效期5分钟
        verificationCodeService.send("unbound", phone, clientIp);
        return true;
    }

    @Override
    public boolean checkUnboundVcode(Long id, String vcode) {

        // 通过用户主键获取 phone 字段
        // select phone from user where id = ?
        String phone = QueryChain.of(mapper)
                .select(USER.PHONE)
                .where(USER.ID.eq(id))
                .objAs(String.class);
        if (ObjectUtil.isNull(phone)) {
            throw new ServiceException(ResultCode.PHONE_NOT_FOUND, "手机号码" + phone + "不存在");
        }

        // 校验验证码是否有效
        verificationCodeService.verify("unbound", phone, vcode);
        return true;
    }

    @Override
    public boolean getBoundVcode(String phone, String clientIp) {

        // 手机号码查重
        // select count(*) from user where phone = ?
        if (QueryChain.of(mapper)
                .select(USER.PHONE)
                .where(USER.PHONE.eq(phone))
                .exists()) {
            throw new ServiceException(ResultCode.PHONE_REPEAT, "手机号码" + phone + "重复");
        }

        // 将短信验证码存入redis中，有效期5分钟
        verificationCodeService.send("bound", phone, clientIp);
        return true;
    }

    @Override
    public boolean updatePhone(UserUpdatePhoneDTO dto) {
        Long id = dto.getId();
        String phone = dto.getPhone();
        String vcode = dto.getVcode();

        // 检查用户是否存在
        this.existsById(id);

        // 手机号码查重
        // select count(*) from user where phone = ?
        if (QueryChain.of(mapper)
                .select(USER.PHONE)
                .where(USER.PHONE.eq(dto.getPhone()))
                .exists()) {
            throw new ServiceException(ResultCode.PHONE_REPEAT, "手机号码" + phone + "重复");
        }

        // 校验验证码是否有效
        verificationCodeService.verify("bound", phone, vcode);

        // 修改用户手机号，修改成功后，删除旧的验证码
        // update user set phone = ? where id = ?
        if (UpdateChain.of(mapper)
                .set(USER.PHONE, phone)
                .where(USER.ID.eq(dto.getId()))
                .update()) {
        } else {
            throw new ServiceException(ResultCode.SERVER_ERROR, "数据库修改失败");
        }
        return true;
    }

    @Override
    public LoginVO loginByAccount(LoginByAccountDTO dto) {
        String username = dto.getUsername();
        String password = dto.getPassword();

        // 按账号查询用户记录
        // select * from user where username = ?
        User user = QueryChain.of(mapper)
                .where(USER.USERNAME.eq(username))
                .one();
        if (ObjectUtil.isNull(user)) {
            throw new ServiceException(ResultCode.ACCOUNT_ILLEGAL, "账号" + username + "不存在");
        }

        // 校验密码是否匹配
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new ServiceException(ResultCode.ACCOUNT_ILLEGAL, "密码" + username + "错误");
        }

        // 构建登录VO
        return this.buildLoginVO(user);
    }

    /**
     * 组装LoginVO
     *
     * @param user 用户实体
     * @return LoginVO: 包含用户信息，对应该用户的Token令牌，角色标题列表以及菜单列表
     */
    private LoginVO buildLoginVO(User user) {
        LoginVO result = new LoginVO();

        // 生成Token令牌
        String tokenKey = UUID.randomUUID().toString();

        // 查询用户角色ID列表
        // select fk_role_id from user_role where fk_user_id = ?
        List<Long> roleIds = QueryChain.of(UserRole.class)
                .select(USER_ROLE.FK_ROLE_ID)
                .where(USER_ROLE.FK_USER_ID.eq(user.getId()))
                .objListAs(Long.class);
        if (CollUtil.isEmpty(roleIds)) {
            List<String> roleTitles = List.of();
            cachePrincipal(tokenKey, user, roleTitles);
            result.setRoleTitles(null);
            result.setMenus(null);
            result.setUser(UserUtil.desensitization(user));
            result.setToken(tokenKey);
            return result;
        }

        // 查询角色标题列表
        // select title from role where id in (1, 2, 3, 4, 5)
        List<String> roleTitles = QueryChain.of(Role.class)
                .select(ROLE.TITLE)
                .where(ROLE.ID.in(roleIds))
                .objListAs(String.class);

        // 查询用户菜单ID列表
        // select fk_menu_id from role_menu where fk_role_id in (1, 2, 3, 4, 5)
        List<Long> menuIds = QueryChain.of(RoleMenu.class)
                .select(ROLE_MENU.FK_MENU_ID)
                .where(ROLE_MENU.FK_ROLE_ID.in(roleIds))
                .objListAs(Long.class);
        if (CollUtil.isEmpty(menuIds)) {
            cachePrincipal(tokenKey, user, roleTitles);
            result.setRoleTitles(roleTitles);
            result.setMenus(null);
            result.setUser(UserUtil.desensitization(user));
            result.setToken(tokenKey);
            return result;
        }

        // 查询用户菜单列表，只查询父菜单，级联子菜单
        // select * from menu where id in (1, 2, 3, 4, 5) and pid = 0 order by idx asc, id desc
        RelationManager.addIgnoreRelations("parentMenu");
        List<Menu> menus = QueryChain.of(Menu.class)
                .where(MENU.ID.in(menuIds))
                .and(MENU.PID.eq(ML.Menu.ROOT_ID))
                .orderBy(MENU.IDX.asc(), MENU.ID.desc())
                .withRelations()
                .list();
        // 组装VO
        cachePrincipal(tokenKey, user, roleTitles);
        result.setRoleTitles(roleTitles);
        result.setMenus(menus);
        result.setUser(UserUtil.desensitization(user));
        result.setToken(tokenKey);
        return result;
    }

    private void cachePrincipal(String token, User user, List<String> roleTitles) {
        TokenPrincipal principal = new TokenPrincipal(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                roleTitles == null ? List.of() : List.copyOf(roleTitles)
        );
        String principalJson = JSONUtil.createObj()
                .set("id", principal.id())
                .set("username", principal.username())
                .set("nickname", principal.nickname())
                .set("roles", principal.roles())
                .toString();
        redis.setEx(token, principalJson, 30, TimeUnit.MINUTES);
    }

    @Override
    public boolean getVcode(String phone, String clientIp) {

        // 检查手机号码是否存在
        // select count(*) from user where phone = ?
        if (!QueryChain.of(mapper)
                .where(USER.PHONE.eq(phone))
                .exists()) {
            throw new ServiceException(ResultCode.PHONE_NOT_FOUND, "手机号码" + phone + "不存在");
        }

        // 将短信验证码存入redis中，有效期5分钟
        verificationCodeService.send("login", phone, clientIp);
        return true;
    }

    @Override
    public LoginVO loginByPhone(LoginByPhoneDTO dto) {
        String vcode = dto.getVcode();
        String phone = dto.getPhone();

        // 校验验证码是否有效
        verificationCodeService.verify("login", phone, vcode);

        // 根据手机号码查询用户记录
        // select * from user where phone = ?
        User user = QueryChain.of(mapper)
                .where(USER.PHONE.eq(phone))
                .one();
        if (ObjectUtil.isNull(user)) {
            throw new ServiceException(ResultCode.PHONE_NOT_FOUND, "手机号码" + phone + "不存在");
        }

        // 构建登录VO
        return buildLoginVO(user);
    }

    @Override
    public Map<String, Object> statistics() {
        String dataFromRedis = redis.get(ML.Redis.USER_STATISTICS_DATA_PREFIX);
        if (StrUtil.isNotBlank(dataFromRedis)) {
            return JSONUtil.parseObj(dataFromRedis);
        }
        Map<String, Object> result = new HashMap<>();

        // 统计用户性别比例
        // select gender as name, count(*) as value from `user` group by gender
        result.put("genderCount", QueryChain.of(mapper)
                .select(USER.GENDER.as("name"), QueryMethods.count().as("value"))
                .groupBy(USER.GENDER)
                .orderBy(USER.GENDER.asc())
                .listAs(Map.class));

        // 统计今日用户数
        // select count(*) from `user` where datediff(curdate(), date_format(created, '%Y-%m-%d')) = 0
        double todayCount = QueryChain.of(mapper)
                .where(dateDiff(currentDate(), dateFormat(USER.CREATED, "%Y-%m-%d")).eq(0))
                .count();

        // 统计昨日用户数
        // select count(*) from `user` where datediff(curdate(), date_format(created, '%Y-%m-%d')) = 1
        double yesterdayCount = QueryChain.of(mapper)
                .where(dateDiff(currentDate(), dateFormat(USER.CREATED, "%Y-%m-%d")).eq(1))
                .count();

        // 统计今年用户数
        // select count(*) from `user` where year(created) = year(current_date);
        double thisYearCount = QueryChain.of(mapper)
                .where(year(USER.CREATED).eq(year(currentDate())))
                .count();

        // 统计去年用户总数
        // select count(*) from `user` where year(created) - year(current_date) = -1;
        double lastYearCount = QueryChain.of(mapper)
                .where(year(USER.CREATED).subtract(year(currentDate())).eq(-1))
                .count();

        result.put("todayCount", todayCount);
        result.put("yesterdayCount", yesterdayCount);
        result.put("dayIncrease", this.increase(todayCount, yesterdayCount));
        result.put("thisYearCount", thisYearCount);
        result.put("lastYearCount", lastYearCount);
        result.put("yearIncrease", this.increase(thisYearCount, lastYearCount));
        redis.setEx(ML.Redis.USER_STATISTICS_DATA_PREFIX, JSONUtil.toJsonStr(result), 2, TimeUnit.HOURS);
        return result;
    }

    /**
     * 计算a到b的增长率
     *
     * @param a 第一个操作数
     * @param b 第二个操作数
     * @return 保留两位小数的增长率
     */
    private String increase(double a, double b) {
        if (b == 0) {
            return a > b ? "100.00" : a < b ? "-100.00" : "0";
        }
        return String.format("%.2f", (a - b) / b);
    }

    /**
     * 检查用户是否存在，如果不存在则直接抛出异常
     *
     * @param id 用户主键
     */
    private void existsById(Long id) {
        // select count(*) from user where id = ?
        if (!QueryChain.of(mapper)
                .where(USER.ID.eq(id))
                .exists()) {
            throw new ServiceException(ResultCode.USER_NOT_FOUND, id + "号用户数据不存在");
        }
    }

}
