package com.yuan.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yuan.demo.common.ResultVO;
import com.yuan.demo.entity.GridCity;
import com.yuan.demo.entity.GridProvince;
import com.yuan.demo.service.IGridCityService;
import com.yuan.demo.service.IGridProvinceService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.sql.Wrapper;
import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author laohan
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/province")
@AllArgsConstructor
public class GridProvinceController {
    private final IGridProvinceService provinceService; //省的service
    private final IGridCityService cityService; //市的 service

    @GetMapping("list") // 查询所有省
    @CrossOrigin
    public ResultVO list() {
        List<GridProvince> list = provinceService.list() ;
        return new ResultVO(200,"查询成功",list);
    }

    /**
     * 根据省id查询城市
     * @param provinceId
     * @return
     */
    @GetMapping("getCitys")
    @CrossOrigin
    public ResultVO getCitys(@RequestParam("provinceId") Integer provinceId){
        QueryWrapper<GridCity> wrapper = new QueryWrapper<>();
        wrapper.eq("province_id",provinceId);//省id市传入的值
        //上面两行是查询条件
        List<GridCity> list = cityService.list(wrapper);
        return new ResultVO(200,"查询成功",list);
    }
}
