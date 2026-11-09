package com.hnist.mapper;

import com.hnist.entity.AppointmentSchedule;
import org.apache.ibatis.annotations.Param;

/** 预约排班模板数据访问：每服务一份每日时间段，读写均以 service_id 为唯一口径。 */
public interface AppointmentScheduleMapper {

    /** 读该服务的排班模板，未设置过返回 null。 */
    AppointmentSchedule selectByServiceId(@Param("serviceId") Integer serviceId);

    /** 兜底任务：返回全部排班模板，模板表一服务一行，全量扫描代价可控。 */
    java.util.List<AppointmentSchedule> selectAllTemplates();

    /** 唯一键 uk_schedule_service 保证一服务只有一份模板。 */
    int insert(AppointmentSchedule schedule);

    /** 更新按 service_id + shop_id 双条件：模板归属不可被请求改挂到别家门店。 */
    int updateByServiceIdAndShopId(@Param("schedule") AppointmentSchedule schedule,
                                   @Param("shopId") Integer shopId);

    /** 删除服务时清理模板（删除校验已保证无时段引用）。 */
    int deleteByServiceId(@Param("serviceId") Integer serviceId);
}
