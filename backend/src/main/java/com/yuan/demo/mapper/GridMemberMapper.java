package com.yuan.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuan.demo.entity.GridMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GridMemberMapper extends BaseMapper<GridMember> {
}
