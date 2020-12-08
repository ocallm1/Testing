package com.clearstream.hydrogen.messagetransform.converttohydrogendata.beans;

import lombok.Data;

@Data
public class RoleTypeAndShortCode
{
    private String oldRoleType;
    private String newRoleType;
    private String oldShortCode;
    private String newShortCode;
}
