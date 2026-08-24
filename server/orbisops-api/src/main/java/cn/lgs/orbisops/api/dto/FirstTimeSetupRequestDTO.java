package cn.lgs.orbisops.api.dto;

import lombok.Data;
import lombok.ToString;

/** Browser first-time setup request. */
@Data
public class FirstTimeSetupRequestDTO {
    private String username;
    @ToString.Exclude
    private String phone;
    @ToString.Exclude
    private String password;
}
