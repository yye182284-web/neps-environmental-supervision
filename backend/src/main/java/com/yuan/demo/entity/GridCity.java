package com.yuan.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 
 * </p>
 *
 * @author laohan
 * @since 2026-09-08
 */
@Getter
@Setter
@TableName("grid_city")
public class GridCity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 系统网格覆盖市区域编号
     */
    @TableId(value = "city_id", type = IdType.AUTO)
    private Integer cityId;

    /**
     * 系统网格覆盖市区域名称
     */
    private String cityName;

    /**
     * 所属省区域编号
     */
    private Integer provinceId;

    /**
     * 备注
     */
    private String remarks;
}
