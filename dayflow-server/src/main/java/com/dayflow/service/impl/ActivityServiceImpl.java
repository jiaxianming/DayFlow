package com.dayflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayflow.common.BusinessException;
import com.dayflow.common.ResultCode;
import com.dayflow.common.UserContext;
import com.dayflow.mapper.ActivityMapper;
import com.dayflow.pojo.dto.ActivityCreateDTO;
import com.dayflow.pojo.dto.ActivityUpdateDTO;
import com.dayflow.pojo.entity.ActivityEntity;
import com.dayflow.pojo.query.ActivityQuery;
import com.dayflow.pojo.vo.ActivityVO;
import com.dayflow.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 活动服务实现
 * <p>CRUD 范式参考实现：entity ↔ VO 转换、当前用户隔离、越权校验。</p>
 *
 * @author jiaxianming
 */
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private final ActivityMapper activityMapper;

    @Override
    public Long create(ActivityCreateDTO dto) {
        ActivityEntity e = new ActivityEntity();
        e.setUserId(UserContext.getUserId());
        e.setContent(dto.getContent());
        e.setCategory(dto.getCategory());
        e.setOccurredAt(dto.getOccurredAt());
        activityMapper.insert(e);
        return e.getId();
    }

    @Override
    public ActivityVO getById(Long id) {
        ActivityEntity e = activityMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "活动不存在");
        }
        if (!Objects.equals(e.getUserId(), UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作他人活动");
        }
        return toVO(e);
    }

    @Override
    public void update(Long id, ActivityUpdateDTO dto) {
        ActivityEntity e = activityMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "活动不存在");
        }
        if (!Objects.equals(e.getUserId(), UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作");
        }
        if (dto.getContent() != null) {
            e.setContent(dto.getContent());
        }
        if (dto.getCategory() != null) {
            e.setCategory(dto.getCategory());
        }
        if (dto.getOccurredAt() != null) {
            e.setOccurredAt(dto.getOccurredAt());
        }
        activityMapper.updateById(e);
    }

    @Override
    public void delete(Long id) {
        ActivityEntity e = activityMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "活动不存在");
        }
        if (!Objects.equals(e.getUserId(), UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作他人活动");
        }
        activityMapper.deleteById(id);
    }

    @Override
    public IPage<ActivityVO> page(ActivityQuery q) {
        LambdaQueryWrapper<ActivityEntity> w = new LambdaQueryWrapper<ActivityEntity>()
                .eq(ActivityEntity::getUserId, UserContext.getUserId())
                .ge(q.getStartTime() != null, ActivityEntity::getOccurredAt, q.getStartTime())
                .le(q.getEndTime() != null, ActivityEntity::getOccurredAt, q.getEndTime())
                .eq(q.getCategory() != null, ActivityEntity::getCategory, q.getCategory())
                .orderByDesc(ActivityEntity::getOccurredAt);
        Page<ActivityEntity> p = new Page<>(q.getPage(), q.getSize());
        return activityMapper.selectPage(p, w).convert(this::toVO);
    }

    /**
     * 实体转视图对象
     *
     * @param e 活动实体
     * @return 活动视图
     */
    private ActivityVO toVO(ActivityEntity e) {
        ActivityVO vo = new ActivityVO();
        vo.setId(e.getId());
        vo.setUserId(e.getUserId());
        vo.setContent(e.getContent());
        vo.setCategory(e.getCategory());
        vo.setOccurredAt(e.getOccurredAt());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }
}
