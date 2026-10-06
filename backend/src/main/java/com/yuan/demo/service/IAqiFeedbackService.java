package com.yuan.demo.service;

import com.yuan.demo.entity.AqiFeedback;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author laohan
 * @since 2026-09-03
 */
public interface IAqiFeedbackService extends IService<AqiFeedback> {
    public List<AqiFeedback> findAll();
}
