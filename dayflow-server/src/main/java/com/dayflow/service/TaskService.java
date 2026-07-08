package com.dayflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dayflow.pojo.dto.TaskCreateDTO;
import com.dayflow.pojo.dto.TaskUpdateDTO;
import com.dayflow.pojo.query.TaskQuery;
import com.dayflow.pojo.vo.TaskVO;

/**
 * 任务服务接口
 * <p>沿用 T6 Activity CRUD 范式，额外提供 complete 状态流转方法。</p>
 *
 * @author jiaxianming
 */
public interface TaskService {

    /**
     * 创建任务
     *
     * @param dto 任务创建入参
     * @return 新建任务 ID
     */
    Long create(TaskCreateDTO dto);

    /**
     * 按 ID 查询任务
     *
     * @param id 任务 ID
     * @return 任务视图
     */
    TaskVO getById(Long id);

    /**
     * 更新任务（部分更新）
     *
     * @param id 任务 ID
     * @param dto 任务修改入参
     */
    void update(Long id, TaskUpdateDTO dto);

    /**
     * 删除任务
     *
     * @param id 任务 ID
     */
    void delete(Long id);

    /**
     * 分页查询当前用户的任务
     *
     * @param query 查询条件
     * @return 任务分页
     */
    IPage<TaskVO> page(TaskQuery query);

    /**
     * 标记任务完成（status -> DONE，completedAt 置当前时间）
     *
     * @param id 任务 ID
     */
    void complete(Long id);
}
