package com.yuan.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * 公众监督员。
 */
@Getter
@Setter
@ToString
@TableName("supervisor")
public class Supervisor implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 公众监督员编号（手机号）。 */
    @TableId(value = "tel_id", type = IdType.INPUT)
    private String telId;

    /** 登录密码。 */
    private String password;

    /** 真实姓名。 */
    private String realName;

    /** 年龄。 */
    private Integer age;

    /** 出生日期。 */
    private String birthday;

    /** 性别（1：男；0：女）。 */
    private Integer sex;

    /** 备注。 */
    private String remarks;
}
