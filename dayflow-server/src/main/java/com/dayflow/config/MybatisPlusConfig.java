package com.dayflow.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 * 注册分页等 MyBatis-Plus 内置插件
 * <p>注：@MapperScan 放在此独立 @Configuration 而非 DayflowApplication，目的是避免
 * @WebMvcTest 切片测试加载主启动类时触发 MapperScannerRegistrar 注册 Mapper Bean
 * （切片上下文无 DataSource / SqlSessionFactory，会导致上下文初始化失败）。
 *
 * @author jiaxianming
 */
@Configuration
@MapperScan("com.dayflow.mapper")
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器（含分页）
     *
     * @return 装配好 PaginationInnerInterceptor 的拦截器（MySQL 方言）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
