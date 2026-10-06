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
@TableName("grid_province")
public class GridProvince implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 系统网格覆盖省区域编号
     */
    @TableId(value = "province_id", type = IdType.AUTO)
    private Integer provinceId;

    /**
     * 系统网格覆盖省区域名称
     */
    private String provinceName;

    /**
     * 系统网格覆盖省区域简称
     */
    private String provinceAbbr;

    /**
     * 备注
     */
    private String remarks;
}
