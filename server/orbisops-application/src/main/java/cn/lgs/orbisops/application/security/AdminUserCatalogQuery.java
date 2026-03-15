package cn.lgs.orbisops.application.security;

/** Filter and bounded pagination request for the account catalog. */
public record AdminUserCatalogQuery(
        String userId,
        String username,
        Integer status,
        Integer pageNum,
        Integer pageSize) {

    public static AdminUserCatalogQuery all() {
        return new AdminUserCatalogQuery(null, null, null, 1, 10);
    }
}
