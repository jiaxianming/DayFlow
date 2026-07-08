package com.dayflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dayflow.pojo.dto.NoteCreateDTO;
import com.dayflow.pojo.dto.NoteUpdateDTO;
import com.dayflow.pojo.query.NoteQuery;
import com.dayflow.pojo.vo.NoteVO;

/**
 * 学习笔记服务接口
 * <p>沿用 T6 Activity CRUD 范式：create / getById / update / delete / page。</p>
 *
 * @author jiaxianming
 */
public interface NoteService {

    /**
     * 创建学习笔记（M1 只存原文）
     *
     * @param dto 笔记创建入参
     * @return 新建笔记 ID
     */
    Long create(NoteCreateDTO dto);

    /**
     * 按 ID 查询笔记
     *
     * @param id 笔记 ID
     * @return 笔记视图
     */
    NoteVO getById(Long id);

    /**
     * 更新笔记（部分更新）
     *
     * @param id 笔记 ID
     * @param dto 笔记修改入参
     */
    void update(Long id, NoteUpdateDTO dto);

    /**
     * 删除笔记
     *
     * @param id 笔记 ID
     */
    void delete(Long id);

    /**
     * 分页查询当前用户的笔记（支持 tags 模糊匹配）
     *
     * @param query 查询条件
     * @return 笔记分页
     */
    IPage<NoteVO> page(NoteQuery query);
}
