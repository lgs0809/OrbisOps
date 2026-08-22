package cn.lgs.orbisops.api.dto;

import lombok.Data;

/** Browser first-time setup request. */
@Data
public class FirstTimeSetupRequestDTO {
    private String username;
    private String password;
}
