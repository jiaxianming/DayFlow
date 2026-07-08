package com.dayflow.controller;

import com.dayflow.common.Result;
import com.dayflow.pojo.dto.TaskCreateDTO;
import com.dayflow.pojo.dto.TaskUpdateDTO;
import com.dayflow.pojo.query.TaskQuery;
import com.dayflow.pojo.vo.TaskVO;
import com.dayflow.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务接口
 * <p>薄层 Controller：仅做参数校验 + 调用 Service + Result 包装；
 * 额外暴露 PATCH /{id}/complete 用于状态流转。</p>
 *
 * @author jiaxianming
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * 创建任务
     *
     * @param dto 任务创建入参
     * @return 新建任务 ID
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody TaskCreateDTO dto) {
        return Result.success(taskService.create(dto));
    }

    /**
     * 查询单个任务
     *
     * @param id 任务 ID
     * @return 任务视图
     */
    @GetMapping("/{id}")
    public Result<TaskVO> get(@PathVariable Long id) {
        return Result.success(taskService.getById(id));
    }

    /**
     * 更新任务
     *
     * @param id 任务 ID
     * @param dto 任务修改入参
     * @return 空载荷成功结果
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TaskUpdateDTO dto) {
        taskService.update(id, dto);
        return Result.success();
    }

    /**
     * 删除任务
     *
     * @param id 任务 ID
     * @return 空载荷成功结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return Result.success();
    }

    /**
     * 分页查询任务
     *
     * @param query 查询条件
     * @return 分页数据
     */
    @GetMapping
    public Result<?> page(TaskQuery query) {
        return Result.success(taskService.page(query));
    }

    /**
     * 标记任务完成（status -> DONE + completedAt = now）
     *
     * @param id 任务 ID
     * @return 空载荷成功结果
     */
    @PatchMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        taskService.complete(id);
        return Result.success();
    }
}
