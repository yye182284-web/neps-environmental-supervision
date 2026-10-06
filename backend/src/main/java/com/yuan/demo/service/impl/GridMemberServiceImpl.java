package com.yuan.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuan.demo.entity.GridMember;
import com.yuan.demo.mapper.GridMemberMapper;
import com.yuan.demo.service.IGridMemberService;
import org.springframework.stereotype.Service;

@Service
public class GridMemberServiceImpl extends ServiceImpl<GridMemberMapper, GridMember>
        implements IGridMemberService {
}
