package com.dayflow.common;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充：created_at / updated_at
 * 实体字段标注 @TableField(fill = FieldFill.INSERT) / FieldFill.INSERT_UPDATE 后，
 * 由本类在 insert / update 时统一写入时间，无需业务代码手动 set。
 *
 * @author jiaxianming
 */
@Component
public class MetaObjectHandlerImpl implements MetaObjectHandler {

    /**
     * 新增时填充 created_at 与 updated_at
     *
     * @param metaObject MyBatis-Plus 反射包装的对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * 更新时填充 updated_at
     *
     * @param metaObject MyBatis-Plus 反射包装的对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
