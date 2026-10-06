package com.yuan.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuan.demo.entity.Supervisor;
import com.yuan.demo.mapper.SupervisorMapper;
import com.yuan.demo.service.ISupervisorService;
import org.springframework.stereotype.Service;

@Service
public class SupervisorServiceImpl extends ServiceImpl<SupervisorMapper, Supervisor>
        implements ISupervisorService {
}
