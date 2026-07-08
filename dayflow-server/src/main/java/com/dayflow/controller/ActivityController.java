package com.dayflow.controller;

import com.dayflow.common.Result;
import com.dayflow.pojo.dto.ActivityCreateDTO;
import com.dayflow.pojo.dto.ActivityUpdateDTO;
import com.dayflow.pojo.query.ActivityQuery;
import com.dayflow.pojo.vo.ActivityVO;
import com.dayflow.service.ActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动接口
 * <p>薄层 Controller：仅做参数校验 + 调用 Service + Result 包装。</p>
 *
 * @author jiaxianming
 */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /**
     * 创建活动
     *
     * @param dto 活动创建入参
     * @return 新建活动 ID
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ActivityCreateDTO dto) {
        return Result.success(activityService.create(dto));
    }

    /**
     * 查询单个活动
     *
     * @param id 活动 ID
     * @return 活动视图
     */
    @GetMapping("/{id}")
    public Result<ActivityVO> get(@PathVariable Long id) {
        return Result.success(activityService.getById(id));
    }

    /**
     * 更新活动
     *
     * @param id 活动 ID
     * @param dto 活动修改入参
     * @return 空载荷成功结果
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ActivityUpdateDTO dto) {
        activityService.update(id, dto);
        return Result.success();
    }

    /**
     * 删除活动
     *
     * @param id 活动 ID
     * @return 空载荷成功结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        activityService.delete(id);
        return Result.success();
    }

    /**
     * 分页查询活动
     *
     * @param query 查询条件
     * @return 分页数据
     */
    @GetMapping
    public Result<?> page(ActivityQuery query) {
        return Result.success(activityService.page(query));
    }
}
