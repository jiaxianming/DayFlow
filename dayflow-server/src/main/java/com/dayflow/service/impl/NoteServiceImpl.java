package com.dayflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayflow.common.BusinessException;
import com.dayflow.common.ResultCode;
import com.dayflow.common.UserContext;
import com.dayflow.mapper.NoteMapper;
import com.dayflow.pojo.dto.NoteCreateDTO;
import com.dayflow.pojo.dto.NoteUpdateDTO;
import com.dayflow.pojo.entity.NoteEntity;
import com.dayflow.pojo.query.NoteQuery;
import com.dayflow.pojo.vo.NoteVO;
import com.dayflow.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 学习笔记服务实现
 * <p>沿用 T6 Activity CRUD 范式：当前用户隔离、越权校验、entity ↔ VO 转换、部分更新；
 * 查询条件 tags 用 LIKE 模糊匹配（M1 只存原文，不做切块 embedding）。</p>
 *
 * @author jiaxianming
 */
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteMapper noteMapper;

    @Override
    public Long create(NoteCreateDTO dto) {
        NoteEntity e = new NoteEntity();
        e.setUserId(UserContext.getUserId());
        e.setTitle(dto.getTitle());
        e.setContent(dto.getContent());
        e.setTags(dto.getTags());
        noteMapper.insert(e);
        return e.getId();
    }

    @Override
    public NoteVO getById(Long id) {
        NoteEntity e = noteMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }
        return toVO(e);
    }

    @Override
    public void update(Long id, NoteUpdateDTO dto) {
        NoteEntity e = noteMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }
        if (!Objects.equals(e.getUserId(), UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作");
        }
        if (dto.getTitle() != null) {
            e.setTitle(dto.getTitle());
        }
        if (dto.getContent() != null) {
            e.setContent(dto.getContent());
        }
        if (dto.getTags() != null) {
            e.setTags(dto.getTags());
        }
        noteMapper.updateById(e);
    }

    @Override
    public void delete(Long id) {
        NoteEntity e = noteMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }
        noteMapper.deleteById(id);
    }

    @Override
    public IPage<NoteVO> page(NoteQuery q) {
        LambdaQueryWrapper<NoteEntity> w = new LambdaQueryWrapper<NoteEntity>()
                .eq(NoteEntity::getUserId, UserContext.getUserId())
                .like(q.getTags() != null, NoteEntity::getTags, q.getTags())
                .orderByDesc(NoteEntity::getCreatedAt);
        Page<NoteEntity> p = new Page<>(q.getPage(), q.getSize());
        return noteMapper.selectPage(p, w).convert(this::toVO);
    }

    /**
     * 实体转视图对象
     *
     * @param e 笔记实体
     * @return 笔记视图
     */
    private NoteVO toVO(NoteEntity e) {
        NoteVO vo = new NoteVO();
        vo.setId(e.getId());
        vo.setUserId(e.getUserId());
        vo.setTitle(e.getTitle());
        vo.setContent(e.getContent());
        vo.setTags(e.getTags());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }
}
