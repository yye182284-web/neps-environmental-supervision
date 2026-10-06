package com.yuan.demo.mapper;

import com.yuan.demo.entity.AqiFeedback;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author laohan
 * @since 2026-09-03
 */
@Mapper
public interface AqiFeedbackMapper extends BaseMapper<AqiFeedback> {
    //制作增加不做修改
    //默认只有传统的基础的CRUD的方法
    //如果我想要特殊的方法，需要你在这里自己写

    @Select("SELECT aqi.*, p.province_name, c.city_name, " +
            "m.so2_concentration, m.co_concentration, m.pm25_concentration, " +
            "m.so2_level_id, m.co_level_id, m.pm25_level_id, " +
            "m.overall_level_id, m.measured_at " +
            "FROM aqi_feedback aqi " +
            "JOIN grid_province p ON p.province_id = aqi.province_id " +
            "JOIN grid_city c ON c.city_id = aqi.city_id AND c.province_id = aqi.province_id " +
            "LEFT JOIN aqi_measurement m ON m.task_id = aqi.af_id AND m.record_status = 'CONFIRMED'")
    public List<AqiFeedback> findAll();
}
