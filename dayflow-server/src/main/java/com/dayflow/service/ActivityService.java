package com.dayflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dayflow.pojo.dto.ActivityCreateDTO;
import com.dayflow.pojo.dto.ActivityUpdateDTO;
import com.dayflow.pojo.query.ActivityQuery;
import com.dayflow.pojo.vo.ActivityVO;

/**
 * 活动服务接口
 * <p>确立 CRUD 范式：create / getById / update / delete / page，供 T7-T9 复用。</p>
 *
 * @author jiaxianming
 */
public interface ActivityService {

    /**
     * 创建活动
     *
     * @param dto 活动创建入参
     * @return 新建活动 ID
     */
    Long create(ActivityCreateDTO dto);

    /**
     * 按 ID 查询活动
     *
     * @param id 活动 ID
     * @return 活动视图
     */
    ActivityVO getById(Long id);

    /**
     * 更新活动（部分更新）
     *
     * @param id 活动 ID
     * @param dto 活动修改入参
     */
    void update(Long id, ActivityUpdateDTO dto);

    /**
     * 删除活动
     *
     * @param id 活动 ID
     */
    void delete(Long id);

    /**
     * 分页查询当前用户的活动
     *
     * @param query 查询条件
     * @return 活动分页
     */
    IPage<ActivityVO> page(ActivityQuery query);
}
