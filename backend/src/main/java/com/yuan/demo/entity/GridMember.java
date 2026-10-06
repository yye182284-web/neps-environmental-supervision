package com.yuan.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * AQI 网格员。
 */
@Getter
@Setter
@ToString
@TableName("grid_member")
public class GridMember implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 网格员手机号码。 */
    @TableId(value = "gm_id", type = IdType.INPUT)
    private String gmId;

    /** 网格员名称。 */
    private String gmName;

    /** 网格员登录编码。 */
    private String gmCode;

    /** 登录密码。 */
    private String password;

    /** 网格区域：省编号。 */
    private Integer provinceId;

    /** 网格区域：市编号。 */
    private Integer cityId;

    /** 联系电话。 */
    private String tel;

    /** 状态（0：可工作；1：临时抽调；2：休假；3：其他）。 */
    private Integer state;

    /** 备注。 */
    private String remarks;
}
