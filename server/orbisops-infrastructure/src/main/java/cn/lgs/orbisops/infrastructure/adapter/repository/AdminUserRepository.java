package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.agent.adapter.repository.IAdminUserRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AdminUserRecord;
import cn.lgs.orbisops.infrastructure.dao.IAdminUserDao;
import cn.lgs.orbisops.infrastructure.dao.po.AdminUser;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JDBC/MyBatis-backed administrator repository.
 *
 * <p>The repository deliberately fails closed when persistence is unavailable. OrbisOps never
 * synthesizes a built-in administrator or a default password in application code.</p>
 */
@Repository
public class AdminUserRepository implements IAdminUserRepository {

    private final IAdminUserDao dao;

    public AdminUserRepository(IAdminUserDao dao) {
        this.dao = dao;
    }

    @Override
    public int insert(AdminUserRecord record) {
        return dao.insert(toPo(record));
    }

    @Override
    public int updateById(AdminUserRecord record) {
        return dao.updateById(toPo(record));
    }

    @Override
    public int updateByUserId(AdminUserRecord record) {
        return dao.updateByUserId(toPo(record));
    }

    @Override
    public int deleteById(Long id) {
        return dao.deleteById(id);
    }

    @Override
    public int deleteByUserId(String userId) {
        return dao.deleteByUserId(userId);
    }

    @Override
    public AdminUserRecord queryById(Long id) {
        return toRecord(dao.queryById(id));
    }

    @Override
    public AdminUserRecord queryByUserId(String userId) {
        return toRecord(dao.queryByUserId(userId));
    }

    @Override
    public AdminUserRecord queryByUsername(String username) {
        return toRecord(dao.queryByUsername(username));
    }

    @Override
    public List<AdminUserRecord> queryEnabledUsers() {
        return toRecords(dao.queryEnabledUsers());
    }

    @Override
    public List<AdminUserRecord> queryByStatus(Integer status) {
        return toRecords(dao.queryByStatus(status));
    }

    @Override
    public List<AdminUserRecord> queryAll() {
        return toRecords(dao.queryAll());
    }

    private AdminUser toPo(AdminUserRecord record) {
        AdminUser po = new AdminUser();
        BeanUtils.copyProperties(record, po);
        return po;
    }

    private AdminUserRecord toRecord(AdminUser po) {
        if (po == null) {
            return null;
        }
        AdminUserRecord record = new AdminUserRecord();
        BeanUtils.copyProperties(po, record);
        return record;
    }

    private List<AdminUserRecord> toRecords(List<AdminUser> rows) {
        return rows == null ? List.of() : rows.stream().map(this::toRecord).toList();
    }
}
