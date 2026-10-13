package com.example.dataadmin.service.impl;

import com.example.common.auth.AuthConstants;
import com.example.common.auth.JwtUtils;
import com.example.common.auth.LoginUser;
import com.example.dataadmin.dto.LoginRequest;
import com.example.dataadmin.dto.auth.BaselineIdentity;
import com.example.dataadmin.dto.auth.SsoLoginRequest;
import com.example.dataadmin.entity.SysMenu;
import com.example.dataadmin.entity.SysUser;
import com.example.dataadmin.mapper.SysUserMapper;
import com.example.dataadmin.service.AuthService;
import com.example.dataadmin.service.BaselineSsoClient;
import com.example.dataadmin.service.SsoUserSyncService;
import com.example.dataadmin.vo.auth.LoginVO;
import com.example.dataadmin.vo.auth.RouteVO;
import com.example.dataadmin.vo.auth.UserInfoVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 登录认证业务实现。 */
@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BaselineSsoClient baselineSsoClient;
    private final SsoUserSyncService ssoUserSyncService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${auth.jwt-secret}")
    private String jwtSecret;

    @Value("${auth.expire-seconds}")
    private long expireSeconds;

    public AuthServiceImpl(
            SysUserMapper userMapper,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            BaselineSsoClient baselineSsoClient,
            SsoUserSyncService ssoUserSyncService) {
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.baselineSsoClient = baselineSsoClient;
        this.ssoUserSyncService = ssoUserSyncService;
    }

    /** 校验账号状态和密码，随后生成 JWT 并把权限快照写入 Redis。 */
    @Override
    public LoginVO login(LoginRequest request) {
        validateLoginRequest(request);
        SysUser user = getAvailableUser(request);
        return createSession(user);
    }

    /** 使用平台令牌同步本地账号后创建本系统登录会话。 */
    @Override
    public LoginVO ssoLogin(SsoLoginRequest request) {
        BaselineIdentity identity = baselineSsoClient.authenticate(request);
        SysUser user = ssoUserSyncService.synchronize(identity);
        return createSession(user);
    }

    /** 复用统一的本地JWT和Redis会话生成逻辑。 */
    private LoginVO createSession(SysUser user) {
        LoginUser loginUser = buildLoginUser(user);

        String tokenId = UUID.randomUUID().toString().replace("-", "");
        loginUser.setTokenId(tokenId);
        saveLoginUser(loginUser);

        String token = JwtUtils.createToken(
                user.getId(),
                tokenId,
                jwtSecret,
                expireSeconds);
        // 操作日志只读取该快照的用户名称和ID，不保存登录令牌。
        org.springframework.web.context.request.RequestAttributes attributes =
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(com.example.dataadmin.config.OperationLogAdvice.ACTOR, loginUser,
                    org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        }
        return new LoginVO(token, "Bearer", expireSeconds);
    }

    /** 退出时删除 Redis 会话，使现有 JWT 立即失效。 */
    @Override
    public void logout(String tokenId) {
        if (!isBlank(tokenId)) {
            redisTemplate.delete(AuthConstants.LOGIN_KEY_PREFIX + tokenId);
        }
    }

    @Override
    public UserInfoVO userInfo(LoginUser loginUser) {
        return new UserInfoVO(
                userMapper.findById(loginUser.getUserId()),
                loginUser.getRoles());
    }

    /** 根据用户的角色菜单关系生成左侧菜单树。 */
    @Override
    public List<RouteVO> routes(LoginUser user) {
        // 所有用户统一通过角色菜单关系查询，管理员由初始化数据绑定全部菜单。
        List<SysMenu> menus = userMapper.findPageMenusByUserId(user.getUserId());
        Map<Long, List<SysMenu>> childrenByParent =
                new LinkedHashMap<Long, List<SysMenu>>();
        for (SysMenu menu : menus) {
            Long parentId = menu.getParentId() == null ? 0L : menu.getParentId();
            List<SysMenu> children = childrenByParent.get(parentId);
            if (children == null) {
                children = new ArrayList<SysMenu>();
                childrenByParent.put(parentId, children);
            }
            children.add(menu);
        }
        List<RouteVO> routes = new ArrayList<RouteVO>();
        List<SysMenu> rootMenus = childrenByParent.get(0L);
        if (rootMenus == null) {
            return routes;
        }
        for (SysMenu rootMenu : rootMenus) {
            routes.add(toRoute(rootMenu, childrenByParent));
        }
        return routes;
    }

    /** 将数据库菜单节点递归转换为左侧菜单节点。 */
    private RouteVO toRoute(
            SysMenu menu,
            Map<Long, List<SysMenu>> childrenByParent) {
        List<RouteVO> childRoutes = null;
        List<SysMenu> childMenus = childrenByParent.get(menu.getId());
        if (childMenus != null && !childMenus.isEmpty()) {
            childRoutes = new ArrayList<RouteVO>();
            for (SysMenu childMenu : childMenus) {
                childRoutes.add(toRoute(childMenu, childrenByParent));
            }
        }
        return new RouteVO(
                menu.getLabel(),
                menu.getIcon(),
                menu.getPath(),
                menu.getHidden(),
                childRoutes);
    }

    /** 校验登录请求中的必填字段。 */
    private void validateLoginRequest(LoginRequest request) {
        if (request == null
                || isBlank(request.getUsername())
                || isBlank(request.getPassword())) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
    }

    /** 查询用户并校验密码和账号状态。 */
    private SysUser getAvailableUser(LoginRequest request) {
        SysUser user = userMapper.findByUsername(request.getUsername().trim());
        if (user == null
                || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new IllegalArgumentException("用户已停用");
        }
        return user;
    }

    /** 登录时一次性加载角色和权限，形成当前会话的权限快照。 */
    private LoginUser buildLoginUser(SysUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setRoles(new HashSet<String>(userMapper.findRoles(user.getId())));
        return loginUser;
    }

    /** 把登录会话序列化后写入Redis，零过期时间表示永久保存。 */
    private void saveLoginUser(LoginUser loginUser) {
        String loginKey = AuthConstants.LOGIN_KEY_PREFIX + loginUser.getTokenId();
        try {
            String loginUserJson = objectMapper.writeValueAsString(loginUser);
            if (expireSeconds > 0) {
                redisTemplate.opsForValue().set(
                        loginKey,
                        loginUserJson,
                        expireSeconds,
                        TimeUnit.SECONDS);
            } else {
                redisTemplate.opsForValue().set(loginKey, loginUserJson);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("保存登录状态失败", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

}
