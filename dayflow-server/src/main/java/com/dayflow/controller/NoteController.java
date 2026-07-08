package com.dayflow.controller;

import com.dayflow.common.Result;
import com.dayflow.pojo.dto.NoteCreateDTO;
import com.dayflow.pojo.dto.NoteUpdateDTO;
import com.dayflow.pojo.query.NoteQuery;
import com.dayflow.pojo.vo.NoteVO;
import com.dayflow.service.NoteService;
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
 * 学习笔记接口（M1 只存原文）
 * <p>薄层 Controller：仅做参数校验 + 调用 Service + Result 包装。</p>
 *
 * @author jiaxianming
 */
@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    /**
     * 创建学习笔记
     *
     * @param dto 笔记创建入参
     * @return 新建笔记 ID
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody NoteCreateDTO dto) {
        return Result.success(noteService.create(dto));
    }

    /**
     * 查询单个笔记
     *
     * @param id 笔记 ID
     * @return 笔记视图
     */
    @GetMapping("/{id}")
    public Result<NoteVO> get(@PathVariable Long id) {
        return Result.success(noteService.getById(id));
    }

    /**
     * 更新笔记
     *
     * @param id 笔记 ID
     * @param dto 笔记修改入参
     * @return 空载荷成功结果
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody NoteUpdateDTO dto) {
        noteService.update(id, dto);
        return Result.success();
    }

    /**
     * 删除笔记
     *
     * @param id 笔记 ID
     * @return 空载荷成功结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        noteService.delete(id);
        return Result.success();
    }

    /**
     * 分页查询笔记（支持 tags 模糊匹配）
     *
     * @param query 查询条件
     * @return 分页数据
     */
    @GetMapping
    public Result<?> page(NoteQuery query) {
        return Result.success(noteService.page(query));
    }
}
