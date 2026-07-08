package com.dayflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayflow.common.BusinessException;
import com.dayflow.common.ResultCode;
import com.dayflow.common.UserContext;
import com.dayflow.mapper.TaskMapper;
import com.dayflow.pojo.dto.TaskCreateDTO;
import com.dayflow.pojo.dto.TaskUpdateDTO;
import com.dayflow.pojo.entity.TaskEntity;
import com.dayflow.pojo.enums.TaskStatus;
import com.dayflow.pojo.query.TaskQuery;
import com.dayflow.pojo.vo.TaskVO;
import com.dayflow.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 任务服务实现
 * <p>沿用 T6 Activity CRUD 范式：当前用户隔离、越权校验、entity ↔ VO 转换、部分更新；
 * 额外实现 complete 状态流转（status -> DONE + completedAt = now）。</p>
 *
 * @author jiaxianming
 */
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskMapper taskMapper;

    @Override
    public Long create(TaskCreateDTO dto) {
        TaskEntity e = new TaskEntity();
        e.setUserId(UserContext.getUserId());
        e.setTitle(dto.getTitle());
        e.setStatus(dto.getStatus());
        taskMapper.insert(e);
        return e.getId();
    }

    @Override
    public TaskVO getById(Long id) {
        TaskEntity e = taskMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
        }
        return toVO(e);
    }

    @Override
    public void update(Long id, TaskUpdateDTO dto) {
        TaskEntity e = taskMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
        }
        if (!Objects.equals(e.getUserId(), UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作");
        }
        if (dto.getTitle() != null) {
            e.setTitle(dto.getTitle());
        }
        if (dto.getStatus() != null) {
            e.setStatus(dto.getStatus());
        }
        taskMapper.updateById(e);
    }

    @Override
    public void delete(Long id) {
        TaskEntity e = taskMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
        }
        taskMapper.deleteById(id);
    }

    @Override
    public IPage<TaskVO> page(TaskQuery q) {
        LambdaQueryWrapper<TaskEntity> w = new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getUserId, UserContext.getUserId())
                .eq(q.getStatus() != null, TaskEntity::getStatus, q.getStatus())
                .orderByDesc(TaskEntity::getCreatedAt);
        Page<TaskEntity> p = new Page<>(q.getPage(), q.getSize());
        return taskMapper.selectPage(p, w).convert(this::toVO);
    }

    @Override
    public void complete(Long id) {
        TaskEntity e = taskMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
        }
        e.setStatus(TaskStatus.DONE);
        e.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(e);
    }

    /**
     * 实体转视图对象
     *
     * @param e 任务实体
     * @return 任务视图
     */
    private TaskVO toVO(TaskEntity e) {
        TaskVO vo = new TaskVO();
        vo.setId(e.getId());
        vo.setUserId(e.getUserId());
        vo.setTitle(e.getTitle());
        vo.setStatus(e.getStatus());
        vo.setCompletedAt(e.getCompletedAt());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }
}
