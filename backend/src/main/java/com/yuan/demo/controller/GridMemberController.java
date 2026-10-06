package com.yuan.demo.controller;

import com.yuan.demo.common.ResultVO;
import com.yuan.demo.entity.GridMember;
import com.yuan.demo.security.PasswordService;
import com.yuan.demo.security.RequireRole;
import com.yuan.demo.service.IGridMemberService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * 网格员基础增删改查接口。
 */
@RestController
@RequestMapping("/gridMember")
@CrossOrigin
@AllArgsConstructor
@RequireRole("ADMIN")
public class GridMemberController {

    private final IGridMemberService gridMemberService;
    private final PasswordService passwordService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/list")
    public ResultVO<List<Map<String, Object>>> list() {
        return new ResultVO<>(200, "查询成功", jdbcTemplate.queryForList("""
                SELECT gm.gm_id AS `gmId`, gm.gm_name AS `gmName`, gm.gm_code AS `gmCode`,
                       gm.province_id AS `provinceId`, gm.city_id AS `cityId`, gm.tel, gm.state,
                       gm.remarks, p.province_name AS `provinceName`, c.city_name AS `cityName`
                FROM grid_member gm
                LEFT JOIN grid_province p ON p.province_id = gm.province_id
                LEFT JOIN grid_city c ON c.city_id = gm.city_id AND c.province_id = gm.province_id
                ORDER BY gm.state, gm.gm_name
                """));
    }

    @GetMapping("/find/{gmId}")
    public ResultVO<GridMember> find(@PathVariable String gmId) {
        GridMember member = gridMemberService.getById(gmId);
        if (member == null) {
            return new ResultVO<>(404, "未找到该网格员");
        }
        member.setPassword(null);
        return new ResultVO<>(200, "查询成功", member);
    }

    @PostMapping("/save")
    public ResultVO<Boolean> save(@RequestBody GridMember member) {
        validate(member);
        if (gridMemberService.getById(member.getGmId()) != null) {
            return new ResultVO<>(400, "网格员编号已存在", false);
        }
        if (member.getPassword() == null || member.getPassword().length() < 6) {
            throw new IllegalArgumentException("网格员登录密码不能少于6位");
        }
        member.setPassword(passwordService.encode(member.getPassword()));
        return new ResultVO<>(200, "保存成功", gridMemberService.save(member));
    }

    @PostMapping("/update")
    public ResultVO<Boolean> update(@RequestBody GridMember member) {
        validate(member);
        GridMember stored = gridMemberService.getById(member.getGmId());
        if (stored == null) {
            return new ResultVO<>(404, "未找到该网格员", false);
        }
        if (member.getPassword() == null || member.getPassword().isBlank()) {
            member.setPassword(stored.getPassword());
        } else {
            member.setPassword(passwordService.encode(member.getPassword()));
        }
        return new ResultVO<>(200, "更新成功", gridMemberService.updateById(member));
    }

    @GetMapping("/delete/{gmId}")
    public ResultVO<Boolean> delete(@PathVariable String gmId) {
        return new ResultVO<>(200, "删除成功", gridMemberService.removeById(gmId));
    }

    private void validate(GridMember member) {
        if (member == null || member.getGmId() == null || member.getGmId().trim().isEmpty()) {
            throw new IllegalArgumentException("网格员编号不能为空");
        }
        if (member.getGmName() == null || member.getGmName().trim().isEmpty()) {
            throw new IllegalArgumentException("网格员姓名不能为空");
        }
        if (member.getState() == null || member.getState() < 0 || member.getState() > 3) {
            throw new IllegalArgumentException("网格员工作状态必须为0到3");
        }
        if (member.getProvinceId() == null || member.getCityId() == null) {
            throw new IllegalArgumentException("网格员所属省份和城市不能为空");
        }
        Integer regionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM grid_city WHERE province_id = ? AND city_id = ?",
                Integer.class, member.getProvinceId(), member.getCityId());
        if (regionCount == null || regionCount == 0) {
            throw new IllegalArgumentException("网格员所属城市与省份不匹配");
        }
    }
}
