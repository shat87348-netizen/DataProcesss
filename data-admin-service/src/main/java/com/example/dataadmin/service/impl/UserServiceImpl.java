package com.example.dataadmin.service.impl;

import com.example.dataadmin.dto.UserSaveRequest;
import com.example.dataadmin.entity.SysUser;
import com.example.dataadmin.mapper.SysUserMapper;
import com.example.dataadmin.service.UserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 系统用户业务实现。 */
@Service
public class UserServiceImpl implements UserService {
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_ROLE_KEY = "admin";
    private final SysUserMapper mapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserServiceImpl(SysUserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SysUser> list() {
        return mapper.findAll();
    }

    @Override
    public SysUser get(Long id) {
        return mapper.findById(id);
    }

    /** 新增用户时先校验用户名，再加密密码并分配角色。 */
    @Override
    @Transactional
    public SysUser create(UserSaveRequest request) {
        if (request == null || blank(request.getUsername()) || blank(request.getPassword())) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        if (mapper.findByUsername(request.getUsername().trim()) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        SysUser user = toUser(request);
        user.setUsername(request.getUsername().trim());
        user.setPassword(encoder.encode(request.getPassword()));
        mapper.insert(user);
        assignRole(user, request.getRoleId());
        return mapper.findById(user.getId());
    }

    /** 用户资料与角色关系在同一事务中更新。 */
    @Override
    @Transactional
    public SysUser update(Long id, UserSaveRequest request) {
        // 先检查请求与原用户，复用已有的用户存在性校验。
        if (request == null) {
            throw new IllegalArgumentException("用户资料不能为空");
        }
        SysUser existingUser = requiredUser(id);
        String username = resolveUpdatedUsername(existingUser, request.getUsername());

        // 将校验后的登录用户名与其他资料一起保存，用户名未传时保留原值。
        SysUser user = toUser(request);
        user.setId(id);
        user.setUsername(username);
        try {
            if (mapper.update(user) == 0) {
                throw new IllegalArgumentException("用户不存在或已删除");
            }
        } catch (DuplicateKeyException exception) {
            // 数据库唯一约束兜底处理并发重名和已删除账号占用的用户名。
            throw new IllegalArgumentException("用户名已存在", exception);
        }

        // 角色调整仍与用户资料共用事务，返回重新查询后的最新用户名。
        assignRole(user, request.getRoleId());
        return mapper.findById(id);
    }

    /** 解析修改后的用户名，并检查空值、管理员保护和用户名唯一性。 */
    private String resolveUpdatedUsername(SysUser existingUser, String requestedUsername) {
        // 兼容仅修改其他资料的请求；明确传入空字符串时拒绝保存。
        if (requestedUsername == null) {
            return existingUser.getUsername();
        }
        if (blank(requestedUsername)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        String username = requestedUsername.trim();
        if (username.length() > 64) {
            throw new IllegalArgumentException("用户名长度不能超过64个字符");
        }

        // 保底管理员不能改名，其他账号也不能通过改名占用管理员身份。
        if (ADMIN_USERNAME.equalsIgnoreCase(existingUser.getUsername())) {
            if (!existingUser.getUsername().equals(username)) {
                throw new IllegalArgumentException("不能修改管理员用户名");
            }
        } else if (ADMIN_USERNAME.equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("不能使用管理员用户名");
        }

        // 复用已有查询检查重名，同一用户保留原用户名时允许保存。
        SysUser sameNameUser = mapper.findByUsername(username);
        if (sameNameUser != null && !existingUser.getId().equals(sameNameUser.getId())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        return username;
    }

    /** 超级管理员为系统保底账号，不允许删除。 */
    @Override
    @Transactional
    public void delete(Long id) {
        SysUser user = requiredUser(id);
        if (ADMIN_USERNAME.equalsIgnoreCase(user.getUsername())) {
            throw new IllegalArgumentException("不能删除管理员");
        }
        mapper.deleteUserRoles(id);
        mapper.delete(id);
    }

    /** 独立更新用户状态；管理员保底账号不允许禁用。 */
    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("用户状态只能为0或1");
        }
        SysUser user = requiredUser(id);
        if (ADMIN_USERNAME.equalsIgnoreCase(user.getUsername()) && status == 0) {
            throw new IllegalArgumentException("不能禁用管理员");
        }
        if (mapper.updateStatus(id, status) == 0) {
            throw new IllegalArgumentException("用户不存在或已删除");
        }
    }

    /** 重置密码属于数据库写操作，异常时由事务统一回滚。 */
    @Override
    @Transactional
    public void resetPassword(Long id, String password) {
        if (blank(password)) {
            throw new IllegalArgumentException("密码不能为空");
        }
        mapper.resetPassword(id, encoder.encode(password));
    }

    /** 请求提供角色时，以新角色替换用户原有角色。 */
    private void assignRole(SysUser user, Long roleId) {
        // 本地admin必须始终绑定超级管理员角色，不能被普通角色替换。
        if (ADMIN_USERNAME.equalsIgnoreCase(user.getUsername())) {
            com.example.dataadmin.entity.SysRole administratorRole =
                    mapper.findRoleByKey(ADMIN_ROLE_KEY);
            if (administratorRole == null
                    || !Integer.valueOf(1).equals(administratorRole.getStatus())) {
                throw new IllegalStateException("超级管理员角色不存在或已停用");
            }
            mapper.deleteUserRoles(user.getId());
            mapper.insertUserRole(user.getId(), administratorRole.getId());
            return;
        }
        if (roleId != null) {
            if (mapper.countEnabledRole(roleId) == 0) {
                throw new IllegalArgumentException("角色不存在或已停用");
            }
            mapper.deleteUserRoles(user.getId());
            mapper.insertUserRole(user.getId(), roleId);
        }
    }

    /** 查询必须存在的本地用户。 */
    private SysUser requiredUser(Long id) {
        SysUser user = mapper.findById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    private SysUser toUser(UserSaveRequest request) {
        SysUser user = new SysUser();
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        return user;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
