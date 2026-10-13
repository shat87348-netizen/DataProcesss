package com.example.dataadmin.dto;

import javax.validation.constraints.Email;

/** 新增或修改用户时使用的请求参数。 */
public class UserSaveRequest {

    /** 用户主键，修改时必填。 */
    private Long id;

    /** 登录用户名；修改时未传保留原值，传入时不能为空，管理员用户名不能修改。 */
    private String username;
    /** 登录密码；新增必填，编辑时按接口规则处理。 */
    private String password;
    /** 用户昵称。 */
    private String nickname;
    /** 联系手机号。 */
    private String phone;
    /** 联系邮箱。 */
    @Email(message = "邮箱格式不正确")
    private String email;
    /** 用户状态：1 启用，0 停用。 */
    private Integer status;
    /** 分配的角色 ID。 */
    private Long roleId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
}
