package cn.lgs.orbisops.domain.agent.adapter.repository;

import cn.lgs.orbisops.domain.agent.model.entity.AdminUserRecord;

import java.util.List;

public interface IAdminUserRepository {

    int insert(AdminUserRecord record);

    int updateById(AdminUserRecord record);

    int updateByUserId(AdminUserRecord record);

    int deleteById(Long id);

    int deleteByUserId(String userId);

    AdminUserRecord queryById(Long id);

    AdminUserRecord queryByUserId(String userId);

    AdminUserRecord queryByUsername(String username);

    List<AdminUserRecord> queryEnabledUsers();

    List<AdminUserRecord> queryByStatus(Integer status);

    List<AdminUserRecord> queryAll();
}
